package nethttp

import (
	"bytes"
	"context"
	"log/slog"
	"mime"
	"net/http"
	"path"
	"runtime"
	"strings"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

// Mux 包装标准库的 http.ServeMux(Go 1.22+ 路由模式,如 "GET /users/{id}"):
// 注册路由的同时把文档声明登记到 Doc,并给路由套上中间件(返回过滤、请求校验)。
//
//	doc := apidoc.New(apidoc.Options{...})
//	mux := http.NewServeMux()
//	m := nethttpadapter.Mount(mux, doc)
//	m.Use(nethttpadapter.ResponseFilter(doc), nethttpadapter.RequestValidator(v))
//	m.GET("/users/{id}", apidoc.Op("UserController", "get").Req(GetUserReq{}).Resp(User{}), getUser)
//
// ServeMux 不能列出已注册的路由,所以只有通过 Mux 注册的路由会出现在文档里。
type Mux struct {
	mux    *http.ServeMux
	doc    *apidoc.Doc
	prefix string
	mws    []func(http.Handler) http.Handler
}

// Mount 在 mux 上挂文档接口(/csap/apidoc*、/csap/openapi.json、/csap/openapi/postman、/csap-api.html 及静态资源),
// 返回根路由的包装。
func Mount(mux *http.ServeMux, doc *apidoc.Doc) *Mux {
	h := doc.Handler()
	seen := map[string]bool{}
	for _, p := range doc.Paths() {
		pat := http.MethodGet + " " + muxPath(p)
		if !seen[pat] {
			seen[pat] = true
			mux.Handle(pat, h) // GET 模式同时匹配 HEAD
		}
	}
	return &Mux{mux: mux, doc: doc}
}

// MountDevtools 在 mux 上挂后台管理(/csap/yaml/*、/devtools/*、/devtools-ui 及其静态资源)。
// dt 由 devtools.New 创建,必须配置鉴权;建议只在开发、测试环境或内网挂载。
func MountDevtools(mux *http.ServeMux, dt *devtools.Devtools) {
	seen := map[string]bool{}
	for _, rt := range dt.Routes() {
		if rt.Method == http.MethodHead {
			continue // GET 模式已包含 HEAD
		}
		pat := rt.Method + " " + muxPath(rt.Path)
		if !seen[pat] {
			seen[pat] = true
			mux.Handle(pat, dt)
		}
	}
}

// muxPath 把 gin 风格的路径(/a/*rest、/a/:id)转成 ServeMux 模式(/a/、/a/{id})。
func muxPath(p string) string {
	if i := strings.Index(p, "/*"); i >= 0 {
		return p[:i+1]
	}
	segs := strings.Split(p, "/")
	for i, s := range segs {
		if strings.HasPrefix(s, ":") {
			segs[i] = "{" + s[1:] + "}"
		}
	}
	return strings.Join(segs, "/")
}

// ServeMux 返回底层的 http.ServeMux。
func (m *Mux) ServeMux() *http.ServeMux { return m.mux }

// Use 添加中间件,作用于之后通过本 Mux(及其 Group)注册的路由。
// 中间件里可以用 Route(r) 取到注册时的路由路径。
func (m *Mux) Use(mws ...func(http.Handler) http.Handler) *Mux {
	m.mws = append(m.mws, mws...)
	return m
}

// Group 返回带公共路径前缀的子路由,继承当前的中间件。
func (m *Mux) Group(prefix string, mws ...func(http.Handler) http.Handler) *Mux {
	all := append(append([]func(http.Handler) http.Handler(nil), m.mws...), mws...)
	return &Mux{mux: m.mux, doc: m.doc, prefix: joinPaths(m.prefix, prefix), mws: all}
}

// Handle 注册路由并登记文档。pattern 与 ServeMux 相同:"[METHOD ][HOST]/path",
// 路径相对于 Group 的前缀;不写方法时文档里按 GET 显示。op 可以为 nil。
// 分组名、接口名留空时按 handler 的函数名推断。
func (m *Mux) Handle(pattern string, op *apidoc.Operation, h http.Handler) *Mux {
	method, host, p := splitPattern(pattern)
	return m.handle(method, host, p, op, h, handlerName(h))
}

// HandleFunc 同 Handle。
func (m *Mux) HandleFunc(pattern string, op *apidoc.Operation, h func(http.ResponseWriter, *http.Request)) *Mux {
	method, host, p := splitPattern(pattern)
	return m.handle(method, host, p, op, http.HandlerFunc(h), funcName(h))
}

func (m *Mux) handle(method, host, p string, op *apidoc.Operation, h http.Handler, name string) *Mux {
	full := joinPaths(m.prefix, p)
	route := strings.TrimSuffix(full, "{$}") // 文档和规则里不带 ServeMux 的 {$}(精确匹配标记)
	var wrapped http.Handler = h
	for i := len(m.mws) - 1; i >= 0; i-- {
		wrapped = m.mws[i](wrapped)
	}
	inner := wrapped
	wrapped = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		inner.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), routeKey{}, route)))
	})
	muxPattern := host + full
	if method != "" {
		muxPattern = method + " " + muxPattern
	}
	m.mux.Handle(muxPattern, wrapped)
	docMethod := method
	if docMethod == "" {
		docMethod = http.MethodGet
	}
	m.doc.Register(docMethod, route, op, name)
	return m
}

