package apidoc

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

type result struct {
	Code    string `json:"code" doc:"返回编码" doc_force:"true"`
	Message string `json:"message" doc:"返回的描述" doc_force:"true"`
	Data    any    `json:"data" doc:"返回的数据"`
}

type page[T any] struct {
	Total int64 `json:"total" doc:"总数"`
	Rows  []T   `json:"rows" doc:"数据"`
}

type item struct {
	ID       int64     `json:"id" doc:"主键" doc_example:"1"`
	Name     string    `json:"name" doc:"名称" doc_desc:"详细说明"`
	Secret   string    `json:"-"`
	Hidden   string    `json:"hidden" doc_hidden:"true"`
	When     time.Time `json:"when"`
	Parent   *item     `json:"parent"`
	internal int
}

type base struct {
	Limit int `form:"limit" doc:"条数"`
}

type listReq struct {
	base
	ID    string `form:"id" doc:"ID" binding:"required"`
	Token string `header:"X-Token"`
}

type createReq struct {
	Name  string `json:"name" binding:"required"`
	Items []item `json:"items"`
}

func newDoc(rules strategy.Rules) *Doc {
	d := New(Options{
		Strategies: []strategy.Strategy{strategy.NewMemory(rules)},
		Wrapper:    &Wrapper{Type: result{}},
	})
	d.Group(GroupInfo{Name: "com.x.ItemController", Title: "条目", Path: "/api/items"})
	d.Register("GET", "/api/items", Op("com.x.ItemController", "list").Summary("列表").Req(listReq{}).Resp(page[item]{}), "")
	d.Register("GET", "/api/items/:id", Op("com.x.ItemController", "get").Resp(item{}), "")
	d.Register("POST", "/api/items", Op("com.x.ItemController", "create").Body(createReq{}).NoWrap(), "")
	d.Register("GET", "/api/secret", Op("com.x.ItemController", "secret").Hidden(), "")
	return d
}

func findParam(ps []*model.Parameter, name string) *model.Parameter {
	for _, p := range ps {
		if p.Name == name {
			return p
		}
	}
	return nil
}

func TestBuildRequestAndResponse(t *testing.T) {
	snap := newDoc(nil).Snapshot()
	c := snap.Controller("com.x.ItemController")
	if c == nil || c.Value != "条目" || c.SimpleName != "ItemController" {
		t.Fatalf("controller: %+v", c)
	}
	if len(c.MethodList) != 3 {
		t.Fatalf("hidden op should be excluded, got %d methods", len(c.MethodList))
	}
	list := snap.Method("com.x.ItemController", "com.x.ItemController.list")
	if list.ApiPath[0] != "/api/items" || len(list.Paths) != 0 {
		t.Fatalf("paths: %v %v", list.ApiPath, list.Paths)
	}
	// 请求:limit(嵌入展开)、id、X-Token 都是单值参数
	if got := list.ParamNames; !reflect.DeepEqual(got, []string{"limit", "id", "X-Token"}) {
		t.Fatalf("paramNames: %v", got)
	}
	id := list.Request[1].Parameters[0]
	if id.KeyName != "id.id" || !id.Required || id.ParamType != model.ParamQuery || *id.LongDataType != "string" {
		t.Fatalf("id param: %+v", id)
	}
	if list.Request[2].Parameters[0].ParamType != model.ParamHeader {
		t.Fatal("X-Token should be HEADER")
	}
	// 返回:包装 + data 替换成 page[item]
	resp := list.Response[0]
	if resp.ModelType != model.ModelTObject {
		t.Fatalf("wrapper model type %s", resp.ModelType)
	}
	data := findParam(resp.Parameters, "data")
	if data == nil || data.DataType != "page<item>" {
		t.Fatalf("data: %+v", data)
	}
	rows := findParam(data.Children.Parameters, "rows")
	if rows.DataType != "List<item>" || *rows.ModelType != model.ModelArray || rows.Children.ModelType != model.ModelArray {
		t.Fatalf("rows: %+v", rows)
	}
	ps := rows.Children.Parameters
	if findParam(ps, "Secret") != nil || findParam(ps, "hidden") != nil || findParam(ps, "internal") != nil {
		t.Fatal("json:- / doc_hidden / unexported fields must be skipped")
	}
	name := findParam(ps, "name")
	if name.KeyName != "data.rows.name" || name.Value != "名称" || name.Description != "详细说明" {
		t.Fatalf("name: %+v", name)
	}
	if findParam(ps, "when").DataType != "Date" {
		t.Fatal("time.Time should be Date")
	}
	// 自引用只展开一层
	parent := findParam(ps, "parent")
	if parent.Children != nil {
		t.Fatal("self reference should not recurse")
	}

	get := snap.Method("com.x.ItemController", "com.x.ItemController.get")
	if get.ApiPath[0] != "/api/items" || get.Paths[0] != "/{id}" {
		t.Fatalf("get paths: %v %v", get.ApiPath, get.Paths)
	}

	create := snap.Method("com.x.ItemController", "com.x.ItemController.create")
	if len(create.Response) != 0 {
		t.Fatal("NoWrap without Resp should have no response")
	}
	if len(create.Request) != 1 || create.ParamNames[0] != "request" {
		t.Fatalf("create request: %+v", create.Request)
	}
	n := findParam(create.Request[0].Parameters, "name")
	if n.KeyName != "request.name" || !n.Required || n.ParamType != model.ParamBody {
		t.Fatalf("body name: %+v", n)
	}
	items := findParam(create.Request[0].Parameters, "items")
	if findParam(items.Children.Parameters, "id").KeyName != "request.items.id" {
		t.Fatal("nested request keyName")
	}
}

