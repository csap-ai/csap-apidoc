package fieldlib_test

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/fieldlib"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

func TestBuiltin(t *testing.T) {
	bs := fieldlib.Builtin()
	if len(bs) < 14 {
		t.Fatalf("内置模板 %d 条", len(bs))
	}
	names := map[string]bool{}
	builtinNames := map[string]bool{}
	for _, n := range validate.PatternNames() {
		builtinNames[n] = true
	}
	for _, tpl := range bs {
		if names[tpl.Name] || !fieldlib.ValidName(tpl.Name) || tpl.Title == "" {
			t.Errorf("模板 %q 不合法或重复", tpl.Name)
		}
		names[tpl.Name] = true
		for _, v := range tpl.Validate {
			if v.Type != "NotNull" && v.Type != "NotEmpty" && v.Type != "Pattern" {
				t.Errorf("%s: 内置模板只用两种语言共有的校验类型,实际 %s", tpl.Name, v.Type)
			}
			if v.Type == "Pattern" && !builtinNames[v.Pattern] {
				if _, err := regexp.Compile("^(?:" + validate.GoPattern(v.Pattern) + ")$"); err != nil {
					t.Errorf("%s: 正则不合法: %v", tpl.Name, err)
				}
			}
		}
		// 示例必须通过自己的校验规则(Pattern)
		for _, v := range tpl.Validate {
			if v.Type == "Pattern" && tpl.Example != "" && !matches(v.Pattern, tpl.Example) {
				t.Errorf("%s: 示例 %q 不匹配自己的正则 %s", tpl.Name, tpl.Example, v.Pattern)
			}
		}
	}
}

func matches(pattern, s string) bool {
	for _, p := range validate.Patterns {
		if p.Name == pattern {
			pattern = p.Pattern
		}
	}
	re := regexp.MustCompile("^(?:" + validate.GoPattern(pattern) + ")$")
	return re.MatchString(s)
}

func TestParse(t *testing.T) {
	f, warns, err := fieldlib.Parse([]byte(`
version: 1
templates:
  - {name: a, title: A, description: x}
  - {name: a, title: A2}
  - {name: Bad Name, title: B}
  - {name: notitle}
  - {name: ok-1, title: OK, example: "007", required: true, validate: [{type: Pattern, pattern: PATTERN_1, code: 400, level: 1}]}
`))
	if err != nil || len(f.Templates) != 2 || len(warns) != 3 {
		t.Fatalf("%v %v %+v", err, warns, f)
	}
	if f.Templates[0].Title != "A2" || f.Templates[1].Example != "007" || f.Templates[1].Validate[0].Code != "400" {
		t.Fatalf("%+v", f.Templates)
	}
	// JSON 表示同一结构
	if jf, _, err := fieldlib.Parse([]byte(`{"version":1,"templates":[{"name":"j","title":"J"}]}`)); err != nil || len(jf.Templates) != 1 {
		t.Fatalf("%v", err)
	}
	if _, _, err := fieldlib.Parse([]byte("version: 2\ntemplates: []")); !errors.Is(err, fieldlib.ErrNewerVersion) {
		t.Fatalf("更高版本应忽略: %v", err)
	}
}

func TestMergeProjectOverridesBuiltin(t *testing.T) {
	b := fieldlib.Builtin()
	es := fieldlib.Merge(b, []fieldlib.Template{{Name: "phone-cn", Title: "我们的手机号", Description: "x"}, {Name: "mine", Title: "M"}})
	if len(es) != len(b)+1 {
		t.Fatalf("%d", len(es))
	}
	n := 0
	for _, e := range es {
		if e.Name == "phone-cn" {
			n++
			if e.Source != fieldlib.SourceProject || e.Title != "我们的手机号" {
				t.Fatalf("%+v", e)
			}
		}
	}
	if n != 1 {
		t.Fatal("同名只应保留项目模板")
	}
}