func (m *Mux) GET(p string, op *apidoc.Operation, h http.HandlerFunc) *Mux {
	return m.handle(http.MethodGet, "", p, op, h, funcName(h))
}

func (m *Mux) POST(p string, op *apidoc.Operation, h http.HandlerFunc) *Mux {
	return m.handle(http.MethodPost, "", p, op, h, funcName(h))
}

func (m *Mux) PUT(p string, op *apidoc.Operation, h http.HandlerFunc) *Mux {
	return m.handle(http.MethodPut, "", p, op, h, funcName(h))
}

func (m *Mux) DELETE(p string, op *apidoc.Operation, h http.HandlerFunc) *Mux {
	return m.handle(http.MethodDelete, "", p, op, h, funcName(h))
}

func (m *Mux) PATCH(p string, op *apidoc.Operation, h http.HandlerFunc) *Mux {
	return m.handle(http.MethodPatch, "", p, op, h, funcName(h))
}

// splitPattern 拆开 ServeMux 模式 "[METHOD ][HOST]/path"。
func splitPattern(pattern string) (method, host, p string) {
	pattern = strings.TrimSpace(pattern)
	if i := strings.IndexAny(pattern, " \t"); i >= 0 {
		method, pattern = pattern[:i], strings.TrimLeft(pattern[i+1:], " \t")
	}
	if i := strings.Index(pattern, "/"); i > 0 {
		host, pattern = pattern[:i], pattern[i:]
	}
	return method, host, pattern
}

func joinPaths(base, rel string) string {
	if base == "" {
		if rel == "" {
			return "/"
		}
		return rel
	}
	if rel == "" || rel == "/" {
		if strings.HasSuffix(rel, "/") && !strings.HasSuffix(base, "/") {
			return base + "/"
		}
		return base
	}
	out := path.Join(base, rel)
	if strings.HasSuffix(rel, "/") && !strings.HasSuffix(out, "/") {
		out += "/"
	}
	return out
}

func funcName(f any) string {
	if f == nil {
		return ""
	}
	pc := reflectPointer(f)
	if pc == 0 {
		return ""
	}
	if fn := runtime.FuncForPC(pc); fn != nil {
		return fn.Name()
	}
	return ""
}

func handlerName(h http.Handler) string {
	if f, ok := h.(http.HandlerFunc); ok {
		return funcName(f)
	}
	return ""
}

type routeKey struct{}

