package devtools

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
)

type result struct {
	Code    string `json:"code" doc:"返回编码" doc_force:"true"`
	Message string `json:"message" doc:"返回的描述"`
	Data    any    `json:"data" doc:"返回的数据"`
}

type address struct {
	City string `json:"city" doc:"城市"`
}

type user struct {
	ID      int64    `json:"id" doc:"用户ID"`
	Name    string   `json:"name" doc:"姓名"`
	Phone   string   `json:"phone" doc:"手机号"`
	Address *address `json:"address" doc:"地址"`
}

type getReq struct {
	ID int64 `uri:"id" doc:"用户ID"`
}

type createReq struct {
	Name  string `json:"name" doc:"姓名" binding:"required"`
	Phone string `json:"phone" doc:"手机号"`
}

const group = "example.UserController"

func newDoc(t *testing.T, ss ...strategy.Strategy) *apidoc.Doc {
	t.Helper()
	d := apidoc.New(apidoc.Options{Strategies: ss, Wrapper: &apidoc.Wrapper{Type: result{}}})
	d.Group(apidoc.GroupInfo{Name: group, Title: "用户", Path: "/api/users"})
	d.Register("GET", "/api/users/:id", apidoc.Op(group, "get").Req(getReq{}).Resp(user{}), "")
	d.Register("POST", "/api/users", apidoc.Op(group, "create").Body(createReq{}).Resp(user{}), "")
	d.Group(apidoc.GroupInfo{Name: "example.Other"})
	d.Register("GET", "/api/other", apidoc.Op("example.Other", "list").Resp([]address{}), "")
	return d
}

type apiResult struct {
	Code    string          `json:"code"`
	Message string          `json:"message"`
	Success bool            `json:"success"`
	Data    json.RawMessage `json:"data"`
}

func call(t *testing.T, h http.Handler, method, url string, body any) (*httptest.ResponseRecorder, apiResult) {
	t.Helper()
	var r *http.Request
	if body != nil {
		b, _ := json.Marshal(body)
		r = httptest.NewRequest(method, url, strings.NewReader(string(b)))
		r.Header.Set("Content-Type", "application/json;charset=UTF-8")
	} else {
		r = httptest.NewRequest(method, url, nil)
	}
	r.SetBasicAuth("admin", "pw")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, r)
	var res apiResult
	if strings.HasPrefix(rec.Header().Get("Content-Type"), "application/json;charset") {
		if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
			t.Fatalf("%s %s: %v\n%s", method, url, err, rec.Body.String())
		}
	}
	return rec, res
}

func ok(t *testing.T, h http.Handler, method, url string, body, out any) {
	t.Helper()
	rec, res := call(t, h, method, url, body)
	if rec.Code != 200 || res.Code != "0" || !res.Success {
		t.Fatalf("%s %s: %d %s %s", method, url, rec.Code, res.Code, res.Message)
	}
	if out != nil {
		if err := json.Unmarshal(res.Data, out); err != nil {
			t.Fatalf("%s %s data: %v %s", method, url, err, res.Data)
		}
	}
}

func newTools(t *testing.T, ss ...strategy.Strategy) (*apidoc.Doc, *Devtools) {
	t.Helper()
	doc := newDoc(t, ss...)
	dt, err := New(doc, Options{Auth: BasicAuth("admin", "pw"), Realm: "devtools"})
	if err != nil {
		t.Fatal(err)
	}
	return doc, dt
}

func TestNewRequiresAuthAndStore(t *testing.T) {
	doc := newDoc(t, strategy.NewMemory(nil))
	if _, err := New(doc, Options{}); !errors.Is(err, ErrNoAuth) {
		t.Fatalf("no auth: %v", err)
	}
	ro := yaml.FS(os.DirFS(t.TempDir()), ".")
	if _, err := New(newDoc(t, ro), Options{Auth: AllowAll}); !errors.Is(err, ErrNoStore) {
		t.Fatalf("read-only store: %v", err)
	}
	defer func() {
		if recover() == nil {
			t.Fatal("MustNew should panic")
		}
	}()
	MustNew(doc, Options{})
}