func TestApplySemantics(t *testing.T) {
	tpl := fieldlib.Template{Name: "phone", Title: "手机号", Description: "手机号", Example: "13800138000", Required: model.Bool(true),
		Validate: []strategy.ValidateRule{{Type: "Pattern", Pattern: "PATTERN_35", Code: "400", Level: 1}}}
	old := strategy.FieldRule{Description: "旧说明", Include: model.Bool(true), ParamType: "BODY",
		Validate: []strategy.ValidateRule{{Type: "NotEmpty"}, {Type: "Length", Pattern: "1,5"}}}
	pv := fieldlib.Preview(old, tpl)
	items := map[string]bool{}
	for _, c := range pv {
		items[c.Item] = true
	}
	for _, want := range []string{"description", "example", "required", "validate", "template"} {
		if !items[want] {
			t.Errorf("预览缺 %s: %+v", want, pv)
		}
	}
	got := fieldlib.Apply(old, tpl)
	if got.Description != "手机号" || got.Example != "13800138000" || !*got.Required || got.Template != "phone" {
		t.Fatalf("%+v", got)
	}
	if len(got.Validate) != 1 || got.Validate[0].Type != "Pattern" { // 整体替换,不是追加
		t.Fatalf("validate: %+v", got.Validate)
	}
	if got.Include == nil || got.ParamType != "BODY" { // 模板没写的项不动
		t.Fatalf("不该改动模板没写的项: %+v", got)
	}
	if len(old.Validate) != 2 {
		t.Fatal("入参不该被修改")
	}
	// 重复套用不叠加,预览为空
	again := fieldlib.Apply(got, tpl)
	if len(again.Validate) != 1 || len(fieldlib.Preview(again, tpl)) != 0 || fieldlib.Diverges(again, tpl) {
		t.Fatalf("%+v", again)
	}
	// 模板没写 validate:字段原有的校验保留
	keep := fieldlib.Apply(old, fieldlib.Template{Name: "d", Title: "D", Description: "只改说明"})
	if len(keep.Validate) != 2 || keep.Description != "只改说明" {
		t.Fatalf("%+v", keep)
	}
	// 模板更新后,已套用的字段与模板不一致
	tpl.Example = "13900139000"
	if !fieldlib.Diverges(again, tpl) {
		t.Fatal("模板变了应提示不一致")
	}
}

func TestFromRule(t *testing.T) {
	r := strategy.FieldRule{Description: "d", Example: "e", Required: model.Bool(false), Validate: []strategy.ValidateRule{{Type: "NotNull"}}, Include: model.Bool(true), ParamType: "BODY"}
	tpl, err := fieldlib.FromRule("my-field", "我的字段", r)
	if err != nil || tpl.Description != "d" || *tpl.Required != false || len(tpl.Validate) != 1 {
		t.Fatalf("%+v %v", tpl, err)
	}
	if _, err := fieldlib.FromRule("Bad", "x", r); err == nil {
		t.Fatal("坏名字应报错")
	}
	if _, err := fieldlib.FromRule("ok", "", r); err == nil {
		t.Fatal("缺 title 应报错")
	}
}

// 项目模板经 YAML 存储读写:文件在 field-template/api-field-template.yaml,与 method-field 并列。
func TestProjectTemplatesYAMLStore(t *testing.T) {
	ctx := context.Background()
	root := t.TempDir()
	rules := filepath.Join(root, "method-field")
	if err := os.MkdirAll(rules, 0o755); err != nil {
		t.Fatal(err)
	}
	s := yaml.New(yaml.Options{Dir: rules, Poll: -1, History: "-"})
	f, _, err := fieldlib.LoadProject(ctx, s)
	if err != nil || len(f.Templates) != 0 {
		t.Fatalf("%v %+v", err, f)
	}
	if err := f.Upsert(fieldlib.Template{Name: "order-no", Title: "订单号", Example: "20260101001",
		Validate: []strategy.ValidateRule{{Type: "Pattern", Pattern: "[0-9]{11}", Code: "400"}}}); err != nil {
		t.Fatal(err)
	}
	if err := f.Upsert(fieldlib.Template{Name: "Bad", Title: "x"}); err == nil {
		t.Fatal("坏名字应报错")
	}
	if _, err := fieldlib.SaveProject(ctx, s, f, "u"); err != nil {
		t.Fatal(err)
	}
	b, err := os.ReadFile(filepath.Join(root, "field-template", "api-field-template.yaml"))
	if err != nil || !strings.Contains(string(b), "order-no") {
		t.Fatalf("%v %s", err, b)
	}
	f2, warns, err := fieldlib.LoadProject(ctx, s)
	if err != nil || len(warns) != 0 || len(f2.Templates) != 1 || f2.Templates[0].Validate[0].Pattern != "[0-9]{11}" {
		t.Fatalf("%v %v %+v", err, warns, f2)
	}
	if !f2.Remove("order-no") || f2.Remove("order-no") {
		t.Fatal("Remove")
	}
	if _, err := fieldlib.SaveProject(ctx, s, f2, "u"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "field-template", "api-field-template.yaml")); !os.IsNotExist(err) {
		t.Fatal("没有模板时应删除文件")
	}
}
