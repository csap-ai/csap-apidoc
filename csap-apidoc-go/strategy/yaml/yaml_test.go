package yaml

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"testing/fstest"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// javaSample 模仿 Java YAMLMapper 写出的格式(INDENT_ARRAYS 下 "-" 单独一行、code 是数字)。
const javaSample = `# 用户接口规则
com.example.UserController:
  login:
    request:
      request.username:
        required: true
        paramType: BODY
        description: 用户名
        validate:
         -
          descr: 字符串不能为空
          level: 1
          type: NotEmpty
          code: 1001
          message: 用户名不能为空
          pattern: NotEmpty
    response:
      code:
        required: true
        include: true
        description: 状态码
      data.accessToken:
        required: true
        include: true
  list:
    response:
      data.rows.id: {include: true}
`

func writeFile(t *testing.T, dir, name, content string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(dir, name), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestLoadJavaFormat(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "UserController-method-field.yaml", javaSample)
	writeFile(t, dir, "Dup-method-field.yaml", "com.example.UserController:\n  other: {}\n") // 同名分组:以排序在前的文件为准
	writeFile(t, dir, "notes.txt", "ignored")
	rules, err := Dir(dir).Load(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := rules["com.example.UserController.other"]; !ok {
		t.Fatal("Dup-... sorts first, its group should win")
	}
	if _, ok := rules["com.example.UserController.login"]; ok {
		t.Fatal("second file's copy of the group must be ignored")
	}

	os.Remove(filepath.Join(dir, "Dup-method-field.yaml"))
	rules, err = Dir(dir).Load(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	login := rules["com.example.UserController.login"]
	u := login.Request["request.username"]
	if !*u.Required || u.ParamType != "BODY" || u.Description != "用户名" || len(u.Validate) != 1 {
		t.Fatalf("request rule: %+v", u)
	}
	if v := u.Validate[0]; v.Code != "1001" || v.Type != "NotEmpty" || v.Level != 1 || v.Message != "用户名不能为空" {
		t.Fatalf("validate: %+v", v)
	}
	if !*login.Response["code"].Include || login.Response["data.accessToken"].Description != "" {
		t.Fatalf("response: %+v", login.Response)
	}
	if !*rules["com.example.UserController.list"].Response["data.rows.id"].Include {
		t.Fatal("flow style")
	}
}

func TestBadFileDoesNotBlockOthers(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "A-method-field.yaml", javaSample)
	writeFile(t, dir, "B-method-field.yaml", "a: [unclosed")
	rules, err := Dir(dir).Load(context.Background())
	if err == nil || !strings.Contains(err.Error(), "B-method-field.yaml") {
		t.Fatalf("want error naming the bad file, got %v", err)
	}
	if len(rules) != 2 {
		t.Fatalf("good file should still load: %d", len(rules))
	}
}

func TestSaveKeepsRestOfFile(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "UserController-method-field.yaml", javaSample)
	s := Dir(dir)
	ctx := context.Background()
	tr := true
	v, err := s.Save(ctx, "com.example.UserController.list", strategy.MethodRules{Response: map[string]strategy.FieldRule{
		"data.rows.name": {Include: &tr, Description: "名称"},
		"data":           {Include: &tr},
	}}, "tester")
	if err != nil || v != 1 {
		t.Fatalf("save: %v %d", err, v)
	}
	b, _ := os.ReadFile(filepath.Join(dir, "UserController-method-field.yaml"))
	out := string(b)
	for _, want := range []string{"# 用户接口规则", "用户名不能为空", "data.rows.name"} {
		if !strings.Contains(out, want) {
			t.Fatalf("missing %q in:\n%s", want, out)
		}
	}
	if strings.Contains(out, "data.rows.id") {
		t.Fatal("list should be replaced")
	}
	if strings.Index(out, "data:") > strings.Index(out, "data.rows.name") {
		t.Fatalf("parent keys should come first:\n%s", out)
	}
	rules, _ := s.Load(ctx)
	if rules["com.example.UserController.list"].Response["data.rows.name"].Description != "名称" {
		t.Fatal("reload after save")
	}
	if len(rules["com.example.UserController.login"].Request) != 1 {
		t.Fatal("other method lost")
	}

	// 新分组:新建 <简单名>-method-field.yaml
	if _, err := s.Save(ctx, "github.com/x/app.OrderController.get", strategy.MethodRules{Request: map[string]strategy.FieldRule{
		"id.id": {Required: &tr, Validate: []strategy.ValidateRule{{Type: "Pattern", Pattern: "PATTERN_11", Code: "2001", Message: "id 不对", Level: 1}}},
	}}, ""); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dir, "OrderController-method-field.yaml")); err != nil {
		t.Fatal(err)
	}
	rules, _ = s.Load(ctx)
	if r := rules["github.com/x/app.OrderController.get"].Request["id.id"]; r.Validate[0].Code != "2001" {
		t.Fatalf("new file: %+v", r)
	}

	// 空规则 = 删除
	if _, err := s.Save(ctx, "com.example.UserController.list", strategy.MethodRules{}, ""); err != nil {
		t.Fatal(err)
	}
	rules, _ = s.Load(ctx)
	if _, ok := rules["com.example.UserController.list"]; ok {
		t.Fatal("delete")
	}
	if _, err := s.Save(ctx, "bad", strategy.MethodRules{}, ""); err == nil {
		t.Fatal("bad key")
	}
}