func TestRulesControlVisibility(t *testing.T) {
	tr, fa := model.Bool(true), model.Bool(false)
	snap := newDoc(strategy.Rules{
		"com.x.ItemController.list": {
			Request: map[string]strategy.FieldRule{
				"id.id": {Required: fa, Description: "规则说明"},
			},
			Response: map[string]strategy.FieldRule{
				"data":         {Include: tr},
				"data.rows":    {Include: tr},
				"data.rows.id": {Include: tr, Required: tr},
				"data.total":   {Include: fa},
			},
		},
		"com.x.ItemController.create": {
			Request: map[string]strategy.FieldRule{
				"request.name": {ParamType: "QUERY", Validate: []strategy.ValidateRule{{Type: "NotEmpty", Code: "1001", Message: "不能为空", Level: 1}}},
			},
		},
	}).Snapshot()

	list := snap.Method("com.x.ItemController", "com.x.ItemController.list")
	id := list.Request[1].Parameters[0]
	if id.Required || id.Description != "规则说明" {
		t.Fatalf("rule should override: %+v", id)
	}
	resp := list.Response[0].Parameters
	// code、message 是 doc_force,始终显示
	if findParam(resp, "code") == nil || findParam(resp, "message") == nil {
		t.Fatal("forced fields must show")
	}
	data := findParam(resp, "data")
	if findParam(data.Children.Parameters, "total") != nil {
		t.Fatal("include=false must hide")
	}
	rows := findParam(data.Children.Parameters, "rows")
	if len(rows.Children.Parameters) != 1 || !rows.Children.Parameters[0].Required {
		t.Fatalf("only data.rows.id should show: %+v", rows.Children.Parameters)
	}

	create := snap.Method("com.x.ItemController", "com.x.ItemController.create")
	ps := create.Request[0].Parameters
	if len(ps) != 1 || ps[0].ParamType != model.ParamQuery || len(ps[0].Validate) != 1 || ps[0].Validate[0].Code != "1001" {
		t.Fatalf("request rules: %+v", ps)
	}
	if ep := snap.Endpoint("POST", "/api/items"); ep == nil || ep.Key != "com.x.ItemController.create" {
		t.Fatalf("endpoint lookup: %+v", ep)
	}
}

