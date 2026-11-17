package ui

import (
	"bytes"
	"embed"
	"io/fs"
	"net/http"
	"path"
	"strings"
	"sync"
)

// static 是 csap-apidoc-ui 的构建产物,由 scripts/sync-ui.sh 从 ../csap-apidoc-ui 同步。
//
//go:embed static
var static embed.FS

// FS 返回内嵌的静态文件(根目录即 static/)。
func FS() fs.FS {
	sub, _ := fs.Sub(static, "static")
	return sub
}

var contentTypes = map[string]string{
	".html": "text/html;charset=UTF-8",
	".ico":  "image/x-icon",
	".js":   "application/javascript",
	".css":  "text/css",
	".png":  "image/png",
	".svg":  "image/svg+xml",
}

// Handler 提供文档页面:
//
//	/csap-api.html             页面入口
//	/favicon.ico               图标
//	<prefix>/assets/...        js、css、图片
//
// 构建产物里写死了 /assets/ 开头的地址,这里在输出时改写成 <prefix>/assets/,
// 避免和业务自己的 /assets/ 冲突。
func Handler(prefix string) http.Handler {
	prefix = "/" + strings.Trim(prefix, "/")
	root := FS()
	var (
		once      sync.Once
		rewritten map[string][]byte
	)
	load := func() {
		rewritten = map[string][]byte{}
		_ = fs.WalkDir(root, ".", func(p string, d fs.DirEntry, err error) error {
			if err != nil || d.IsDir() {
				return err
			}
			ext := path.Ext(p)
			if ext != ".html" && ext != ".js" && ext != ".css" {
				return nil
			}
			b, err := fs.ReadFile(root, p)
			if err != nil {
				return err
			}
			for _, q := range []string{`"`, `'`, `(`} {
				b = bytes.ReplaceAll(b, []byte(q+"/assets/"), []byte(q+prefix+"/assets/"))
			}
			rewritten[p] = b
			return nil
		})
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		once.Do(load)
		var name string
		switch p := r.URL.Path; {
		case p == "/csap-api.html":
			name = "csap-api.html"
			w.Header().Set("Cache-Control", "no-cache")
		case p == "/favicon.ico":
			name = "favicon.ico"
		case strings.HasPrefix(p, prefix+"/assets/"):
			name = strings.TrimPrefix(p, prefix+"/")
			// 文件名带内容哈希,可以长期缓存
			w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
		default:
			http.NotFound(w, r)
			return
		}
		name = path.Clean(name)
		if strings.HasPrefix(name, "..") {
			http.NotFound(w, r)
			return
		}
		b, ok := rewritten[name]
		if !ok {
			var err error
			b, err = fs.ReadFile(root, name)
			if err != nil {
				w.Header().Del("Cache-Control")
				http.NotFound(w, r)
				return
			}
		}
		if ct, ok := contentTypes[path.Ext(name)]; ok {
			w.Header().Set("Content-Type", ct)
		}
		if r.Method == http.MethodHead {
			return
		}
		_, _ = w.Write(b)
	})
}