// Route 返回请求命中的路由路径(注册时的写法,如 /api/users/{id}),不是通过 Mux 注册的路由返回空串。
func Route(r *http.Request) string {
	s, _ := r.Context().Value(routeKey{}).(string)
	return s
}

// ResponseFilter 返回字段过滤中间件:接口配置了返回规则时,按白名单裁剪 JSON 返回,
// 代码里返回了、但规则没放行的字段不输出。没有规则的接口不受影响,也不缓冲。规则热更新后下一个请求即生效。
// 默认只裁剪 2xx 响应,4xx/5xx 的错误体原样输出;可用 apidoc.Options.ShouldFilter 自定义,
// 用 apidoc.Options.FilterEndpoints 按接口关闭。用 Mux.Use 注册(需要路由信息)。
func ResponseFilter(doc *apidoc.Doc) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ep := doc.Snapshot().Endpoint(r.Method, Route(r))
			if ep == nil && r.Method == http.MethodHead {
				ep = doc.Snapshot().Endpoint(http.MethodGet, Route(r))
			}
			if ep == nil || ep.ResponseMask == nil {
				next.ServeHTTP(w, r)
				return
			}
			bw := &bufferWriter{ResponseWriter: w, status: http.StatusOK}
			next.ServeHTTP(bw, r)
			mask := ep.ResponseMask
			if !doc.ShouldFilter(r, bw.status) {
				mask = nil
			}
			bw.flushTo(mask)
		})
	}
}

// RequestValidator 请求参数校验中间件,模式(关闭 / 观察 / 拦截)见 validate.Options。
// 路径参数用 r.PathValue 取。用 Mux.Use 注册(需要路由信息)。
func RequestValidator(v *validate.Validator) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if v.Handle(w, r, Route(r), r.PathValue) {
				next.ServeHTTP(w, r)
			}
		})
	}
}

// bufferWriter 缓冲业务 handler 的输出,结束后统一裁剪再写出。
type bufferWriter struct {
	http.ResponseWriter
	buf         bytes.Buffer
	status      int
	wroteHeader bool
}

func (w *bufferWriter) WriteHeader(code int) {
	if !w.wroteHeader {
		w.status = code
		w.wroteHeader = true
	}
}

func (w *bufferWriter) Write(b []byte) (int, error) {
	w.wroteHeader = true
	return w.buf.Write(b)
}

// Flush 在缓冲模式下不做事:流式输出(SSE 等)的接口不应该配置返回过滤。
func (w *bufferWriter) Flush() {}

// Unwrap 供 http.ResponseController 使用。
func (w *bufferWriter) Unwrap() http.ResponseWriter { return w.ResponseWriter }

// flushTo 写出缓冲的响应,mask 为 nil 时原样写出。
func (w *bufferWriter) flushTo(mask *filter.Node) {
	out := w.buf.Bytes()
	if mask != nil {
		ct, _, _ := mime.ParseMediaType(w.Header().Get("Content-Type"))
		if ct == "" && len(out) > 0 {
			ct, _, _ = mime.ParseMediaType(http.DetectContentType(out))
		}
		if ct == "application/json" || strings.HasSuffix(ct, "+json") || (ct == "text/plain" && looksJSON(out)) {
			if pruned, err := filter.Prune(out, mask); err == nil {
				out = pruned
			} else {
				slog.Warn("apidoc: response is not valid JSON, not filtered", "err", err)
			}
		}
	}
	w.Header().Del("Content-Length")
	w.ResponseWriter.WriteHeader(w.status)
	if len(out) > 0 {
		_, _ = w.ResponseWriter.Write(out)
	}
}

// looksJSON:handler 没设 Content-Type 时,DetectContentType 对 JSON 只会给出 text/plain。
func looksJSON(b []byte) bool {
	b = bytes.TrimSpace(b)
	return len(b) > 0 && (b[0] == '{' || b[0] == '[')
}
