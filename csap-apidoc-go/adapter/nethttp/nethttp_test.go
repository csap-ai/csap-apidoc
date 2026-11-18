package nethttp

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

type result struct {
	Code    string `json:"code" doc_force:"true"`
	Message string `json:"message" doc_force:"true"`
	Data    any    `json:"data" doc_force:"true"`
	Success bool   `json:"success"`
}

type user struct {
	ID       int64  `json:"id"`
	Name     string `json:"name"`
	Password string `json:"password"`
}

type getReq struct {
	ID int64 `in:"path" json:"id" doc:"ID"`
}

type createReq struct {
	Name string `json:"name"`
}

type controller struct{}

func (controller) Get(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(result{Code: "0", Message: "成功", Success: true,
		Data: user{ID: 1, Name: r.PathValue("id"), Password: "secret"}})
}

func setup(t *testing.T, mode validate.Mode) (*http.ServeMux, *strategy.Memory, *apidoc.Doc) {
	t.Helper()
	mem := strategy.NewMemory(strategy.Rules{
		"U.get": {Response: map[string]strategy.FieldRule{
			"data.id":   {Include: model.Bool(true)},
			"data.name": {Include: model.Bool(true)},
		}},
		"U.create": {Request: map[string]strategy.FieldRule{
			"request.name": {Required: model.Bool(true), Validate: []strategy.ValidateRule{{Type: "NotEmpty", Code: "1001", Message: "名称不能为空"}}},
		}},
		"U.noct": {Response: map[string]strategy.FieldRule{"a": {Include: model.Bool(true)}}},
	})
	doc := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{mem}, Wrapper: &apidoc.Wrapper{Type: result{}}})
	mux := http.NewServeMux()
	m := Mount(mux, doc)
	m.Use(ResponseFilter(doc), RequestValidator(validate.New(doc, validate.Options{Mode: mode})))
	api := m.Group("/api")
	api.GET("/users/{id}", apidoc.Op("U", "get").Req(getReq{}).Resp(user{}), controller{}.Get)
	users := api.Group("/users")
	users.POST("", apidoc.Op("U", "create").Body(createReq{}), func(w http.ResponseWriter, r *http.Request) {
		var req createReq
		_ = json.NewDecoder(r.Body).Decode(&req) // 校验后请求体仍可读
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"name":"`+req.Name+`","extra":1}`)
	})
	api.Handle("GET /noct", apidoc.Op("U", "noct").NoWrap().Resp(map[string]int{}), http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = io.WriteString(w, `{"a":1,"b":2}`) // 没设 Content-Type
	}))
	api.HandleFunc("/raw", nil, func(w http.ResponseWriter, r *http.Request) { _, _ = io.WriteString(w, "not json") })
	api.GET("/exact/{$}", nil, func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) })
	return mux, mem, doc
}

func do(h http.Handler, method, url, body string) *httptest.ResponseRecorder {
	var r *http.Request
	if body != "" {
		r = httptest.NewRequest(method, url, strings.NewReader(body))
		r.Header.Set("Content-Type", "application/json")
	} else {
		r = httptest.NewRequest(method, url, nil)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, r)
	return rec
}

func TestResponseFilter(t *testing.T) {
	mux, _, _ := setup(t, validate.Off)
	rec := do(mux, "GET", "/api/users/7", "")
	body := rec.Body.String()
	if rec.Code != http.StatusCreated || strings.Contains(body, "secret") || strings.Contains(body, "success") ||
		!strings.Contains(body, `"name":"7"`) || !strings.Contains(body, `"code":"0"`) {
		t.Fatalf("%d %s", rec.Code, body)
	}
	if rec := do(mux, "GET", "/api/noct", ""); strings.Contains(rec.Body.String(), `"b"`) {
		t.Fatalf("no content-type: %s", rec.Body.String())
	}
	if rec := do(mux, "GET", "/api/raw", ""); rec.Body.String() != "not json" {
		t.Fatalf("raw: %s", rec.Body.String())
	}
	if rec := do(mux, "GET", "/api/exact/", ""); rec.Code != 204 {
		t.Fatalf("exact: %d", rec.Code)
	}
	if rec := do(mux, "HEAD", "/api/users/7", ""); rec.Code != http.StatusCreated {
		t.Fatalf("head: %d", rec.Code)
	}
}

func TestRequestValidator(t *testing.T) {
	mux, _, _ := setup(t, validate.Enforce)
	rec := do(mux, "POST", "/api/users", `{"name":""}`)
	if !strings.Contains(rec.Body.String(), `"code":"1001"`) || strings.Contains(rec.Body.String(), "extra") {
		t.Fatalf("enforce: %s", rec.Body.String())
	}
	rec = do(mux, "POST", "/api/users", `{"name":"ok"}`)
	if rec.Body.String() != `{"name":"ok","extra":1}` {
		t.Fatalf("pass: %s", rec.Body.String())
	}
	// 路径参数类型校验走 r.PathValue
	if rec := do(mux, "GET", "/api/users/abc", ""); !strings.Contains(rec.Body.String(), "数据格式错误") {
		t.Fatalf("path type: %s", rec.Body.String())
	}
}

func TestDocRoutes(t *testing.T) {
	mux, mem, doc := setup(t, validate.Off)
	for _, p := range []string{"/csap/apidoc", "/csap/apidoc/parent", "/csap/openapi.json", "/csap/openapi/postman",
		"/csap-api.html", "/favicon.ico", "/csap/apidoc/U?key=U.get"} {
		if rec := do(mux, "GET", p, ""); rec.Code != 200 {
			t.Fatalf("%s: %d", p, rec.Code)
		}
	}
	snap := doc.Snapshot()
	m := snap.Method("U", "U.get")
	if m == nil || m.ApiPath[0] != "/api/users/{id}" || m.Request[0].Parameters[0].ParamType != "PATH" {
		t.Fatalf("doc: %+v", m)
	}
	if e := snap.Method("U", "U.create"); e == nil || e.Methods[0] != "POST" {
		t.Fatal("create")
	}
	if c := snap.Controller("github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/nethttp"); c == nil {
		t.Fatal("inferred group for anonymous handler")
	}
	if snap.Endpoint("GET", "/api/exact/") == nil {
		t.Fatal("{$} should be stripped in doc")
	}

	// 后台管理改规则,返回立即变化
	MountDevtools(mux, devtools.MustNew(doc, devtools.Options{Auth: devtools.AllowAll}))
	rec := do(mux, "POST", "/csap/yaml/addResponseParam",
		`{"className":"U","methodName":"get","returnType":{"fields":[{"name":"data","parameters":[{"name":"id"}]}]}}`)
	if !strings.Contains(rec.Body.String(), `"success":true`) {
		t.Fatalf("devtools: %s", rec.Body.String())
	}
	if body := do(mux, "GET", "/api/users/7", "").Body.String(); strings.Contains(body, `"name"`) || !strings.Contains(body, `"id":1`) {
		t.Fatalf("after devtools: %s", body)
	}
	for _, p := range []string{"/devtools-ui", "/devtools-ui/api", "/csap/yaml/scannerApi"} {
		if rec := do(mux, "GET", p, ""); rec.Code != 200 {
			t.Fatalf("%s: %d", p, rec.Code)
		}
	}
	if h, _ := mem.History(context.Background(), "U.get", 0); len(h) != 1 {
		t.Fatalf("history: %d", len(h))
	}
}

func TestHelpers(t *testing.T) {
	cases := map[string][3]string{
		"GET /a/{id}":         {"GET", "", "/a/{id}"},
		"/b":                  {"", "", "/b"},
		"POST example.com/c/": {"POST", "example.com", "/c/"},
	}
	for in, want := range cases {
		m, h, p := splitPattern(in)
		if [3]string{m, h, p} != want {
			t.Fatalf("%s: %v", in, [3]string{m, h, p})
		}
	}
	if muxPath("/csap/apidoc/*name") != "/csap/apidoc/" || muxPath("/x/:id") != "/x/{id}" {
		t.Fatal("muxPath")
	}
	for _, c := range [][3]string{{"", "", "/"}, {"/api", "", "/api"}, {"/api", "/", "/api/"}, {"/api", "/u/", "/api/u/"}, {"", "/u", "/u"}} {
		if got := joinPaths(c[0], c[1]); got != c[2] {
			t.Fatalf("join %q %q = %q", c[0], c[1], got)
		}
	}
	if funcName(nil) != "" || handlerName(http.RedirectHandler("/", 301)) != "" || reflectPointer(1) != 0 {
		t.Fatal("names")
	}
	if Route(httptest.NewRequest("GET", "/", nil)) != "" {
		t.Fatal("route")
	}
	if New := (&Mux{mux: http.NewServeMux()}); New.ServeMux() == nil {
		t.Fatal("ServeMux")
	}
}

// 默认只裁剪 2xx;错误体原样输出。ShouldFilter 可自定义,FilterEndpoints 可按接口关闭。
func TestResponseFilterStatusAndOverrides(t *testing.T) {
	newMux := func(opts apidoc.Options) *http.ServeMux {
		opts.Strategies = []strategy.Strategy{strategy.NewMemory(strategy.Rules{
			"U.get": {Response: map[string]strategy.FieldRule{"data.id": {Include: model.Bool(true)}}},
		})}
		opts.Wrapper = &apidoc.Wrapper{Type: result{}}
		doc := apidoc.New(opts)
		mux := http.NewServeMux()
		m := Mount(mux, doc)
		m.Use(ResponseFilter(doc))
		m.GET("/users/{id}", apidoc.Op("U", "get").Resp(user{}), func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			if r.URL.Query().Get("fail") != "" {
				w.WriteHeader(http.StatusInternalServerError)
			}
			_, _ = io.WriteString(w, `{"code":"0","message":"m","data":{"id":1,"name":"a","password":"p"},"success":true}`)
		})
		return mux
	}
	const filtered = `{"code":"0","message":"m","data":{"id":1}}`
	const raw = `{"code":"0","message":"m","data":{"id":1,"name":"a","password":"p"},"success":true}`

	mux := newMux(apidoc.Options{})
	if got := do(mux, "GET", "/users/1", "").Body.String(); got != filtered {
		t.Fatalf("2xx: %s", got)
	}
	rec := do(mux, "GET", "/users/1?fail=1", "")
	if rec.Code != http.StatusInternalServerError || rec.Body.String() != raw {
		t.Fatalf("5xx must not be filtered: %d %s", rec.Code, rec.Body.String())
	}

	mux = newMux(apidoc.Options{ShouldFilter: func(r *http.Request, status int) bool { return r.URL.Query().Get("raw") == "" }})
	if got := do(mux, "GET", "/users/1?raw=1", "").Body.String(); got != raw {
		t.Fatalf("custom ShouldFilter false: %s", got)
	}
	if got := do(mux, "GET", "/users/1?fail=1", "").Body.String(); got != filtered {
		t.Fatalf("custom ShouldFilter true on 5xx: %s", got)
	}

	mux = newMux(apidoc.Options{FilterEndpoints: map[string]filter.Mode{"U.get": filter.ModeOff}})
	if got := do(mux, "GET", "/users/1", "").Body.String(); got != raw {
		t.Fatalf("endpoint off: %s", got)
	}
}
