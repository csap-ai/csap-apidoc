package apidoc

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/mock"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

type mockItem struct {
	ID    int64    `json:"id" doc:"ID"`
	Name  string   `json:"name" doc:"名称"`
	Email string   `json:"email" doc:"邮箱"`
	Level int      `json:"level" doc:"等级"`
	Token string   `json:"token" doc:"令牌"`
	Tags  []string `json:"tags" doc:"标签"`
	Pwd   string   `json:"pwd" doc:"密码"`
}

type mockResult struct {
	Code    string `json:"code" doc_force:"true"`
	Message string `json:"message" doc_force:"true"`
	Data    any    `json:"data" doc_force:"true"`
}

func newMockDoc(t *testing.T, mo MockOptions, so SchemaOptions, rules strategy.Rules) *Doc {
	t.Helper()
	d := New(Options{Strategies: []strategy.Strategy{strategy.NewMemory(rules)}, Mock: mo, Schema: so,
		Wrapper: &Wrapper{Type: mockResult{}}})
	d.Group(GroupInfo{Name: "demo.Item", Title: "条目", Path: "/api/items"})
	d.Register("GET", "/api/items/:id", Op("demo.Item", "get").Resp(mockItem{}), "")
	d.Register("POST", "/api/items", Op("demo.Item", "create").Body(mockItem{}).Resp(mockItem{}), "")
	d.Register("GET", "/api/items", Op("demo.Item", "list").Resp([]mockItem{}).Deprecated(), "")
	d.Register("DELETE", "/api/items/:id", Op("demo.Item", "remove"), "")
	return d
}

func serve(d *Doc, method, target string, body string, hdr ...string) *httptest.ResponseRecorder {
	var r *http.Request
	if body != "" {
		r = httptest.NewRequest(method, target, strings.NewReader(body))
		r.Header.Set("Content-Type", "application/json")
	} else {
		r = httptest.NewRequest(method, target, nil)
	}
	for i := 0; i+1 < len(hdr); i += 2 {
		r.Header.Set(hdr[i], hdr[i+1])
	}
	w := httptest.NewRecorder()
	d.Handler().ServeHTTP(w, r)
	return w
}

func TestMockDisabledByDefault(t *testing.T) {
	d := newMockDoc(t, MockOptions{}, SchemaOptions{}, nil)
	for _, p := range []string{"/csap/mock/api/items/1", "/csap/mock-status"} {
		if w := serve(d, "GET", p, ""); w.Code != 404 {
			t.Errorf("%s 关闭时应 404,实际 %d", p, w.Code)
		}
	}
	if len(d.MethodPaths()) != 0 {
		t.Fatalf("关闭时不注册路由: %v", d.MethodPaths())
	}
	if w := serve(d, "POST", "/csap/schema/import", "{}"); w.Code != 404 {
		t.Fatalf("导入默认关闭: %d", w.Code)
	}
}