func TestWatch(t *testing.T) {
	dir := t.TempDir()
	s := New(Options{Dir: dir, Poll: 20 * time.Millisecond})
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	changed := make(chan struct{}, 10)
	go func() { _ = s.Watch(ctx, func() { changed <- struct{}{} }) }()
	time.Sleep(30 * time.Millisecond)

	wait := func(what string) {
		t.Helper()
		select {
		case <-changed:
		case <-time.After(2 * time.Second):
			t.Fatalf("no change event: %s", what)
		}
	}
	writeFile(t, dir, "A-method-field.yaml", javaSample) // 外部修改
	wait("external write")
	tr := true
	_, _ = s.Save(ctx, "G.m", strategy.MethodRules{Response: map[string]strategy.FieldRule{"x": {Include: &tr}}}, "")
	wait("own save")
}

func TestFSReadOnly(t *testing.T) {
	fsys := fstest.MapFS{"rules/U-method-field.yaml": {Data: []byte(javaSample)}}
	s := FS(fsys, "rules")
	rules, err := s.Load(context.Background())
	if err != nil || len(rules) != 2 {
		t.Fatalf("%v %d", err, len(rules))
	}
	if _, err := s.Save(context.Background(), "G.m", strategy.MethodRules{}, ""); err != strategy.ErrReadOnly {
		t.Fatalf("want ErrReadOnly, got %v", err)
	}
	if !strings.HasPrefix(s.Name(), "yaml:") {
		t.Fatal(s.Name())
	}
	// 目录不存在不算错
	if r, err := Dir(filepath.Join(t.TempDir(), "nope")).Load(context.Background()); err != nil || len(r) != 0 {
		t.Fatal(err)
	}
}

// TestRealProjectFiles 用真实项目的规则文件做解析和回写测试(默认跳过,设置 CSAP_TEST_YAML_DIR)。
func TestRealProjectFiles(t *testing.T) {
	src := os.Getenv("CSAP_TEST_YAML_DIR")
	if src == "" {
		t.Skip("CSAP_TEST_YAML_DIR 未设置")
	}
	ctx := context.Background()
	rules, err := Dir(src).Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	var nReq, nResp, nVal int
	for _, r := range rules {
		nReq += len(r.Request)
		nResp += len(r.Response)
		for _, f := range r.Request {
			nVal += len(f.Validate)
		}
	}
	t.Logf("%d 个接口,请求规则 %d 条,返回规则 %d 条,校验规则 %d 条", len(rules), nReq, nResp, nVal)

	// 复制到临时目录,每个接口原样写回一次,再加载,结果应完全相同
	dir := t.TempDir()
	files, _ := filepath.Glob(filepath.Join(src, "*.yaml"))
	for _, f := range files {
		b, _ := os.ReadFile(f)
		writeFile(t, dir, filepath.Base(f), string(b))
	}
	s := Dir(dir)
	if _, err := s.Load(ctx); err != nil {
		t.Fatal(err)
	}
	for k, r := range rules {
		if _, err := s.Save(ctx, k, r, ""); err != nil {
			t.Fatalf("%s: %v", k, err)
		}
	}
	again, err := s.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(again) != len(rules) {
		t.Fatalf("method count %d -> %d", len(rules), len(again))
	}
	for k, r := range rules {
		if !equalRules(r, again[k]) {
			t.Fatalf("%s changed after round trip", k)
		}
	}
}

func equalRules(a, b strategy.MethodRules) bool {
	eq := func(x, y map[string]strategy.FieldRule) bool {
		if len(x) != len(y) {
			return false
		}
		for k, fx := range x {
			fy, ok := y[k]
			if !ok || fx.Description != fy.Description || fx.ParamType != fy.ParamType || fx.Example != fy.Example ||
				!eqb(fx.Required, fy.Required) || !eqb(fx.Include, fy.Include) || !eqb(fx.Hidden, fy.Hidden) ||
				len(fx.Validate) != len(fy.Validate) {
				return false
			}
			for i := range fx.Validate {
				if fx.Validate[i] != fy.Validate[i] {
					return false
				}
			}
		}
		return true
	}
	return eq(a.Request, b.Request) && eq(a.Response, b.Response)
}

func eqb(a, b *bool) bool {
	if a == nil || b == nil {
		return a == b
	}
	return *a == *b
}

// TestWatchSeesChangeBeforeStart:Load 之后、Watch 启动之前的外部修改也要能发现。
func TestWatchSeesChangeBeforeStart(t *testing.T) {
	dir := t.TempDir()
	s := New(Options{Dir: dir, Poll: time.Hour})
	if _, err := s.Load(context.Background()); err != nil {
		t.Fatal(err)
	}
	writeFile(t, dir, "A-method-field.yaml", javaSample)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	changed := make(chan struct{}, 1)
	go func() { _ = s.Watch(ctx, func() { changed <- struct{}{} }) }()
	select {
	case <-changed:
	case <-time.After(2 * time.Second):
		t.Fatal("change between Load and Watch was missed")
	}
}