func TestAuth(t *testing.T) {
	_, dt := newTools(t, strategy.NewMemory(nil))
	for _, p := range []string{"/csap/yaml/scannerApi", "/devtools-ui", "/csap-ui/assets2/x.js"} {
		rec := httptest.NewRecorder()
		dt.ServeHTTP(rec, httptest.NewRequest("GET", p, nil))
		if rec.Code != 401 || !strings.Contains(rec.Header().Get("WWW-Authenticate"), `realm="devtools"`) {
			t.Fatalf("%s: %d %v", p, rec.Code, rec.Header())
		}
	}
	r := httptest.NewRequest("GET", "/", nil)
	r.RemoteAddr = "127.0.0.1:1234"
	if !AllowLocal(r) || defaultUser(r) != "127.0.0.1" {
		t.Fatal("AllowLocal loopback")
	}
	r.RemoteAddr = "10.0.0.1:1"
	if AllowLocal(r) {
		t.Fatal("AllowLocal remote")
	}
	r.Header.Set("X-Internal", "s3cret")
	if !Token("X-Internal", "s3cret")(r) || Token("X-Internal", "other")(r) {
		t.Fatal("Token")
	}
	if !Any(nil, AllowLocal, Token("X-Internal", "s3cret"))(r) || Any(AllowLocal)(r) {
		t.Fatal("Any")
	}
	r.SetBasicAuth("bob", "x")
	if defaultUser(r) != "bob" || BasicAuth("bob", "y")(r) {
		t.Fatal("basic")
	}
}

func TestQueries(t *testing.T) {
	_, dt := newTools(t, strategy.NewMemory(nil))
	var ctrls []model.Controller
	ok(t, dt, "GET", "/csap/yaml/scannerApi", nil, &ctrls)
	if len(ctrls) != 2 || ctrls[0].MethodList == nil || len(ctrls[0].MethodList) != 0 {
		t.Fatalf("scannerApi: %+v", ctrls)
	}
	ok(t, dt, "GET", "/csap/yaml/scannerApi?className="+group, nil, &ctrls)
	if len(ctrls) != 1 || ctrls[0].Value != "用户" {
		t.Fatalf("scannerApi filtered: %+v", ctrls)
	}
	var methods []model.Method
	ok(t, dt, "GET", "/csap/yaml/getAllMethod?className="+group, nil, &methods)
	if len(methods) != 2 {
		t.Fatalf("getAllMethod: %d", len(methods))
	}
	ok(t, dt, "GET", "/csap/yaml/getAllMethod?className=nope", nil, &methods)
	if len(methods) != 0 {
		t.Fatal("unknown class")
	}

	// 返回值字段:外层包装 + data 换成 user
	var fs []field
	ok(t, dt, "POST", "/devtools/getFields", map[string]any{"className": "x", "controllerClassName": group, "methodName": "get", "parameterIndex": -1}, &fs)
	if len(fs) != 3 || fs[2].Name != "data" || len(fs[2].ChildrenField) != 4 || fs[2].ChildrenField[3].Key != "data.address" ||
		fs[2].ChildrenField[3].ChildrenField[0].Key != "data.address.city" {
		t.Fatalf("response fields: %+v", fs)
	}
	// 路径参数(单值):一个与参数同名的字段
	ok(t, dt, "POST", "/devtools/getFields", map[string]any{"controllerClassName": group, "methodName": "get", "parameterIndex": 0}, &fs)
	if len(fs) != 1 || fs[0].Name != "id" || fs[0].DataType != "Long" {
		t.Fatalf("path param fields: %+v", fs)
	}
	// 按类名 + 排除
	name := "github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools.user"
	ok(t, dt, "POST", "/devtools/getFields", map[string]any{"className": name, "excledFields": []string{"phone", "address"}}, &fs)
	if len(fs) != 2 || fs[1].Name != "name" {
		t.Fatalf("className fields: %+v", fs)
	}
	// 接口找不到时退回类名;都找不到返回 []
	ok(t, dt, "POST", "/devtools/getFields", map[string]any{"className": name, "controllerClassName": group, "methodName": "nope"}, &fs)
	if len(fs) != 4 {
		t.Fatalf("fallback: %d", len(fs))
	}
	ok(t, dt, "POST", "/devtools/getFields", map[string]any{"className": "nope"}, &fs)
	if len(fs) != 0 {
		t.Fatal("unknown type")
	}

	var pm struct {
		PatternList     []map[string]any `json:"patternList"`
		PatternTypeList []map[string]any `json:"patternTypeList"`
	}
	ok(t, dt, "GET", "/csap/yaml/validatePatternTypes", nil, &pm)
	if len(pm.PatternList) != 46 || len(pm.PatternTypeList) != 4 || pm.PatternList[0]["pattern"] != "NotNull" ||
		pm.PatternList[0]["code"] != "1001" || pm.PatternTypeList[0]["pattern"] != nil {
		t.Fatalf("patterns: %d %v", len(pm.PatternList), pm.PatternTypeList)
	}
	for _, p := range pm.PatternList {
		if p["descr"] == "汉字" && !strings.Contains(p["pattern"].(string), `\u4e00`) {
			t.Fatalf("pattern should use \\u for the JS page: %v", p["pattern"])
		}
	}
	var b bool
	ok(t, dt, "POST", "/devtools/hiddenMethod", map[string]any{"className": group, "methodName": "get", "hidden": true}, &b)
	if !b {
		t.Fatal("hiddenMethod")
	}
}

