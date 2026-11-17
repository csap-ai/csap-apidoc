package gin

import (
	"net/http"
	"path"
	"strings"

	"github.com/gin-gonic/gin"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
)

// Router 包装 gin 的路由组:注册路由的同时把文档声明登记到 Doc。
//
//	doc := apidoc.New(apidoc.Options{})
//	r := gin.New()
//	g := ginadapter.Mount(r, doc)
//	g.GET("/users/:id", apidoc.Op("UserController", "get").Req(GetUserReq{}).Resp(User{}), getUser)
//
// 直接用 r.GET 注册、没有声明的路由,也会出现在文档里(只有方法和路径)。
type Router struct {
	rg     *gin.RouterGroup
	doc    *apidoc.Doc
	engine *gin.Engine
}

// Mount 在 engine 上挂文档接口(/csap/apidoc*、/csap/openapi.json、/csap-api.html 及静态资源),
// 并让 Doc 从 engine 收集未声明的路由。返回根路由组的包装。
func Mount(engine *gin.Engine, doc *apidoc.Doc) *Router {
	h := gin.WrapH(doc.Handler())
	for _, p := range doc.Paths() {
		engine.GET(p, h)
		engine.HEAD(p, h)
	}
	doc.SetRouteSource(func() []apidoc.Route {
		var out []apidoc.Route
		for _, ri := range engine.Routes() {
			if ri.Method == http.MethodHead {
				continue
			}
			out = append(out, apidoc.Route{Method: ri.Method, Path: ri.Path, Handler: ri.Handler})
		}
		return out
	})
	return &Router{rg: &engine.RouterGroup, doc: doc, engine: engine}
}

// Wrap 包装一个已有的路由组(例如带鉴权中间件的 /api 组)。
func (r *Router) Wrap(rg *gin.RouterGroup) *Router {
	return &Router{rg: rg, doc: r.doc, engine: r.engine}
}

// Group 新建子路由组,用法同 gin.RouterGroup.Group。
func (r *Router) Group(relativePath string, handlers ...gin.HandlerFunc) *Router {
	return &Router{rg: r.rg.Group(relativePath, handlers...), doc: r.doc, engine: r.engine}
}

// Use 添加中间件。
func (r *Router) Use(handlers ...gin.HandlerFunc) *Router {
	r.rg.Use(handlers...)
	return r
}

// RouterGroup 返回底层的 gin 路由组。
func (r *Router) RouterGroup() *gin.RouterGroup { return r.rg }

// Handle 注册路由并登记文档。op 可以为 nil。handlers 的最后一个是业务 handler,
// 分组名、接口名留空时按它的函数名推断。
func (r *Router) Handle(method, relativePath string, op *apidoc.Operation, handlers ...gin.HandlerFunc) *Router {
	r.rg.Handle(method, relativePath, handlers...)
	full := joinPaths(r.rg.BasePath(), relativePath)
	name := ""
	if len(handlers) > 0 {
		name = nameOfFunction(handlers[len(handlers)-1])
	}
	r.doc.Register(method, full, op, name)
	return r
}

func (r *Router) GET(p string, op *apidoc.Operation, h ...gin.HandlerFunc) *Router {
	return r.Handle(http.MethodGet, p, op, h...)
}

func (r *Router) POST(p string, op *apidoc.Operation, h ...gin.HandlerFunc) *Router {
	return r.Handle(http.MethodPost, p, op, h...)
}

func (r *Router) PUT(p string, op *apidoc.Operation, h ...gin.HandlerFunc) *Router {
	return r.Handle(http.MethodPut, p, op, h...)
}

func (r *Router) DELETE(p string, op *apidoc.Operation, h ...gin.HandlerFunc) *Router {
	return r.Handle(http.MethodDelete, p, op, h...)
}

func (r *Router) PATCH(p string, op *apidoc.Operation, h ...gin.HandlerFunc) *Router {
	return r.Handle(http.MethodPatch, p, op, h...)
}

func joinPaths(abs, rel string) string {
	if rel == "" {
		return abs
	}
	out := path.Join(abs, rel)
	if strings.HasSuffix(rel, "/") && !strings.HasSuffix(out, "/") {
		out += "/"
	}
	return out
}
