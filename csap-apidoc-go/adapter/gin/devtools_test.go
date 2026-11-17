package gin

import (
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

// 用 devtools 接口改返回字段,下一个请求的实际返回立即按新规则过滤。
func TestMountDevtoolsChangesResponse(t *testing.T) {
	r, _, doc := setup(t, validate.Off)
	MountDevtools(r, devtools.MustNew(doc, devtools.Options{Auth: devtools.AllowAll}))

	get := func(p string) *httptest.ResponseRecorder {
		rec := httptest.NewRecorder()
		r.ServeHTTP(rec, httptest.NewRequest("GET", p, nil))
		return rec
	}
	if body := get("/api/users/1").Body.String(); !strings.Contains(body, `"name"`) || strings.Contains(body, "secret") {
		t.Fatalf("before: %s", body)
	}
	req := httptest.NewRequest("POST", "/csap/yaml/addResponseParam", strings.NewReader(
		`{"className":"U","methodName":"get","returnType":{"appendName":"","fields":[{"name":"data","parameters":[{"name":"id"}]}]}}`))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)
	if !strings.Contains(rec.Body.String(), `"success":true`) {
		t.Fatalf("save: %s", rec.Body.String())
	}
	if body := get("/api/users/1").Body.String(); strings.Contains(body, `"name"`) || !strings.Contains(body, `"id"`) {
		t.Fatalf("after: %s", body)
	}
	for _, p := range []string{"/devtools-ui", "/devtools-ui/api", "/csap/yaml/scannerApi"} {
		if rec := get(p); rec.Code != 200 {
			t.Fatalf("%s: %d", p, rec.Code)
		}
	}
	// 管理接口不出现在文档里
	if strings.Contains(get("/csap/apidoc").Body.String(), "/csap/yaml") {
		t.Fatal("devtools routes leaked into doc")
	}
	html := get("/devtools-ui").Body.String()
	i := strings.Index(html, "/csap-ui/assets2/")
	asset := html[i : i+strings.IndexByte(html[i:], '"')]
	if rec := get(asset); rec.Code != 200 {
		t.Fatalf("asset %s: %d", asset, rec.Code)
	}
}