func TestParamKeysStableAndUnique(t *testing.T) {
	collect := func() []string {
		var keys []string
		var walk func(ps []*model.Parameter)
		walk = func(ps []*model.Parameter) {
			for _, p := range ps {
				keys = append(keys, p.Key)
				walk(p.Parameters)
			}
		}
		for _, c := range newDoc(nil).Snapshot().Doc.ApiList {
			for _, m := range c.MethodList {
				for _, md := range append(m.Request, m.Response...) {
					walk(md.Parameters)
				}
			}
		}
		return keys
	}
	a, b := collect(), collect()
	if !reflect.DeepEqual(a, b) {
		t.Fatal("keys should be deterministic")
	}
	seen := map[string]bool{}
	for _, k := range a {
		if seen[k] {
			t.Fatalf("duplicate key %s", k)
		}
		seen[k] = true
	}
}

func TestRouteSourceAndInferName(t *testing.T) {
	d := New(Options{})
	d.Register("GET", "/a", nil, "github.com/x/app/user.(*Controller).List-fm")
	d.SetRouteSource(func() []Route {
		return []Route{
			{Method: "GET", Path: "/a", Handler: "ignored"},
			{Method: "POST", Path: "/b/:id", Handler: "github.com/x/app/user.Create"},
			{Method: "GET", Path: "/c", Handler: "main.main.func1"},
			{Method: "GET", Path: "/csap/apidoc", Handler: "x"},
			{Method: "GET", Path: "/csap-ui/assets/*filepath", Handler: "x"},
		}
	})
	snap := d.Snapshot()
	names := map[string][]string{}
	for _, c := range snap.Doc.ApiList {
		for _, m := range c.MethodList {
			names[c.Name] = append(names[c.Name], m.Name)
		}
	}
	want := map[string][]string{
		"github.com/x/app/user.Controller": {"List"},
		"github.com/x/app/user":            {"Create"},
		"main":                             {"get_c"},
	}
	if !reflect.DeepEqual(names, want) {
		t.Fatalf("got %v", names)
	}
	if m := snap.Method("github.com/x/app/user", "github.com/x/app/user.Create"); m.ApiPath[0] != "/b/{id}" {
		t.Fatalf("path conversion: %v", m.ApiPath)
	}
}

func TestDuplicateNamesGetUniqueKeys(t *testing.T) {
	d := New(Options{})
	d.Register("GET", "/x", Op("G", "h"), "")
	d.Register("POST", "/x", Op("G", "h"), "")
	ms := d.Snapshot().Controller("G").MethodList
	if ms[0].Key != "G.h" || ms[1].Key != "G.h#2" {
		t.Fatalf("keys: %s %s", ms[0].Key, ms[1].Key)
	}
}

func TestDisplayName(t *testing.T) {
	cases := map[reflect.Type]string{
		reflect.TypeOf(page[item]{}):         "page<item>",
		reflect.TypeOf(map[string][]int64{}): "Map<String, List<Long>>",
		reflect.TypeOf([]*item{}):            "List<item>",
		reflect.TypeOf(new(any)).Elem():      "Object",
	}
	for typ, want := range cases {
		if got := javaName(typ); got != want {
			t.Errorf("%v: got %q want %q", typ, got, want)
		}
	}
	if got := fullName(reflect.TypeOf(page[item]{})); got != "github.com/csap-ai/csap-apidoc/csap-apidoc-go.page" {
		t.Errorf("fullName: %s", got)
	}
}

type namedPage[T any] struct {
	Rows []T `json:"rows"`
}

func (namedPage[T]) ApidocName() string { return "分页返回信息" }

type namedItem struct {
	ID int64 `json:"id"`
}

func (*namedItem) ApidocName() string { return "条目" } // 指针接收者也生效

type plainItem struct {
	ID int64 `json:"id"`
}

