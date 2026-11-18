package devtools

import (
	"crypto/subtle"
	"errors"
	"net"
	"net/http"
	"strings"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/ui"
)

// Auth 是后台管理的鉴权回调,返回 true 表示放行。页面和接口都经过它。
type Auth func(r *http.Request) bool

// ErrNoAuth 表示没有配置鉴权回调。后台可以改线上的返回字段和校验规则,不允许不设鉴权就开启。
var ErrNoAuth = errors.New("apidoc/devtools: Options.Auth is required (use devtools.AllowAll only for local development)")

// ErrNoStore 表示没有可写的规则来源。
var ErrNoStore = errors.New("apidoc/devtools: no writable strategy (strategy.Saver) configured")

// Options 是 New 的参数。
type Options struct {
	// Auth 鉴权回调,必填。不放行时页面和接口都回 401。
	// 可以用 BasicAuth、Token、AllowLocal 组合,也可以接自己的登录态;AllowAll 只用于本机开发。
	Auth Auth
	// Realm 不为空时,401 带上 WWW-Authenticate: Basic realm="...",浏览器会弹出用户名密码框(配合 BasicAuth)。
	Realm string
	// Store 是保存规则的地方,默认取 Doc 的 Strategies 里最后一个可写的(strategy.Saver,优先级最高)。
	// 实现了 strategy.Historian 时提供修改记录和回滚(YAML、SQL、Memory 都实现了)。
	Store strategy.Saver
	// User 返回修改人,写进修改记录。默认取 Basic Auth 用户名,没有时取客户端 IP。
	User func(r *http.Request) string
	// DisableUI 为 true 时不提供 /devtools-ui 页面,只提供接口。
	DisableUI bool
	// MaxBodyBytes 是请求体上限,默认 10MB。
	MaxBodyBytes int64
}

// Devtools 是后台管理:接口与 Java csap-apidoc-devtools 兼容(/csap/yaml/*、/devtools/*),
// 页面 /devtools-ui 直接内嵌 Java 版的前端。改规则后立即重新生成文档,返回过滤和请求校验下一个请求就按新规则执行。
//
// 默认不开启:要显式调用 New 并挂到路由上,且必须配置鉴权。建议只在开发、测试环境或内网开启。
type Devtools struct {
	doc  *apidoc.Doc
	opts Options
	ui   http.Handler
}

// New 创建后台管理。Auth 为 nil 时返回 ErrNoAuth;找不到可写的规则来源时返回 ErrNoStore。
func New(doc *apidoc.Doc, opts Options) (*Devtools, error) {
	if opts.Auth == nil {
		return nil, ErrNoAuth
	}
	if opts.Store == nil {
		ss := doc.Options().Strategies
		for i := len(ss) - 1; i >= 0; i-- {
			if s, ok := ss[i].(strategy.Saver); ok {
				if ro, ok := ss[i].(interface{ ReadOnly() bool }); ok && ro.ReadOnly() {
					continue
				}
				opts.Store = s
				break
			}
		}
	}
	if opts.Store == nil {
		return nil, ErrNoStore
	}
	if opts.User == nil {
		opts.User = defaultUser
	}
	if opts.MaxBodyBytes <= 0 {
		opts.MaxBodyBytes = 10 << 20
	}
	d := &Devtools{doc: doc, opts: opts}
	if !opts.DisableUI {
		d.ui = ui.DevtoolsHandler(doc.Options().UIPrefix)
	}
	return d, nil
}

// MustNew 同 New,出错时 panic。
func MustNew(doc *apidoc.Doc, opts Options) *Devtools {
	d, err := New(doc, opts)
	if err != nil {
		panic(err)
	}
	return d
}

// Store 返回保存规则的策略。
func (d *Devtools) Store() strategy.Saver { return d.opts.Store }

// Route 是一条需要挂到框架路由上的路径(gin 风格,*x 为通配)。
type Route struct {
	Method string
	Path   string
}

// Routes 返回后台管理的全部路由,适配器按它注册 Handler。
func (d *Devtools) Routes() []Route {
	rs := []Route{
		{http.MethodGet, "/csap/yaml/*action"},
		{http.MethodPost, "/csap/yaml/*action"},
		{http.MethodDelete, "/csap/yaml/*action"},
		{http.MethodGet, "/devtools/*action"},
		{http.MethodPost, "/devtools/*action"},
	}
	if d.ui != nil {
		prefix := d.doc.Options().UIPrefix
		for _, m := range []string{http.MethodGet, http.MethodHead} {
			rs = append(rs,
				Route{m, ui.DevtoolsPath},
				Route{m, ui.DevtoolsPath + "/*path"},
				Route{m, prefix + "/assets2/*filepath"},
			)
		}
	}
	return rs
}

// Patterns 返回挂到 http.ServeMux 上用的路径前缀:
//
//	for _, p := range dt.Patterns() { mux.Handle(p, dt) }
func (d *Devtools) Patterns() []string {
	ps := []string{"/csap/yaml/", "/devtools/"}
	if d.ui != nil {
		ps = append(ps, ui.DevtoolsPath, ui.DevtoolsPath+"/", d.doc.Options().UIPrefix+"/assets2/")
	}
	return ps
}

// ServeHTTP 处理后台管理的页面和接口。
func (d *Devtools) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !d.opts.Auth(r) {
		if d.opts.Realm != "" {
			w.Header().Set("WWW-Authenticate", `Basic realm="`+strings.ReplaceAll(d.opts.Realm, `"`, "")+`", charset="UTF-8"`)
		}
		writeError(w, http.StatusUnauthorized, "401", "未登录或没有后台管理权限")
		return
	}
	p := r.URL.Path
	switch {
	case strings.HasPrefix(p, "/csap/yaml/") || strings.HasPrefix(p, "/devtools/"):
		d.serveAPI(w, r)
	case d.ui != nil:
		d.ui.ServeHTTP(w, r)
	default:
		http.NotFound(w, r)
	}
}

// AllowAll 放行所有请求。只用于本机开发,不要在能被外部访问的环境使用。
func AllowAll(*http.Request) bool { return true }

// AllowLocal 只放行来自本机(回环地址)的请求。注意:放在反向代理后面时,代理到本服务的请求也来自本机。
func AllowLocal(r *http.Request) bool {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

// BasicAuth 校验 HTTP Basic 用户名密码。配合 Options.Realm,浏览器打开页面时会弹框输入。
func BasicAuth(user, password string) Auth {
	return func(r *http.Request) bool {
		u, p, ok := r.BasicAuth()
		return ok && subtle.ConstantTimeCompare([]byte(u), []byte(user)) == 1 &&
			subtle.ConstantTimeCompare([]byte(p), []byte(password)) == 1
	}
}

// Token 校验请求头 header 的值等于 token(如网关注入的内部令牌)。
func Token(header, token string) Auth {
	return func(r *http.Request) bool {
		v := r.Header.Get(header)
		return v != "" && subtle.ConstantTimeCompare([]byte(v), []byte(token)) == 1
	}
}

// Any 任意一个放行即放行。
func Any(auths ...Auth) Auth {
	return func(r *http.Request) bool {
		for _, a := range auths {
			if a != nil && a(r) {
				return true
			}
		}
		return false
	}
}

func defaultUser(r *http.Request) string {
	if u, _, ok := r.BasicAuth(); ok && u != "" {
		return u
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}