func TestErrors(t *testing.T) {
	_, dt := newTools(t, strategy.NewMemory(nil))
	rec, _ := call(t, dt, "GET", "/csap/yaml/getMethodValidateFields?className=a", nil)
	if rec.Code != 400 || !strings.Contains(rec.Body.String(), "methodName") {
		t.Fatalf("missing param: %d %s", rec.Code, rec.Body.String())
	}
	if rec, _ := call(t, dt, "GET", "/csap/yaml/addRequestParam", nil); rec.Code != 405 {
		t.Fatalf("method: %d", rec.Code)
	}
	if rec, _ := call(t, dt, "GET", "/csap/yaml/nope", nil); rec.Code != 404 {
		t.Fatalf("unknown: %d", rec.Code)
	}
	r := httptest.NewRequest("POST", "/csap/yaml/addResponseParam", strings.NewReader(`{}`))
	r.Header.Set("Content-Type", "text/plain")
	r.SetBasicAuth("admin", "pw")
	w := httptest.NewRecorder()
	dt.ServeHTTP(w, r)
	if w.Code != 415 {
		t.Fatalf("content type: %d", w.Code)
	}
	for _, body := range []any{
		map[string]any{"methodName": "get"},
		map[string]any{"className": group},
		map[string]any{"className": group, "methodName": "nope"},
	} {
		rec, res := call(t, dt, "POST", "/csap/yaml/addResponseParam", body)
		if rec.Code != 200 || res.Success || res.Code != "500" || res.Message == "" {
			t.Fatalf("bad body %v: %d %+v", body, rec.Code, res)
		}
	}
	rec, res := call(t, dt, "POST", "/csap/yaml/rollback", map[string]any{})
	if res.Success {
		t.Fatalf("rollback without id: %d %+v", rec.Code, res)
	}
	r = httptest.NewRequest("POST", "/csap/yaml/writeFieldMethod", strings.NewReader(`{bad`))
	r.Header.Set("Content-Type", "application/json")
	r.SetBasicAuth("admin", "pw")
	w = httptest.NewRecorder()
	dt.ServeHTTP(w, r)
	if !strings.Contains(w.Body.String(), "请求体格式错误") {
		t.Fatalf("bad json: %s", w.Body.String())
	}
}