func TestModelDisplayNameOverride(t *testing.T) {
	d := New(Options{
		Wrapper:   &Wrapper{Type: result{}},
		TypeNames: map[reflect.Type]string{reflect.TypeOf(&plainItem{}): "普通条目", reflect.TypeOf(result{}): "统一返回"},
	})
	d.Register("GET", "/a", Op("G", "a").Resp(namedPage[namedItem]{}), "")
	d.Register("GET", "/b", Op("G", "b").Resp([]plainItem{}), "")
	snap := d.Snapshot()
	a := snap.Method("G", "G.a").Response[0]
	if a.Value == nil || *a.Value != "统一返回" {
		t.Fatalf("wrapper name from TypeNames: %v", a.Value)
	}
	data := findParam(a.Parameters, "data")
	if v := data.Children.Value; v == nil || *v != "分页返回信息" {
		t.Fatalf("ApidocName (value receiver): %v", v)
	}
	if data.DataType != "namedPage<namedItem>" {
		t.Fatalf("dataType must keep the type name: %s", data.DataType)
	}
	rows := findParam(data.Children.Parameters, "rows")
	if v := rows.Children.Value; v == nil || *v != "条目" {
		t.Fatalf("ApidocName (pointer receiver): %v", v)
	}
	b := snap.Method("G", "G.b").Response[0]
	data = findParam(b.Parameters, "data")
	if v := data.Children.Value; v == nil || *v != "普通条目" {
		t.Fatalf("TypeNames with pointer key: %v", v)
	}
	// 没有覆盖时仍是类型名
	d = New(Options{})
	d.Register("GET", "/c", Op("G", "c").Resp(plainItem{}), "")
	if v := d.Snapshot().Method("G", "G.c").Response[0].Value; v == nil || *v != "plainItem" {
		t.Fatalf("default name: %v", v)
	}
}

func get(t *testing.T, h http.Handler, url string) *httptest.ResponseRecorder {
	t.Helper()
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest("GET", url, nil))
	return rec
}

func decode(t *testing.T, rec *httptest.ResponseRecorder) map[string]any {
	t.Helper()
	var m map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &m); err != nil {
		t.Fatalf("decode %q: %v", rec.Body.String(), err)
	}
	return m
}

