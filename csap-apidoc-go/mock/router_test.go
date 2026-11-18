package mock

import (
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

func ep(key, apiPath, path string, methods ...string) *model.Method {
	return &model.Method{Key: key, ApiPath: []string{apiPath}, Paths: []string{path}, Methods: methods}
}

func TestRouterMatching(t *testing.T) {
	doc := &model.Response{ApiList: []*model.Controller{
		{MethodList: []*model.Method{
			ep("a.byId", "/api/users", "/{id}", "GET"),
			ep("a.me", "/api/users", "/me", "GET"),                     // 字面段多,优先于 {id}
			ep("a.create", "/api/users", "", "POST"),                   // 根路径
			ep("a.re", "/api/orders", "/{no:[0-9]+}", "GET", "DELETE"), // 带正则的占位符
			ep("a.any", "/api/any", "/{x}"),                            // methods 为空:任意方法
			ep("a.zz", "/api/dup", "/{x}", "GET"),                      // 同样的占位符,key 小的优先
			ep("a.aa", "/api/dup", "/{y}", "GET"),
			{Key: "a.hidden", ApiPath: []string{"/api/hid"}, Methods: []string{"GET"}, Hidden: true},
		}},
		{Hidden: true, MethodList: []*model.Method{ep("h.x", "/api/hc", "", "GET")}},
	}}
	r := NewRouter(doc)
	cases := []struct {
		method, path string
		status       int
		key          string
		allow        []string
	}{
		{"GET", "/api/users/42", 200, "a.byId", nil},
		{"GET", "/api/users/me", 200, "a.me", nil},
		{"get", "/api/users/me/", 200, "a.me", nil}, // 方法忽略大小写,首尾 / 规整
		{"POST", "/api/users", 200, "a.create", nil},
		{"DELETE", "/api/orders/7", 200, "a.re", nil}, // 不校验正则
		{"PATCH", "/api/any/z", 200, "a.any", nil},
		{"GET", "/api/dup/1", 200, "a.aa", nil},
		{"PUT", "/api/users/42", 405, "", []string{"GET"}},
		{"PUT", "/api/orders/7", 405, "", []string{"DELETE", "GET"}},
		{"GET", "/api/users/42/extra", 404, "", nil},
		{"GET", "/api/hid", 404, "", nil},
		{"GET", "/api/hc", 404, "", nil},
		{"GET", "/nope", 404, "", nil},
	}
	for _, c := range cases {
		res := r.Match(c.method, c.path)
		if res.Status != c.status || (c.key != "" && (res.Method == nil || res.Method.Key != c.key)) {
			t.Errorf("%s %s: status=%d method=%v, want %d %s", c.method, c.path, res.Status, res.Method, c.status, c.key)
		}
		if c.status == 405 && len(res.Allow) != len(c.allow) {
			t.Errorf("%s %s: allow %v want %v", c.method, c.path, res.Allow, c.allow)
		}
	}
}
