package gin

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
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

type createReq struct {
	Name string `json:"name"`
}

func setup(t *testing.T, mode validate.Mode) (*gin.Engine, *strategy.Memory, *apidoc.Doc) {
	gin.SetMode(gin.TestMode)
	mem := strategy.NewMemory(strategy.Rules{
		"U.get": {Response: map[string]strategy.FieldRule{
			"data.id":   {Include: model.Bool(true)},
			"data.name": {Include: model.Bool(true)},
		}},
		"U.create": {Request: map[string]strategy.FieldRule{
			"request.name": {Required: model.Bool(true), Validate: []strategy.ValidateRule{{Type: "NotEmpty", Code: "1001", Message: "名称不能为空"}}},
		}},
	})
	doc := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{mem}, Wrapper: &apidoc.Wrapper{Type: result{}}})
	r := gin.New()
	r.Use(ResponseFilter(doc), RequestValidator(validate.New(doc, validate.Options{Mode: mode})))
	g := Mount(r, doc)
	api := g.Group("/api")
	api.GET("/users/:id", apidoc.Op("U", "get").Resp(user{}), func(c *gin.Context) {
		c.JSON(http.StatusCreated, result{Code: "0", Message: "成功", Success: true, Data: user{ID: 1, Name: "a", Password: "secret"}})
	})
	api.POST("/users", apidoc.Op("U", "create").Body(createReq{}), func(c *gin.Context) {
		var req createReq
		_ = c.ShouldBindJSON(&req) // 校验后请求体仍可读
		c.JSON(200, gin.H{"name": req.Name, "extra": 1})
	})
	api.GET("/raw", nil, func(c *gin.Context) { c.String(200, "not json") })
	return r, mem, doc
}

func do(r http.Handler, method, url, body string) *httptest.ResponseRecorder {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(method, url, strings.NewReader(body))
	if body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	r.ServeHTTP(rec, req)
	return rec
}

func TestResponseFilterMiddleware(t *testing.T) {
	r, mem, doc := setup(t, validate.Off)
	rec := do(r, "GET", "/api/users/1", "")
	if rec.Code != http.StatusCreated {
		t.Fatalf("status lost: %d", rec.Code)
	}
	if got := rec.Body.String(); got != `{"code":"0","message":"成功","data":{"id":1,"name":"a"}}` {
		t.Fatalf("filtered: %s", got)
	}
	// 规则热更新:放行 password 之外再去掉 name
	_, _ = mem.Save(context.Background(), "U.get", strategy.MethodRules{Response: map[string]strategy.FieldRule{
		"data.id": {Include: model.Bool(true)},
	}}, "test")
	_ = doc.Refresh(context.Background())
	if got := do(r, "GET", "/api/users/1", "").Body.String(); got != `{"code":"0","message":"成功","data":{"id":1}}` {
		t.Fatalf("after reload: %s", got)
	}
	// 没有规则的接口、非 JSON 不受影响
	if got := do(r, "POST", "/api/users", `{"name":"x"}`).Body.String(); got != `{"extra":1,"name":"x"}` {
		t.Fatalf("unfiltered: %s", got)
	}
	if got := do(r, "GET", "/api/raw", "").Body.String(); got != "not json" {
		t.Fatalf("raw: %s", got)
	}
}

func TestRequestValidatorMiddleware(t *testing.T) {
	r, _, _ := setup(t, validate.Enforce)
	rec := do(r, "POST", "/api/users", `{"name":""}`)
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), `"code":"1001"`) || !strings.Contains(rec.Body.String(), "字段name,名称不能为空") {
		t.Fatalf("enforce: %d %s", rec.Code, rec.Body.String())
	}
	if got := do(r, "POST", "/api/users", `{"name":"ok"}`).Body.String(); got != `{"extra":1,"name":"ok"}` {
		t.Fatalf("pass: %s", got)
	}
	r, _, _ = setup(t, validate.Observe)
	if got := do(r, "POST", "/api/users", `{"name":""}`).Body.String(); got != `{"extra":1,"name":""}` {
		t.Fatalf("observe should pass: %s", got)
	}
}