// 前端"返回参数"弹窗:勾选字段保存 -> 文档和返回过滤立即生效 -> 修改记录 -> 回滚。
func TestResponseParamFlowWithHistory(t *testing.T) {
	ctx := context.Background()
	mem := strategy.NewMemory(nil)
	doc, dt := newTools(t, mem)
	body := map[string]any{
		"className": group, "methodName": "get",
		"returnType": map[string]any{
			"appendName": "", "modelType": "T_OBJECT", "packageName": "x.result",
			"fields": []any{
				map[string]any{"name": "code", "dataType": "String"},
				map[string]any{"name": "data", "parameters": []any{
					map[string]any{"name": "id", "required": true},
					map[string]any{"name": "name"},
				}},
			},
		},
	}
	ok(t, dt, "POST", "/csap/yaml/addResponseParam", body, nil)

	ep := doc.Snapshot().Endpoint("GET", "/api/users/:id")
	if ep.ResponseMask == nil || len(ep.Rules.Response) != 4 || *ep.Rules.Response["data.id"].Required != true {
		t.Fatalf("rules not applied: %+v", ep.Rules.Response)
	}
	m := doc.Snapshot().Method(group, group+".get")
	data := m.Response[0].Parameters[len(m.Response[0].Parameters)-1]
	if data.Name != "data" || len(data.Parameters) != 2 {
		t.Fatalf("doc not refreshed: %+v", data)
	}

	// 再改一次,只留 id
	body["returnType"].(map[string]any)["fields"] = []any{map[string]any{"name": "data", "parameters": []any{map[string]any{"name": "id"}}}}
	ok(t, dt, "POST", "/csap/yaml/addResponseParam", body, nil)

	var hist []changeView
	ok(t, dt, "GET", "/csap/yaml/history?className="+group+"&methodName=get", nil, &hist)
	if len(hist) != 2 || hist[0].UpdatedBy != "admin" || hist[0].Before == nil || hist[1].Before != nil || hist[0].UpdatedAt == 0 {
		t.Fatalf("history: %+v", hist)
	}
	var all []changeView
	ok(t, dt, "GET", "/csap/yaml/history?limit=1", nil, &all)
	if len(all) != 1 || all[0].ID != hist[0].ID {
		t.Fatalf("history limit: %+v", all)
	}

	var v map[string]int64
	ok(t, dt, "POST", "/csap/yaml/rollback", map[string]any{"id": hist[0].ID}, &v)
	if v["version"] != 3 {
		t.Fatalf("rollback version: %v", v)
	}
	if _, ok := doc.Snapshot().Endpoint("GET", "/api/users/:id").Rules.Response["data.name"]; !ok {
		t.Fatal("rollback not applied to doc")
	}
	ok(t, dt, "POST", "/csap/yaml/rollback", map[string]any{"id": hist[1].ID}, nil)
	if ep := doc.Snapshot().Endpoint("GET", "/api/users/:id"); ep.ResponseMask != nil {
		t.Fatal("rollback to no rules should remove filtering")
	}
	if h, _ := mem.History(ctx, "", 0); len(h) != 4 {
		t.Fatalf("records: %d", len(h))
	}
	if _, res := call(t, dt, "POST", "/csap/yaml/rollback", map[string]any{"id": 99}); res.Success {
		t.Fatal("unknown change id")
	}
}

