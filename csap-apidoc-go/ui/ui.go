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

// static 是 csap-apidoc-ui(文档页面)的构建产物,devtools 是 csap-apidoc-devtools(管理页面)的构建产物,
// 都由 scripts/sync-ui.sh 从 Java 仓库同步,与 Java 版 jar 里的完全相同。
//
//go:embed static devtools
var files embed.FS

// FS 返回文档页面的静态文件(根目录即 static/)。
func FS() fs.FS {
	sub, _ := fs.Sub(files, "static")
	return sub
}

// DevtoolsFS 返回管理页面的静态文件(根目录即 devtools/,页面为 index.html)。
func DevtoolsFS() fs.FS {
	sub, _ := fs.Sub(files, "devtools")
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

// site 是一套内嵌前端:html/js/css 里写死的资源地址在第一次请求时改写一次,之后直接输出。
type site struct {
	root     fs.FS
	replacer *strings.Replacer
	once     sync.Once
	cache    map[string][]byte
}

func newSite(root fs.FS, pairs ...string) *site {
	return &site{root: root, replacer: strings.NewReplacer(pairs...)}
}

func (s *site) load() {
	s.cache = map[string][]byte{}
	_ = fs.WalkDir(s.root, ".", func(p string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		switch path.Ext(p) {
		case ".html", ".js", ".css":
		default:
			return nil
		}
		b, err := fs.ReadFile(s.root, p)
		if err != nil {
			return err
		}
		s.cache[p] = []byte(s.replacer.Replace(string(b)))
		return nil
	})
}

// serve 输出 name 对应的文件。找不到时回 404 并返回 false。
func (s *site) serve(w http.ResponseWriter, r *http.Request, name string) bool {
	s.once.Do(s.load)
	name = path.Clean(name)
	if strings.HasPrefix(name, "..") || strings.HasPrefix(name, "/") {
		http.NotFound(w, r)
		return false
	}
	b, ok := s.cache[name]
	if !ok {
		var err error
		b, err = fs.ReadFile(s.root, name)
		if err != nil {
			w.Header().Del("Cache-Control")
			http.NotFound(w, r)
			return false
		}
	}
	if ct, ok := contentTypes[path.Ext(name)]; ok {
		w.Header().Set("Content-Type", ct)
	}
	if r.Method == http.MethodHead {
		return true
	}
	_, _ = w.Write(b)
	return true
}

func normPrefix(prefix string) string {
	return "/" + strings.Trim(prefix, "/")
}

// Handler 提供文档页面:
//
//	/csap-api.html             页面入口
//	/favicon.ico               图标
//	<prefix>/assets/...        js、css、图片
//
// 构建产物里写死了 /assets/ 开头的地址(Java 版放在根路径下),这里在输出时改写成 <prefix>/assets/,
// 避免和业务自己的 /assets/ 冲突。
func Handler(prefix string) http.Handler {
	prefix = normPrefix(prefix)
	var pairs []string
	for _, q := range []string{`"`, `'`, `(`} {
		pairs = append(pairs, q+"/assets/", q+prefix+"/assets/")
	}
	s := newSite(FS(), pairs...)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var name string
		switch p := r.URL.Path; {
		case p == "/csap-api.html":
			name = "csap-api.html"
			w.Header().Set("Cache-Control", "no-cache")
		case p == "/favicon.ico":
			name = "favicon.ico"
		case strings.HasPrefix(p, prefix+"/assets/") && strings.HasPrefix(path.Clean(p), prefix+"/assets/"):
			name = strings.TrimPrefix(p, prefix+"/")
			// 文件名带内容哈希,可以长期缓存
			w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
		default:
			http.NotFound(w, r)
			return
		}
		s.serve(w, r, name)
	})
}

// DevtoolsPath 是管理页面的入口,与 Java 版相同。页面用 React Router,basename 写死为 /devtools-ui,
// 所以 /devtools-ui 下的所有路径都输出同一个页面。
const DevtoolsPath = "/devtools-ui"

// DevtoolsHandler 提供管理页面:
//
//	/devtools-ui、/devtools-ui/...   页面入口(前端路由)
//	<prefix>/assets2/...             js、css、图片
//
// Java 版的资源在 /assets2/ 下,这里改写成 <prefix>/assets2/。构建产物里有两种写法:
// html 和图片地址是 "/assets2/x",vite 的按需加载是 "assets2/x"(运行时再拼上 "/")。
func DevtoolsHandler(prefix string) http.Handler {
	prefix = normPrefix(prefix)
	s := newSite(DevtoolsFS(),
		`"/assets2/`, `"`+prefix+`/assets2/`,
		`"assets2/`, `"`+strings.TrimPrefix(prefix, "/")+`/assets2/`,
	)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch p := r.URL.Path; {
		case p == DevtoolsPath || strings.HasPrefix(p, DevtoolsPath+"/"):
			w.Header().Set("Cache-Control", "no-cache")
			s.serve(w, r, "index.html")
		case strings.HasPrefix(p, prefix+"/assets2/") && strings.HasPrefix(path.Clean(p), prefix+"/assets2/"):
			w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
			s.serve(w, r, strings.TrimPrefix(p, prefix+"/"))
		default:
			http.NotFound(w, r)
		}
	})
}

// referencedAssets 返回 html/js/css 里引用的 <prefix>/<dir>/ 下的资源路径(相对 FS 根),测试用。
func referencedAssets(b []byte, prefix, dir string) []string {
	var out []string
	marker := []byte(strings.TrimPrefix(prefix, "/") + "/" + dir + "/")
	for {
		i := bytes.Index(b, marker)
		if i < 0 {
			return out
		}
		b = b[i+len(marker)-len(dir)-1:]
		end := bytes.IndexAny(b, "\"')` ")
		if end < 0 {
			return out
		}
		out = append(out, string(b[:end]))
		b = b[end:]
	}
}
