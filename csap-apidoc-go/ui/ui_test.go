package ui

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func fetch(t *testing.T, h http.Handler, method, url string) (*http.Response, string) {
	t.Helper()
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(method, url, nil))
	res := rec.Result()
	b, _ := io.ReadAll(res.Body)
	return res, string(b)
}

// 页面里引用的每个资源(改写后的地址)都能取到,且不再有指向根路径 /assets/ 的地址。
func TestDocsPageAssets(t *testing.T) {
	h := Handler("/csap-ui")
	res, html := fetch(t, h, "GET", "/csap-api.html")
	if res.StatusCode != 200 || !strings.HasPrefix(res.Header.Get("Content-Type"), "text/html") {
		t.Fatalf("html: %d %s", res.StatusCode, res.Header.Get("Content-Type"))
	}
	if strings.Contains(html, `"/assets/`) || !strings.Contains(html, `"/csap-ui/assets/`) {
		t.Fatalf("html not rewritten: %s", html)
	}
	checked := 0
	for _, a := range referencedAssets([]byte(html), "/csap-ui", "assets") {
		res, body := fetch(t, h, "GET", "/csap-ui/"+a)
		if res.StatusCode != 200 || body == "" {
			t.Fatalf("%s: %d", a, res.StatusCode)
		}
		if strings.HasSuffix(a, ".js") {
			if strings.Contains(body, `"/assets/`) {
				t.Fatalf("%s still references /assets/", a)
			}
			for _, b := range referencedAssets([]byte(body), "/csap-ui", "assets") {
				if r, _ := fetch(t, h, "GET", "/csap-ui/"+b); r.StatusCode != 200 {
					t.Fatalf("%s -> %s: %d", a, b, r.StatusCode)
				}
				checked++
			}
		}
		checked++
	}
	if checked < 3 {
		t.Fatalf("only %d assets referenced", checked)
	}
	if r, _ := fetch(t, h, "GET", "/favicon.ico"); r.StatusCode != 200 || r.Header.Get("Content-Type") != "image/x-icon" {
		t.Fatalf("favicon: %d", r.StatusCode)
	}
	if r, _ := fetch(t, h, "HEAD", "/csap-ui/assets/../csap-api.html"); r.StatusCode != 404 {
		t.Fatalf("traversal: %d", r.StatusCode)
	}
	if r, _ := fetch(t, h, "GET", "/csap-ui/assets/js/nope.js"); r.StatusCode != 404 || r.Header.Get("Cache-Control") != "" {
		t.Fatalf("missing: %d", r.StatusCode)
	}
	if r, _ := fetch(t, h, "GET", "/other"); r.StatusCode != 404 {
		t.Fatalf("other: %d", r.StatusCode)
	}
}

func TestDevtoolsPageAssets(t *testing.T) {
	h := DevtoolsHandler("csap-ui/")
	for _, p := range []string{"/devtools-ui", "/devtools-ui/", "/devtools-ui/api", "/devtools-ui/login"} {
		res, html := fetch(t, h, "GET", p)
		if res.StatusCode != 200 || !strings.Contains(html, `<div id="root">`) {
			t.Fatalf("%s: %d", p, res.StatusCode)
		}
	}
	_, html := fetch(t, h, "GET", "/devtools-ui")
	if strings.Contains(html, `"/assets2/`) {
		t.Fatalf("html not rewritten: %s", html)
	}
	seen := map[string]bool{}
	queue := referencedAssets([]byte(html), "/csap-ui", "assets2")
	for len(queue) > 0 {
		a := queue[0]
		queue = queue[1:]
		if seen[a] {
			continue
		}
		seen[a] = true
		res, body := fetch(t, h, "GET", "/csap-ui/"+a)
		if res.StatusCode != 200 {
			t.Fatalf("%s: %d", a, res.StatusCode)
		}
		if strings.HasSuffix(a, ".js") || strings.HasSuffix(a, ".css") {
			if strings.Contains(body, `"/assets2/`) || strings.Contains(body, `"assets2/`) {
				t.Fatalf("%s still references root assets2", a)
			}
			queue = append(queue, referencedAssets([]byte(body), "/csap-ui", "assets2")...)
		}
	}
	// 入口 + 按需加载的页面(登录页、接口管理页、404)都应该能找到
	if len(seen) < 8 {
		t.Fatalf("only %d assets reachable: %v", len(seen), seen)
	}
	if r, _ := fetch(t, h, "GET", "/csap-ui/assets/x.js"); r.StatusCode != 404 {
		t.Fatalf("docs asset served by devtools handler")
	}
}