func TestMockServing(t *testing.T) {
	rules := strategy.Rules{"demo.Item.get": {Response: map[string]strategy.FieldRule{
		"data.id":    {Include: model.Bool(true)},
		"data.name":  {Include: model.Bool(true), Example: "Zed"},
		"data.email": {Include: model.Bool(true)},
		"data.level": {Include: model.Bool(true), Mock: "@oneOf(1,2,3)"},
		"data.token": {Include: model.Bool(true), Mock: "@uuid"},
		"data.tags":  {Include: model.Bool(true)},
		"code":       {Include: model.Bool(true)}, "message": {Include: model.Bool(true)},
	}}}
	d := newMockDoc(t, MockOptions{Enabled: true}, SchemaOptions{}, rules)
	w := serve(d, "GET", "/csap/mock/api/items/42", "")
	if w.Code != 200 || w.Header().Get("X-CSAP-Mock") != "1" || w.Header().Get("X-CSAP-Mock-Seed") != "csap" ||
		w.Header().Get("X-CSAP-Mock-Operation") != "demo.Item.get" || !strings.HasPrefix(w.Header().Get("Content-Type"), "application/json") {
		t.Fatalf("%d %v", w.Code, w.Header())
	}
	var got struct {
		Code, Message string
		Data          map[string]any
	}
	if err := json.Unmarshal(w.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	if got.Code != "200" || got.Message != "ok" || got.Data["name"] != "Zed" { // 启发式 + example
		t.Fatalf("%s", w.Body.String())
	}
	if _, ok := got.Data["pwd"]; ok {
		t.Fatalf("规则没放行的字段不在文档里,也不该输出: %s", w.Body.String())
	}
	if lv := got.Data["level"].(float64); lv < 1 || lv > 3 {
		t.Fatalf("%v", got.Data["level"])
	}
	if len(got.Data["tags"].([]any)) != 2 {
		t.Fatalf("数组默认长度 2: %s", w.Body.String())
	}
	// 可复现;换种子数据变,结构不变
	if again := serve(d, "GET", "/csap/mock/api/items/7", ""); again.Body.String() != w.Body.String() {
		t.Fatalf("同一种子应可复现(不依赖路径参数)")
	}
	for _, hdr := range [][]string{{"X-CSAP-Mock-Seed", "s2"}} {
		w2 := serve(d, "GET", "/csap/mock/api/items/42", "", hdr...)
		if w2.Header().Get("X-CSAP-Mock-Seed") != "s2" || w2.Body.String() == w.Body.String() {
			t.Fatalf("请求头种子: %s", w2.Body.String())
		}
	}
	w3 := serve(d, "GET", "/csap/mock/api/items/42?_csap_mock_seed=q", "")
	w4 := serve(d, "GET", "/csap/mock/api/items/42?_csap_mock_seed=q", "", "X-CSAP-Mock-Seed", "h") // 请求头优先
	if w3.Header().Get("X-CSAP-Mock-Seed") != "q" || w4.Header().Get("X-CSAP-Mock-Seed") != "h" {
		t.Fatalf("%v %v", w3.Header(), w4.Header())
	}
	// 不认识的路径、方法不对
	nf := serve(d, "GET", "/csap/mock/nope", "")
	if nf.Code != 404 || !strings.Contains(nf.Body.String(), "MOCK_NOT_FOUND") || !strings.Contains(nf.Body.String(), "no documented endpoint for GET /nope") {
		t.Fatalf("%d %s", nf.Code, nf.Body.String())
	}
	na := serve(d, "PUT", "/csap/mock/api/items/42", "")
	if na.Code != 405 || !strings.Contains(na.Body.String(), "MOCK_METHOD_NOT_ALLOWED") || !strings.Contains(na.Header().Get("Allow"), "GET") {
		t.Fatalf("%d %s %v", na.Code, na.Body.String(), na.Header())
	}
	// POST 也能 Mock;没有返回模型的接口 → 200 + 空 body;DEPRECATED 照常
	if p := serve(d, "POST", "/csap/mock/api/items", `{"x":1}`); p.Code != 200 || p.Header().Get("X-CSAP-Mock-Operation") != "demo.Item.create" {
		t.Fatalf("%d", p.Code)
	}
	bare := New(Options{Mock: MockOptions{Enabled: true}}) // 没有统一包装、接口也没声明返回 → 没有返回模型
	bare.Register("DELETE", "/api/items/:id", Op("demo.Item", "remove"), "")
	if del := serve(bare, "DELETE", "/csap/mock/api/items/9", ""); del.Code != 200 || del.Body.Len() != 0 || del.Header().Get("X-CSAP-Mock") != "1" {
		t.Fatalf("%d %q", del.Code, del.Body.String())
	}
	if l := serve(d, "GET", "/csap/mock/api/items", ""); l.Code != 200 || !strings.HasPrefix(strings.TrimSpace(l.Body.String()), "{") {
		t.Fatalf("%d %s", l.Code, l.Body.String())
	}
	// mock-status
	st := serve(d, "GET", "/csap/mock-status", "")
	if st.Code != 200 || !strings.Contains(st.Body.String(), `"enabled":true`) || !strings.Contains(st.Body.String(), `"prefix":"/csap/mock"`) || !strings.Contains(st.Body.String(), `"seed":"csap"`) {
		t.Fatalf("%d %s", st.Code, st.Body.String())
	}
	// 规则里的 mock 进了文档 JSON(字段上的 mock 属性)
	if !strings.Contains(string(d.Snapshot().data()), `"mock":"@uuid"`) {
		t.Fatal("文档里应带字段的 mock 属性")
	}
}

func TestMockOptionsAndAuth(t *testing.T) {
	zero := 0
	d := newMockDoc(t, MockOptions{Enabled: true, Seed: "random", ArraySize: &zero, DisableHeuristics: true}, SchemaOptions{}, nil)
	d.opts.DocAuth = func(r *http.Request) bool { return r.Header.Get("X-Ok") == "1" }
	if w := serve(d, "GET", "/csap/mock/api/items/1", ""); w.Code != 401 {
		t.Fatalf("沿用文档接口的访问控制: %d", w.Code)
	}
	a := serve(d, "GET", "/csap/mock/api/items/1", "", "X-Ok", "1")
	b := serve(d, "GET", "/csap/mock/api/items/1", "", "X-Ok", "1")
	if a.Header().Get("X-CSAP-Mock-Seed") == "random" || a.Header().Get("X-CSAP-Mock-Seed") == b.Header().Get("X-CSAP-Mock-Seed") {
		t.Fatalf("random 每次请求生成新种子: %q %q", a.Header().Get("X-CSAP-Mock-Seed"), b.Header().Get("X-CSAP-Mock-Seed"))
	}
	if !strings.Contains(a.Body.String(), `"tags":[]`) || strings.Contains(a.Body.String(), "@example.com") {
		t.Fatalf("array-size=0 且关闭启发式: %s", a.Body.String())
	}
	paths := d.MethodPaths()
	if len(paths) != 1 || paths[0].Method != "*" || paths[0].Path != "/csap/mock/*path" {
		t.Fatalf("%v", paths)
	}
	_ = mock.Size
}

func TestSchemaEndpoints(t *testing.T) {
	d := newMockDoc(t, MockOptions{}, SchemaOptions{}, nil)
	w := serve(d, "GET", "/csap/schema.json?download=true", "")
	if w.Code != 200 || !strings.Contains(w.Header().Get("Content-Disposition"), "csap-schema.json") {
		t.Fatalf("%d %v", w.Code, w.Header())
	}
	var coll struct {
		Schema string           `json:"$schema"`
		Ops    []map[string]any `json:"x-csap-operations"`
		Defs   map[string]any   `json:"$defs"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &coll); err != nil {
		t.Fatalf("%v %s", err, w.Body.String())
	}
	if coll.Schema != "https://json-schema.org/draft/2020-12/schema" || len(coll.Ops) != 4 {
		t.Fatalf("%+v", coll.Ops)
	}
	if _, ok := coll.Defs["demo.Item.get.response"]; !ok {
		t.Fatalf("defs: %v", coll.Defs)
	}
	if strings.Contains(w.Body.String(), `"code":"0"`) { // 裸 JSON,不套外层
		t.Fatal("不该套 ApidocResult")
	}
	m := serve(d, "GET", "/csap/schema/method?key=demo.Item.list", "")
	var md map[string]any
	if m.Code != 200 || json.Unmarshal(m.Body.Bytes(), &md) != nil || md["deprecated"] != true || md["method"] != "GET" || md["path"] != "/api/items" {
		t.Fatalf("%d %s", m.Code, m.Body.String())
	}
	if nf := serve(d, "GET", "/csap/schema/method?key=nope", ""); nf.Code != 404 {
		t.Fatalf("%d", nf.Code)
	}
}

func TestSchemaImport(t *testing.T) {
	mem := strategy.NewMemory(nil)
	d := New(Options{Strategies: []strategy.Strategy{mem}, Schema: SchemaOptions{ImportEnabled: true, Store: mem}})
	d.Group(GroupInfo{Name: "demo.Item", Path: "/api/items"})
	d.Register("POST", "/api/items", Op("demo.Item", "create").Body(mockItem{}), "")
	body := func(apply bool) string {
		b, _ := json.Marshal(map[string]any{"key": "demo.Item.create", "kind": "request", "section": "body", "apply": apply,
			"schema": map[string]any{"type": "object", "required": []any{"name"}, "properties": map[string]any{
				"name":  map[string]any{"type": "string", "description": "新说明", "minLength": 2, "maxLength": 20, "examples": []any{"Ann"}},
				"ghost": map[string]any{"type": "string"},
				"level": map[string]any{"type": "integer", "minimum": 1, "maximum": 9, "format": "int32", "x-csap-mock": 5},
			}}})
		return string(b)
	}
	type resp struct {
		Code string `json:"code"`
		Data struct {
			Applied   bool                          `json:"applied"`
			Matched   int                           `json:"matched"`
			Rules     map[string]strategy.FieldRule `json:"rules"`
			YAML      string                        `json:"yaml"`
			Unmatched []struct{ Path string }       `json:"unmatched"`
			Skipped   []struct{ Keyword string }    `json:"skipped"`
		} `json:"data"`
	}
	var pv resp
	w := serve(d, "POST", "/csap/schema/import", body(false))
	if err := json.Unmarshal(w.Body.Bytes(), &pv); err != nil || pv.Code != "0" {
		t.Fatalf("%v %s", err, w.Body.String())
	}
	if pv.Data.Applied || pv.Data.Matched != 2 || len(pv.Data.Unmatched) != 1 || pv.Data.Unmatched[0].Path != "body.ghost" ||
		len(pv.Data.Skipped) != 1 || pv.Data.Skipped[0].Keyword != "format" || pv.Data.YAML == "" {
		t.Fatalf("%s", w.Body.String())
	}
	name := pv.Data.Rules["request.name"]
	if name.Description != "新说明" || name.Required == nil || !*name.Required || name.Example != "Ann" || len(name.Validate) != 1 || name.Validate[0].Type != "Length" || name.Validate[0].Pattern != "2,20" {
		t.Fatalf("%+v", name)
	}
	if rs, _ := mem.Load(nil); len(rs) != 0 { //nolint:staticcheck
		t.Fatal("预览不该写入")
	}
	// apply:写入存储,修改人 schema-import,文档刷新
	var ap resp
	w = serve(d, "POST", "/csap/schema/import", body(true))
	if err := json.Unmarshal(w.Body.Bytes(), &ap); err != nil || !ap.Data.Applied {
		t.Fatalf("%v %s", err, w.Body.String())
	}
	rs, _ := mem.Load(nil) //nolint:staticcheck
	if rs["demo.Item.create"].Request["request.name"].Description != "新说明" {
		t.Fatalf("%+v", rs)
	}
	// 导入的必填、校验在运行时生效(文档重新生成)
	m := d.Snapshot().Method("demo.Item", "demo.Item.create")
	found := false
	for _, md := range m.Request {
		for _, p := range md.Parameters {
			if p.Name == "name" && p.Required && p.Description == "新说明" {
				found = true
			}
		}
	}
	if !found {
		t.Fatal("导入后文档应带新规则")
	}
	// 只读:apply 返回明确错误,预览仍可用
	d.opts.Schema.ReadOnly = true
	if w := serve(d, "POST", "/csap/schema/import", body(true)); !strings.Contains(w.Body.String(), `"code":"1007"`) {
		t.Fatalf("%s", w.Body.String())
	}
	if w := serve(d, "POST", "/csap/schema/import", body(false)); !strings.Contains(w.Body.String(), `"code":"0"`) {
		t.Fatalf("%s", w.Body.String())
	}
	// 错误请求
	if w := serve(d, "POST", "/csap/schema/import", `{"key":"nope","kind":"request","schema":{}}`); w.Code != 404 {
		t.Fatalf("%d", w.Code)
	}
	if w := serve(d, "POST", "/csap/schema/import", `{"key":"demo.Item.create","kind":"request","schema":{"properties":{"x":{}}}}`); w.Code != 400 {
		t.Fatalf("没有 section 且不是导出形状应 400: %d %s", w.Code, w.Body.String())
	}
	_, _ = io.Discard.Write(nil)
}