// 前端"请求参数"弹窗:保存必填、位置和校验规则;只替换提交的参数,其他参数和字段说明保留。
func TestRequestParamFlowAndValidateFields(t *testing.T) {
	tr := true
	mem := strategy.NewMemory(strategy.Rules{group + ".create": {
		Request: map[string]strategy.FieldRule{
			"request.name":  {Description: "保留的说明", Validate: []strategy.ValidateRule{{Type: "NotEmpty", Pattern: "NotEmpty", Code: "1"}}},
			"request.phone": {Required: &tr},
			"requestX.a":    {Required: &tr}, // 前缀相同但不是同一个参数,不能被删
		},
		Response: map[string]strategy.FieldRule{"data.id": {Include: &tr}},
	}})
	doc, dt := newTools(t, mem)
	ok(t, dt, "POST", "/csap/yaml/addRequestParam", map[string]any{
		"className": group, "methodName": "create", "methodParamName": "request",
		"requestParams": []any{map[string]any{
			"packageName": "x.createReq", "modelType": "OBJECT",
			"fields": []any{
				// 没点过校验弹窗:保留原来的校验规则
				map[string]any{"name": "extra", "paramType": "DEFAULT"},
				map[string]any{"name": "name", "required": true, "paramType": "body", "clickValidate": false,
					"validate": []any{map[string]any{"type": "Pattern", "pattern": "x"}}},
				// 点过:用提交的,按 level 排序;code 可以是数字;\u 写法转成 RE2
				map[string]any{"name": "phone", "required": false, "clickValidate": true, "validate": []any{
					map[string]any{"type": "Pattern", "pattern": `^[\u4e00-\u9fa5]+$`, "code": 1002, "message": "中文", "level": 2},
					map[string]any{"type": "Pattern", "pattern": "PATTERN_35", "code": "1001", "message": "手机号", "level": 1},
				}},
			},
		}},
	}, nil)
	rules, _ := mem.Load(context.Background())
	r := rules[group+".create"]
	name, phone := r.Request["request.name"], r.Request["request.phone"]
	if name.Description != "保留的说明" || !*name.Required || name.ParamType != "BODY" || len(name.Validate) != 1 || name.Validate[0].Code != "1" {
		t.Fatalf("name: %+v", name)
	}
	if *phone.Required || len(phone.Validate) != 2 || phone.Validate[0].Code != "1001" || phone.Validate[1].Code != "1002" ||
		phone.Validate[1].Pattern != `^[\x{4e00}-\x{9fa5}]+$` {
		t.Fatalf("phone: %+v", phone)
	}
	if f, ok := r.Request["request.extra"]; !ok || f.ParamType != "" {
		t.Fatalf("DEFAULT paramType should not be saved: %+v", f)
	}
	if _, ok := r.Request["requestX.a"]; !ok || len(r.Response) != 1 {
		t.Fatalf("other params / response must be kept: %+v", r)
	}
	if ep := doc.Snapshot().Endpoint("POST", "/api/users"); len(ep.Checks) == 0 {
		t.Fatal("checks not rebuilt")
	}

	var pd paramData
	ok(t, dt, "GET", "/csap/yaml/getMethodValidateFields?className="+group+"&methodName=create&fieldName=request.phone", nil, &pd)
	if pd.Required || len(pd.Validate) != 2 || pd.Validate[1].Pattern != `^[\u4e00-\u9fa5]+$` || pd.Validate[1].Code != "1002" {
		t.Fatalf("validate fields: %+v", pd)
	}
	ok(t, dt, "GET", "/csap/yaml/getMethodValidateFields?className="+group+"&methodName=create&fieldName=nope", nil, &pd)
	if pd.Validate == nil || len(pd.Validate) != 0 || pd.Required {
		t.Fatalf("empty validate fields: %+v", pd)
	}
}

func TestWriteEndpointsWithYAML(t *testing.T) {
	dir := t.TempDir()
	ys := yaml.Dir(dir)
	doc, dt := newTools(t, strategy.NewMemory(nil), ys) // 默认写最后一个可写来源
	if dt.Store() != ys {
		t.Fatal("default store should be the last saver")
	}
	ok(t, dt, "POST", "/csap/yaml/writeFieldMethod", map[string]any{
		group: map[string]any{"get": map[string]any{
			"response": map[string]any{"data": map[string]any{"include": true}, "data.id": map[string]any{"include": true, "required": true}},
		}},
	}, nil)
	b, err := os.ReadFile(filepath.Join(dir, "UserController-method-field.yaml"))
	if err != nil || !strings.Contains(string(b), "data.id:") {
		t.Fatalf("yaml not written: %v %s", err, b)
	}
	ok(t, dt, "POST", "/csap/yaml/write", map[string]any{"methodFieldMap": map[string]any{
		group: map[string]any{"create": map[string]any{
			"request": map[string]any{"request.name": map[string]any{"required": true, "paramType": "body",
				"validate": []any{map[string]any{"type": "NotEmpty", "pattern": "NotEmpty", "code": 1001, "message": "姓名不能为空"}}}},
		}},
	}}, nil)
	if c := doc.Snapshot().Endpoint("POST", "/api/users").Checks; len(c) == 0 || c[0].Rules[0].Code != "1001" {
		t.Fatalf("write not applied: %+v", c)
	}

	// writeSelect:没有规则的接口生成完整规则,有规则的不动
	ok(t, dt, "POST", "/csap/yaml/writeSelect", []string{}, nil)
	rules, _ := ys.Load(context.Background())
	other := rules["example.Other.list"]
	if len(other.Response) == 0 || *other.Response["data.city"].Include != true {
		t.Fatalf("writeSelect: %+v", other)
	}
	if len(rules[group+".get"].Response) != 2 {
		t.Fatalf("existing rules must be kept: %+v", rules[group+".get"])
	}
	ok(t, dt, "POST", "/csap/yaml/writeSelect", []string{"nope"}, nil)

	var hist []changeView
	ok(t, dt, "GET", "/csap/yaml/history", nil, &hist)
	if len(hist) != 3 {
		t.Fatalf("yaml history: %d", len(hist))
	}
}

