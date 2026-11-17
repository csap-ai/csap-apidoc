package gin

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
)

type userCtl struct{}

func (userCtl) Get(c *gin.Context) { c.JSON(200, gin.H{"id": c.Param("id")}) }

type getReq struct {
	ID int64 `uri:"id" doc:"用户ID"`
}

func TestMountAndRegister(t *testing.T) {
	gin.SetMode(gin.TestMode)
	doc := apidoc.New(apidoc.Options{})
	r := gin.New()
	g := Mount(r, doc)
	api := g.Group("/api")
	api.GET("/users/:id", apidoc.Op("", "").Summary("查询用户").Req(getReq{}), userCtl{}.Get)
	api.POST("/users", apidoc.Op("UserController", "create"), func(c *gin.Context) {})
	r.DELETE("/raw/:id", func(c *gin.Context) {})

	// 业务路由照常工作
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, httptest.NewRequest("GET", "/api/users/7", nil))
	if rec.Body.String() != `{"id":"7"}` {
		t.Fatalf("business route: %s", rec.Body.String())
	}

	snap := doc.Snapshot()
	group := "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/gin.userCtl"
	m := snap.Method(group, group+".Get")
	if m == nil {
		t.Fatalf("inferred method missing, groups: %v", names(snap))
	}
	if m.ApiPath[0] != "/api/users/{id}" || m.Request[0].Parameters[0].ParamType != "PATH" {
		t.Fatalf("method: %+v", m)
	}
	if snap.Method("UserController", "UserController.create") == nil {
		t.Fatal("declared method missing")
	}
	if snap.Endpoint("DELETE", "/raw/:id") == nil {
		t.Fatal("undeclared route should be collected")
	}
	for _, c := range snap.Doc.ApiList {
		for _, mm := range c.MethodList {
			if len(mm.ApiPath) > 0 && (mm.ApiPath[0] == "/csap/apidoc" || mm.ApiPath[0] == "/csap-api.html") {
				t.Fatal("doc routes must not appear in doc")
			}
		}
	}

	// 文档接口挂上了
	for _, p := range []string{"/csap/apidoc", "/csap/apidoc/parent", "/csap/apidoc/" + group + "?key=x", "/csap/openapi.json", "/csap-api.html"} {
		rec := httptest.NewRecorder()
		r.ServeHTTP(rec, httptest.NewRequest("GET", p, nil))
		if rec.Code != http.StatusOK {
			t.Fatalf("%s: %d", p, rec.Code)
		}
	}
	rec = httptest.NewRecorder()
	r.ServeHTTP(rec, httptest.NewRequest("GET", "/csap/apidoc/method?name="+group+"&key="+group+".Get", nil))
	var res map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatal(err)
	}
	if res["data"].(map[string]any)["title"] != "查询用户" {
		t.Fatalf("method detail: %v", res)
	}
}

func names(s *apidoc.Snapshot) []string {
	var out []string
	for _, c := range s.Doc.ApiList {
		out = append(out, c.Name)
	}
	return out
}