func TestHTTPEndpoints(t *testing.T) {
	d := newDoc(nil)
	h := d.Handler()

	rec := get(t, h, "/csap/apidoc")
	if ct := rec.Header().Get("Content-Type"); ct != jsonContentType {
		t.Fatalf("content-type %q", ct)
	}
	m := decode(t, rec)
	if m["code"] != "0" || m["success"] != true || m["message"] != "成功" || m["language"] != "ZH" {
		t.Fatalf("wrapper: %v", m)
	}
	if !strings.HasPrefix(rec.Body.String(), "{\n\t\"code\":\"0\",\n\t\"data\":{\n\t\t\"apiInfo\":{") {
		t.Fatalf("format: %.80q", rec.Body.String())
	}

	// parent
	m = decode(t, get(t, h, "/csap/apidoc/parent"))
	apiList := m["data"].(map[string]any)["apiList"].([]any)
	child := apiList[0].(map[string]any)["children"].([]any)[1].(map[string]any)
	if child["path"] != "/api/items/{id}" || child["method"] != "GET" || child["isLeaf"] != true {
		t.Fatalf("tree child: %v", child)
	}

	// method
	m = decode(t, get(t, h, "/csap/apidoc/method?name=com.x.ItemController&key=com.x.ItemController.list"))
	md := m["data"].(map[string]any)
	if md["patch"] != "/api/items" || len(md["request"].([]any)) != 3 {
		t.Fatalf("method detail: %v", md)
	}
	if rec := get(t, h, "/csap/apidoc/method?name=nope&key=nope"); rec.Code != 200 || rec.Body.Len() != 0 || rec.Header().Get("Content-Type") != "" {
		t.Fatalf("not found should be empty 200, got %d %q", rec.Code, rec.Body.String())
	}
	if rec := get(t, h, "/csap/apidoc/method?name=x"); rec.Code != 400 {
		t.Fatalf("missing key should be 400, got %d", rec.Code)
	}

	// {name}?key= 只保留一个方法,且不改快照
	m = decode(t, get(t, h, "/csap/apidoc/com.x.ItemController?key=com.x.ItemController.get"))
	list := m["data"].(map[string]any)["apiList"].([]any)
	if len(list) != 1 || len(list[0].(map[string]any)["methodList"].([]any)) != 1 {
		t.Fatalf("filtered: %v", list)
	}
	m = decode(t, get(t, h, "/csap/apidoc/nope?key=x"))
	if len(m["data"].(map[string]any)["apiList"].([]any)) != 0 {
		t.Fatal("unknown name should give empty apiList")
	}
	if n := len(d.Snapshot().Controller("com.x.ItemController").MethodList); n != 3 {
		t.Fatalf("snapshot mutated: %d methods", n)
	}
	m = decode(t, get(t, h, "/csap/apidoc/anything"))
	if len(m["data"].(map[string]any)["apiList"].([]any)) != 1 {
		t.Fatal("without key should return full doc")
	}

	// openapi
	m = decode(t, get(t, h, "/csap/openapi.json"))
	if m["openapi"] != "3.1.0" {
		t.Fatalf("openapi: %v", m["openapi"])
	}

	// postman:与 Java 一致的 Content-Type,两次输出相同
	rec = get(t, h, "/csap/openapi/postman")
	if rec.Code != 200 || rec.Header().Get("Content-Type") != "application/json;charset=UTF-8" {
		t.Fatalf("postman: %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	var pm map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &pm); err != nil || pm["info"] == nil || pm["item"] == nil {
		t.Fatalf("postman body: %v %s", err, rec.Body.String())
	}
	if again := get(t, h, "/csap/openapi/postman"); again.Body.String() != rec.Body.String() {
		t.Fatal("postman output not deterministic")
	}

	// UI
	rec = get(t, h, "/csap-api.html")
	if !strings.Contains(rec.Body.String(), `src="/csap-ui/assets/js/`) {
		t.Fatalf("asset prefix not rewritten: %s", rec.Body.String())
	}
	if rec := get(t, h, "/csap-ui/assets/png/ICON-425279ef.png"); rec.Code != 200 || rec.Header().Get("Content-Type") != "image/png" {
		t.Fatalf("asset: %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	if rec := get(t, h, "/csap-ui/assets/../../go.mod"); rec.Code != 404 {
		t.Fatalf("path traversal: %d", rec.Code)
	}
}

func TestDocAuth(t *testing.T) {
	d := New(Options{DocAuth: func(r *http.Request) bool { return r.Header.Get("X-Doc") == "1" }})
	h := d.Handler()
	if rec := get(t, h, "/csap/apidoc"); rec.Code != 401 {
		t.Fatalf("want 401, got %d", rec.Code)
	}
	req := httptest.NewRequest("GET", "/csap/apidoc", nil)
	req.Header.Set("X-Doc", "1")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != 200 {
		t.Fatalf("want 200, got %d", rec.Code)
	}
}

func TestWatchReloadsRules(t *testing.T) {
	mem := strategy.NewMemory(nil)
	d := New(Options{Strategies: []strategy.Strategy{mem}})
	d.Register("GET", "/x", Op("G", "x").Resp(item{}), "")
	if n := len(d.Snapshot().Method("G", "G.x").Response[0].Parameters); n != 4 {
		t.Fatalf("before: %d fields", n)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() { _ = d.Watch(ctx) }()
	time.Sleep(20 * time.Millisecond)
	if _, err := mem.Save(ctx, "G.x", strategy.MethodRules{Response: map[string]strategy.FieldRule{"id": {Include: model.Bool(true)}}}, "test"); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		if len(d.Snapshot().Method("G", "G.x").Response[0].Parameters) == 1 {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("rules not reloaded")
}

func TestInferName(t *testing.T) {
	cases := []struct{ h, group, name string }{
		{"github.com/x/app/user.(*Controller).List-fm", "github.com/x/app/user.Controller", "List"},
		{"github.com/x/app/user.Controller.Get-fm", "github.com/x/app/user.Controller", "Get"},
		{"github.com/x/app/user.Create", "github.com/x/app/user", "Create"},
		{"github.com/x/app/user.(*Controller).List.func1", "github.com/x/app/user.Controller", "get_users_id"},
		{"main.main.func1", "main", "get_users_id"},
		{"", "default", "get_users_id"},
	}
	for _, c := range cases {
		g, n := inferName(c.h, "GET", "/users/:id")
		if g != c.group || n != c.name {
			t.Errorf("%s: got (%s, %s) want (%s, %s)", c.h, g, n, c.group, c.name)
		}
	}
}