// 默认只裁剪 2xx;错误体原样输出。ShouldFilter 可自定义,FilterEndpoints 可按接口关闭。
func TestResponseFilterStatusAndOverrides(t *testing.T) {
	gin.SetMode(gin.TestMode)
	newEngine := func(opts apidoc.Options) *gin.Engine {
		opts.Strategies = []strategy.Strategy{strategy.NewMemory(strategy.Rules{
			"U.get": {Response: map[string]strategy.FieldRule{"data.id": {Include: model.Bool(true)}}},
		})}
		opts.Wrapper = &apidoc.Wrapper{Type: result{}}
		doc := apidoc.New(opts)
		r := gin.New()
		r.Use(ResponseFilter(doc))
		Mount(r, doc).GET("/users/:id", apidoc.Op("U", "get").Resp(user{}), func(c *gin.Context) {
			status := http.StatusOK
			if c.Query("fail") != "" {
				status = http.StatusBadRequest
			}
			c.JSON(status, result{Code: "0", Message: "m", Success: true, Data: user{ID: 1, Name: "a", Password: "p"}})
		})
		return r
	}
	const filtered = `{"code":"0","message":"m","data":{"id":1}}`
	const raw = `{"code":"0","message":"m","data":{"id":1,"name":"a","password":"p"},"success":true}`

	r := newEngine(apidoc.Options{})
	if got := do(r, "GET", "/users/1", "").Body.String(); got != filtered {
		t.Fatalf("2xx: %s", got)
	}
	rec := do(r, "GET", "/users/1?fail=1", "")
	if rec.Code != http.StatusBadRequest || rec.Body.String() != raw {
		t.Fatalf("4xx must not be filtered: %d %s", rec.Code, rec.Body.String())
	}

	r = newEngine(apidoc.Options{ShouldFilter: func(req *http.Request, status int) bool { return req.URL.Query().Get("raw") == "" }})
	if got := do(r, "GET", "/users/1?raw=1", "").Body.String(); got != raw {
		t.Fatalf("custom ShouldFilter false: %s", got)
	}
	if got := do(r, "GET", "/users/1?fail=1", "").Body.String(); got != filtered {
		t.Fatalf("custom ShouldFilter true on 4xx: %s", got)
	}

	r = newEngine(apidoc.Options{FilterEndpoints: map[string]filter.Mode{"U.get": filter.ModeOff}})
	if got := do(r, "GET", "/users/1", "").Body.String(); got != raw {
		t.Fatalf("endpoint off: %s", got)
	}
}

// 业务失败也返回 200:ShouldFilterBody 按响应体判断,失败体原样输出;与 ShouldFilter 组合时先粗筛再细判。
func TestResponseFilterBody(t *testing.T) {
	gin.SetMode(gin.TestMode)
	newEngine := func(opts apidoc.Options) *gin.Engine {
		opts.Strategies = []strategy.Strategy{strategy.NewMemory(strategy.Rules{
			"U.get": {Response: map[string]strategy.FieldRule{"data.id": {Include: model.Bool(true)}}},
		})}
		opts.Wrapper = &apidoc.Wrapper{Type: result{}}
		doc := apidoc.New(opts)
		r := gin.New()
		r.Use(ResponseFilter(doc))
		Mount(r, doc).GET("/users/:id", apidoc.Op("U", "get").Resp(user{}), func(c *gin.Context) {
			c.JSON(http.StatusOK, result{Code: "0", Message: "m", Success: c.Query("biz") == "", Data: user{ID: 1, Name: "a", Password: "p"}})
		})
		return r
	}
	const filtered = `{"code":"0","message":"m","data":{"id":1}}`
	const rawOK = `{"code":"0","message":"m","data":{"id":1,"name":"a","password":"p"},"success":true}`
	const rawFail = `{"code":"0","message":"m","data":{"id":1,"name":"a","password":"p"},"success":false}`

	r := newEngine(apidoc.Options{})
	if got := do(r, "GET", "/users/1?biz=1", "").Body.String(); got != filtered {
		t.Fatalf("no hook: %s", got)
	}

	r = newEngine(apidoc.Options{ShouldFilterBody: apidoc.SuccessField("success", true)})
	if got := do(r, "GET", "/users/1", "").Body.String(); got != filtered {
		t.Fatalf("success body should be filtered: %s", got)
	}
	if got := do(r, "GET", "/users/1?biz=1", "").Body.String(); got != rawFail {
		t.Fatalf("business failure must pass through: %s", got)
	}

	var gotStatus int
	var gotBody string
	r = newEngine(apidoc.Options{ShouldFilterBody: func(_ *http.Request, status int, body []byte) bool {
		gotStatus, gotBody = status, string(body)
		return false
	}})
	if got := do(r, "GET", "/users/1", "").Body.String(); got != rawOK || gotStatus != 200 || gotBody != rawOK {
		t.Fatalf("custom hook: %s %d %s", got, gotStatus, gotBody)
	}

	called := false
	r = newEngine(apidoc.Options{
		ShouldFilter:     func(*http.Request, int) bool { return false },
		ShouldFilterBody: func(*http.Request, int, []byte) bool { called = true; return true },
	})
	if got := do(r, "GET", "/users/1", "").Body.String(); got != rawOK || called {
		t.Fatalf("ShouldFilter false: %s called=%v", got, called)
	}
	r = newEngine(apidoc.Options{
		ShouldFilter:     func(*http.Request, int) bool { return true },
		ShouldFilterBody: func(*http.Request, int, []byte) bool { return true },
	})
	if got := do(r, "GET", "/users/1", "").Body.String(); got != filtered {
		t.Fatalf("both true: %s", got)
	}
}