func TestInitialRulesRequest(t *testing.T) {
	doc := newDoc(t)
	r := initialRules(doc.Snapshot().FullMethod(group + ".get"))
	if f, ok := r.Request["id.id"]; !ok || !*f.Required || f.ParamType != "PATH" {
		t.Fatalf("path param: %+v", r.Request)
	}
	r = initialRules(doc.Snapshot().FullMethod(group + ".create"))
	if f := r.Request["request.name"]; f.Required == nil || !*f.Required {
		t.Fatalf("body: %+v", r.Request)
	}
	if initialRules(nil).Request != nil {
		t.Fatal("nil method")
	}
}

func TestUIAndRoutes(t *testing.T) {
	doc, dt := newTools(t, strategy.NewMemory(nil))
	rec, _ := call(t, dt, "GET", "/devtools-ui/api", nil)
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), "/csap-ui/assets2/") {
		t.Fatalf("page: %d", rec.Code)
	}
	if len(dt.Routes()) != 10 || len(dt.Patterns()) != 5 {
		t.Fatalf("routes: %v %v", dt.Routes(), dt.Patterns())
	}
	noUI, _ := New(doc, Options{Auth: AllowAll, DisableUI: true})
	if len(noUI.Routes()) != 4 || len(noUI.Patterns()) != 2 {
		t.Fatal("routes without ui")
	}
	rec = httptest.NewRecorder()
	noUI.ServeHTTP(rec, httptest.NewRequest("GET", "/devtools-ui", nil))
	if rec.Code != 404 {
		t.Fatalf("ui disabled: %d", rec.Code)
	}
}

func TestSetStatus(t *testing.T) {
	mem := strategy.NewMemory(nil)
	doc, dt := newTools(t, mem)
	status := func() (string, string) {
		m := doc.Snapshot().Method(group, group+".get")
		return m.Status, m.StatusNote
	}
	ok(t, dt, "POST", "/csap/yaml/setStatus", map[string]any{"className": group, "methodName": "get", "status": "DEPRECATED", "statusNote": "请改用 /v2"}, nil)
	if s, n := status(); s != "DEPRECATED" || n != "请改用 /v2" {
		t.Fatalf("%s %s", s, n)
	}
	// 只有状态的规则保存后还在(不当空规则删除),并留修改记录
	rs, _ := mem.Load(context.Background())
	if rs[group+".get"].Status != "DEPRECATED" {
		t.Fatalf("%+v", rs)
	}
	// 再保存字段规则不丢状态
	ok(t, dt, "POST", "/csap/yaml/addResponseParam", map[string]any{
		"className": group, "methodName": "get",
		"returnType": map[string]any{"appendName": "", "modelType": "T_OBJECT", "packageName": "x", "fields": []any{map[string]any{"name": "code", "dataType": "String"}}},
	}, nil)
	if s, _ := status(); s != "DEPRECATED" {
		t.Fatalf("保存字段规则丢了状态: %s", s)
	}
	// null 清除覆盖
	ok(t, dt, "POST", "/csap/yaml/setStatus", map[string]any{"className": group, "methodName": "get", "status": nil}, nil)
	if s, n := status(); s != "DEFAULT" || n != "" {
		t.Fatalf("%s %s", s, n)
	}
	// 非法状态
	if rec, res := call(t, dt, "POST", "/csap/yaml/setStatus", map[string]any{"className": group, "methodName": "get", "status": "BOGUS"}); rec.Code != 200 || res.Success {
		t.Fatalf("非法状态应失败: %s", rec.Body.String())
	}
}
