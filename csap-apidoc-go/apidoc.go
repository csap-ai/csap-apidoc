package apidoc

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"reflect"
	"sort"
	"strings"
	"sync"
	"sync/atomic"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/mock"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// Wrapper 描述统一外层包装,如 {code,message,data}。注册一次,所有接口的返回都套在里面,
// 规则路径以 data. 开头,与 Java 写法一致。
type Wrapper struct {
	Type        any    // 包装结构体的零值,如 Result{}
	DataField   string // 业务数据字段的 json 名,默认 "data"
	Title       string // 文档里的显示名,默认取类型名
	Description string
}

// Options 是 New 的参数。
type Options struct {
	// Info 是文档首页信息,nil 时用与 Java 默认值相同的配置。
	Info *model.ApiInfo
	// Strategies 是字段规则来源,按顺序合并,后面的覆盖前面的。结构体标签总是最先生效。
	Strategies []strategy.Strategy
	// Wrapper 是统一外层包装,可为 nil。
	Wrapper *Wrapper
	// MethodHeaders 是每个接口默认的请求头,nil 时与 Java 默认一样只有 Content-Type。
	MethodHeaders []model.MethodHeader
	// DocAuth 为文档接口加鉴权,返回 false 时回 401。nil 表示不鉴权(与 Java 一致)。
	DocAuth func(r *http.Request) bool
	// UIPrefix 是文档页面静态资源的路径前缀,默认 "/csap-ui"。
	// Java 版把资源放在 /assets/ 下,容易和业务冲突,Go 版加了前缀。
	UIPrefix string
	// DisableUI 为 true 时不提供 /csap-api.html 页面。
	DisableUI bool
	// FilterMode 是返回字段过滤的模式,默认 filter.ModePath(路径白名单)。
	// 从 Java 迁移、要求返回与 Java 逐字节一致时用 filter.ModeCsap。
	FilterMode filter.Mode
	// FilterEndpoints 按接口(规则的键 group.name)覆盖 FilterMode,值为 filter.ModePath / ModeCsap / ModeOff。
	// 对应 Java csap.apidoc.runtime.filter.endpoints。
	FilterEndpoints map[string]filter.Mode
	// ShouldFilter 决定一次响应是否按规则裁剪,在业务 handler 写完响应后调用,status 是最终的状态码。
	// nil 时只裁剪 2xx 响应:4xx/5xx 的错误体(参数错误、异常处理器写的失败体等)原样输出,
	// 与 Java 只过滤控制器正常返回的对象一致。
	// 业务失败也用 200 返回时,可以在这里按请求或状态码进一步排除。
	ShouldFilter func(r *http.Request, status int) bool
	// ShouldFilterBody 按响应体细判是否裁剪,用于"业务失败也返回 HTTP 200"的接口:
	// 响应已缓冲、裁剪之前调用,返回 false 时这次响应原样输出。nil 时不做这层判断,行为与 v0.1.1 相同。
	// 先后顺序:先 ShouldFilter(只看请求和状态码,粗筛),返回 true 才会走到 ShouldFilterBody(再看响应体);
	// ShouldFilter 返回 false 时不会调用它。body 是业务 handler 写出的原始字节,只读,不要修改。
	// 常用判断见 SuccessField。
	ShouldFilterBody func(r *http.Request, status int, body []byte) bool
	// Mock 是 Mock 服务的配置,默认关闭。开启后 /csap/mock/{原路径} 按文档里的返回结构生成可复现的假数据。
	Mock MockOptions
	// Schema 是 JSON Schema 导入导出的配置。导出(/csap/schema.json、/csap/schema/method)始终开启;导入默认关闭。
	Schema SchemaOptions
	// TypeNames 覆盖结构体类型在文档里的显示名(相当于 Java @ApiModel 的 value),
	// 键为 reflect.TypeOf(T{}),指针类型按其元素类型处理。优先级:TypeNames > 类型的 ApidocName() 方法 > 类型名。
	TypeNames map[reflect.Type]string
	// HeaderHints、Auth 是全局的"在线调试"建议(对应 Java 包上的 @DocGlobalHeader / @DocAuth),
	// 分组(GroupInfo)和接口(Operation.HeaderHint / Auth)可以覆盖。
	HeaderHints []model.GlobalHeaderHint
	Auth        *model.AuthHint
}

// Doc 是运行时文档。并发安全:注册路由、刷新规则、处理请求可以同时发生,
// 请求读的是原子替换的快照,不加锁。
type Doc struct {
	opts Options

	mu      sync.Mutex
	groups  map[string]GroupInfo
	ops     []registered
	source  func() []Route
	rules   strategy.Rules
	loaded  bool
	dirty   bool
	current atomic.Pointer[Snapshot]
}

type registered struct {
	method  string
	path    string
	handler string
	op      *Operation
}

// New 创建文档。
func New(opts Options) *Doc {
	if opts.Info == nil {
		opts.Info = model.DefaultApiInfo()
	}
	if opts.MethodHeaders == nil {
		opts.MethodHeaders = []model.MethodHeader{{
			Description: "上下文类型",
			Example:     "Content-Type=application/json",
			Key:         "Content-Type",
			Required:    true,
			Value:       "application/json",
		}}
	}
	if opts.Wrapper != nil && opts.Wrapper.DataField == "" {
		opts.Wrapper.DataField = "data"
	}
	if opts.UIPrefix == "" {
		opts.UIPrefix = "/csap-ui"
	}
	opts.UIPrefix = "/" + strings.Trim(opts.UIPrefix, "/")
	if len(opts.TypeNames) > 0 {
		names := make(map[reflect.Type]string, len(opts.TypeNames))
		for t, n := range opts.TypeNames {
			if t = deref(t); t != nil {
				names[t] = n
			}
		}
		opts.TypeNames = names
	}
	if opts.Mock.Enabled {
		slog.Warn("csap-apidoc mock is ENABLED, /csap/mock/** returns synthetic data; do not enable in production")
	}
	return &Doc{opts: opts, groups: map[string]GroupInfo{}, dirty: true}
}

// ModelNamer 是可选接口:结构体类型实现它(值或指针接收者均可)时,ApidocName 的返回值作为文档里的显示名,
// 相当于 Java @ApiModel 的 value。会在零值上调用,不要依赖字段内容。
//
//	func (Page[T]) ApidocName() string { return "分页返回信息" }
type ModelNamer interface {
	ApidocName() string
}

// filterMode 返回接口(规则的键)生效的返回过滤模式。
func (d *Doc) filterMode(ruleKey string) filter.Mode {
	if m, ok := d.opts.FilterEndpoints[ruleKey]; ok {
		return m
	}
	return d.opts.FilterMode
}

// ShouldFilter 判断这次响应是否要按规则裁剪:Options.ShouldFilter 不为 nil 时由它决定,
// 否则只裁剪 2xx。适配器的 ResponseFilter 在写出响应前调用。
func (d *Doc) ShouldFilter(r *http.Request, status int) bool {
	if d.opts.ShouldFilter != nil {
		return d.opts.ShouldFilter(r, status)
	}
	return status >= 200 && status < 300
}

// ShouldFilterBody 在 ShouldFilter 返回 true 之后调用,按响应体判断是否裁剪;
// 没有设置 Options.ShouldFilterBody 时恒为 true。适配器的 ResponseFilter 在裁剪前调用。
func (d *Doc) ShouldFilterBody(r *http.Request, status int, body []byte) bool {
	if d.opts.ShouldFilterBody != nil {
		return d.opts.ShouldFilterBody(r, status, body)
	}
	return true
}

// Options 返回创建时的配置(已填充默认值)。
func (d *Doc) Options() Options { return d.opts }

// Group 登记分组信息(标题、说明、排序、公共路径)。
func (d *Doc) Group(g GroupInfo) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.groups[g.Name] = g
	d.dirty = true
}

// Register 登记一条路由。handler 是 handler 函数名,用来在 op 没写分组/接口名时推断,可为空。
// op 为 nil 时只登记方法和路径。适配器在注册路由时调用它。
func (d *Doc) Register(method, path string, op *Operation, handler string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.ops = append(d.ops, registered{method: strings.ToUpper(method), path: path, handler: handler, op: op})
	d.dirty = true
}

// SetRouteSource 设置路由来源。没有通过 Register 声明的路由会从这里补进文档(只有方法和路径)。
// 每次生成快照时调用。
func (d *Doc) SetRouteSource(fn func() []Route) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.source = fn
	d.dirty = true
}

// Refresh 重新从所有策略加载规则并生成快照。对应 Java 的 flush=true。
func (d *Doc) Refresh(ctx context.Context) error {
	rules, err := d.loadRules(ctx)
	d.mu.Lock()
	defer d.mu.Unlock()
	// 部分策略加载失败时:第一次加载用成功的那部分;之后保留上一次的规则,避免规则突然丢失。
	if err == nil || !d.loaded {
		d.rules = rules
	}
	d.loaded = true
	d.current.Store(d.buildLocked())
	d.dirty = false
	return err
}

// Snapshot 返回当前快照。第一次调用、或注册了新路由之后,会先生成。
func (d *Doc) Snapshot() *Snapshot {
	d.mu.Lock()
	needLoad := !d.loaded
	dirty := d.dirty
	d.mu.Unlock()
	if needLoad {
		_ = d.Refresh(context.Background())
		return d.current.Load()
	}
	if dirty {
		d.mu.Lock()
		if d.dirty {
			d.current.Store(d.buildLocked())
			d.dirty = false
		}
		d.mu.Unlock()
	}
	return d.current.Load()
}

// Watch 监听所有实现了 strategy.Watcher 的策略,规则变更时重新加载。阻塞到 ctx 结束。
func (d *Doc) Watch(ctx context.Context) error {
	var wg sync.WaitGroup
	errs := make(chan error, len(d.opts.Strategies))
	for _, s := range d.opts.Strategies {
		w, ok := s.(strategy.Watcher)
		if !ok {
			continue
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			err := w.Watch(ctx, func() { _ = d.Refresh(ctx) })
			if err != nil && !errors.Is(err, context.Canceled) {
				errs <- err
			}
		}()
	}
	wg.Wait()
	close(errs)
	return <-errs
}

func (d *Doc) loadRules(ctx context.Context) (strategy.Rules, error) {
	all := make([]strategy.Rules, 0, len(d.opts.Strategies))
	var errs []error
	for _, s := range d.opts.Strategies {
		r, err := s.Load(ctx)
		if err != nil {
			errs = append(errs, fmt.Errorf("apidoc: load strategy %s: %w", s.Name(), err))
			continue
		}
		all = append(all, r)
	}
	return strategy.Merge(all...), errors.Join(errs...)
}

// Snapshot 是某一时刻的完整文档,生成后不再修改。
type Snapshot struct {
	Doc   *model.Response
	Rules strategy.Rules

	controllers map[string]*model.Controller
	methods     map[string]map[string]*model.Method // controller -> key -> method
	endpoints   map[string]*Endpoint                // "METHOD path" -> endpoint
	defs        map[string]*opDef                   // 规则的键 -> 接口声明(同名取第一个),devtools 生成完整字段用
	types       map[string]reflect.Type             // 类型全名 -> 类型,devtools 按类名取字段用
	doc         *Doc

	mockOnce   sync.Once
	mockRouter *mock.Router

	dataOnce sync.Once
	dataJSON []byte
}

// Endpoint 是快照里一条接口的运行时信息,供过滤、校验中间件按路由查找规则。
type Endpoint struct {
	Key    string // 规则的键 group.name
	Method string
	Path   string // 注册时的原始路径
	Rules  strategy.MethodRules
	// Doc 是该接口的文档(请求参数树用于校验)。
	Doc *model.Method
	// ResponseMask 是返回字段过滤掩码,nil 表示不过滤(该接口没有返回规则、规则都没命中返回字段,或过滤模式为 off)。
	ResponseMask *filter.Node
	// Checks 是请求参数校验项(必填、校验规则、单值参数的类型)。
	Checks []FieldCheck
}

// FieldCheck 是一个请求字段的校验项,由文档构建时从结构体标签和请求规则算出。
type FieldCheck struct {
	In        string   // 取值位置:PATH / QUERY / HEADER / FORM / FORM_DATA / BODY
	Name      string   // 非 BODY 时的参数名
	Path      []string // BODY 时在 JSON 里的路径(经过数组时对每个元素校验)
	Field     string   // 规则路径,如 request.items.name
	FieldName string   // 字段名,错误信息 TIP_1/TIP_2 用
	Remark    string   // 字段说明,错误信息 TIP_3 用
	Required  bool
	DataType  string // 单值参数的类型名(Integer、Long…),非空时校验格式
	Rules     []strategy.ValidateRule
}

// Endpoint 按方法和注册路径查找接口。
func (s *Snapshot) Endpoint(method, path string) *Endpoint {
	return s.endpoints[strings.ToUpper(method)+" "+path]
}

// Controller 按分组名查找。
func (s *Snapshot) Controller(name string) *model.Controller { return s.controllers[name] }

// Method 按分组名和接口 key 查找。
func (s *Snapshot) Method(name, key string) *model.Method {
	if m := s.methods[name]; m != nil {
		return m[key]
	}
	return nil
}

func (s *Snapshot) data() []byte {
	s.dataOnce.Do(func() {
		s.dataJSON, _ = model.MarshalCsap(s.Doc, 1)
	})
	return s.dataJSON
}

func (d *Doc) buildLocked() *Snapshot {
	types := map[string]reflect.Type{}
	b := &builder{doc: d, rules: d.rules, types: types}
	resp := &model.Response{
		ApiInfo:       d.opts.Info,
		ApiList:       []*model.Controller{},
		DocEnumList:   []model.Enum{},
		GlobalApiList: []*model.Controller{},
		Groups:        []string{model.Default},
		Versions:      []string{model.Default},
	}
	snap := &Snapshot{
		Doc:         resp,
		Rules:       d.rules,
		controllers: map[string]*model.Controller{},
		methods:     map[string]map[string]*model.Method{},
		endpoints:   map[string]*Endpoint{},
		defs:        map[string]*opDef{},
		types:       types,
		doc:         d,
	}

	all := append([]registered(nil), d.ops...)
	seen := map[string]bool{}
	for _, r := range all {
		seen[r.method+" "+r.path] = true
	}
	if d.source != nil {
		for _, rt := range d.source() {
			m := strings.ToUpper(rt.Method)
			if seen[m+" "+rt.Path] || isDocPath(rt.Path, d.opts.UIPrefix) {
				continue
			}
			seen[m+" "+rt.Path] = true
			all = append(all, registered{method: m, path: rt.Path, handler: rt.Handler})
		}
	}

	type ctrlState struct {
		c    *model.Controller
		info GroupInfo
		keys map[string]int
	}
	ctrls := map[string]*ctrlState{}
	for _, r := range all {
		if isDocPath(r.path, d.opts.UIPrefix) {
			continue
		}
		op := r.op
		if op == nil {
			op = &Operation{}
		}
		group, name := op.group, op.name
		if group == "" || name == "" {
			g, n := inferName(r.handler, r.method, r.path)
			if group == "" {
				group = g
			}
			if name == "" {
				name = n
			}
		}
		info, ok := d.groups[group]
		if !ok {
			info = GroupInfo{Name: group}
		}
		if info.Hidden || op.hidden {
			continue
		}
		cs := ctrls[group]
		if cs == nil {
			cs = &ctrlState{c: newController(info), info: info, keys: map[string]int{}}
			ctrls[group] = cs
			snap.methods[group] = map[string]*model.Method{}
		}
		key := strategy.Key(group, name)
		ruleKey := key
		if n := cs.keys[key]; n > 0 {
			key = fmt.Sprintf("%s#%d", key, n+1)
		}
		cs.keys[ruleKey]++

		mr := d.rules[ruleKey]
		m, mask, checks := b.method(cs.info, op, r.method, r.path, name, key, mr)
		cs.c.MethodList = append(cs.c.MethodList, m)
		snap.methods[group][key] = m
		if snap.defs[ruleKey] == nil {
			snap.defs[ruleKey] = &opDef{info: cs.info, op: op, method: r.method, path: r.path, name: name, key: key}
		}
		snap.endpoints[r.method+" "+r.path] = &Endpoint{Key: ruleKey, Method: r.method, Path: r.path, Rules: mr, Doc: m, ResponseMask: mask, Checks: checks}
	}
	for name, cs := range ctrls {
		resp.ApiList = append(resp.ApiList, cs.c)
		snap.controllers[name] = cs.c
	}
	sort.SliceStable(resp.ApiList, func(i, j int) bool {
		a, b := resp.ApiList[i], resp.ApiList[j]
		if a.Position != b.Position {
			return a.Position < b.Position
		}
		return a.Name < b.Name
	})
	return snap
}

// opDef 是一条接口的声明,用来在不套规则的情况下重新生成文档。
type opDef struct {
	info   GroupInfo
	op     *Operation
	method string
	path   string
	name   string
	key    string
}

// FullMethod 返回接口(规则的键 group.name)不套任何字段规则时的文档:请求参数和返回字段都是完整的。
// devtools 用它列出"可选字段"。找不到接口时返回 nil。
func (s *Snapshot) FullMethod(ruleKey string) *model.Method {
	def := s.defs[ruleKey]
	if def == nil {
		return nil
	}
	b := &builder{doc: s.doc}
	m, _, _ := b.method(def.info, def.op, def.method, def.path, def.name, def.key, strategy.MethodRules{})
	return m
}

// TypeFields 返回文档里出现过的结构体类型(按全名,如 github.com/x/app.User)的完整字段树,不套规则。
// 找不到时返回 nil。
func (s *Snapshot) TypeFields(name string) []*model.Parameter {
	t := s.types[name]
	if t == nil {
		return nil
	}
	b := &builder{doc: s.doc}
	mc := &methodCtx{keyCount: map[string]int{}, mapPaths: map[string]bool{}, docHidden: map[*model.Parameter]bool{}}
	return b.structParams(t, "", false, mc, []reflect.Type{t})
}

// RuleKeys 返回快照里所有接口规则的键(group.name),按字母序。
func (s *Snapshot) RuleKeys() []string {
	out := make([]string, 0, len(s.defs))
	for k := range s.defs {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

func newController(info GroupInfo) *model.Controller {
	simple := simpleName(info.Name)
	title := info.Title
	if title == "" {
		title = simple
	}
	tags := info.Tags
	if tags == nil {
		tags = []string{}
	}
	var path []string
	if info.Path != "" {
		path = []string{info.Path}
	}
	return &model.Controller{
		Description:  info.Description,
		Group:        []string{model.Default},
		HiddenMethod: []string{},
		MethodList:   []*model.Method{},
		Name:         info.Name,
		Path:         path,
		Position:     info.Position,
		Protocols:    "HTTP",
		Search:       uniq(info.Description, title),
		ShowMethod:   []string{},
		SimpleName:   simple,
		Status:       model.StatusDefault,
		Tags:         tags,
		Value:        title,
		Version:      []string{model.Default},
	}
}

func isDocPath(p, uiPrefix string) bool {
	return p == "/csap" || strings.HasPrefix(p, "/csap/") || p == "/csap-api.html" ||
		p == "/favicon.ico" || p == uiPrefix || strings.HasPrefix(p, uiPrefix+"/") ||
		p == "/devtools-ui" || strings.HasPrefix(p, "/devtools-ui/") || strings.HasPrefix(p, "/devtools/")
}

// simpleName 取全名最后一段:com.example.UserController -> UserController,
// github.com/x/app/user -> user。
func simpleName(full string) string {
	s := full
	if i := strings.LastIndexAny(s, "./"); i >= 0 {
		s = s[i+1:]
	}
	if s == "" {
		return full
	}
	return s
}

// inferName 从 handler 函数名推断分组和接口名:
//
//	github.com/x/app/user.(*Controller).Get-fm -> github.com/x/app/user.Controller, Get
//	github.com/x/app/user.List                 -> github.com/x/app/user, List
//	匿名函数 main.main.func1                    -> main, get_users_id(由方法和路径生成)
func inferName(handler, method, path string) (group, name string) {
	h := strings.TrimSuffix(handler, "-fm")
	if h == "" {
		return "default", routeName(method, path)
	}
	slash := strings.LastIndex(h, "/")
	dot := strings.Index(h[slash+1:], ".")
	if dot < 0 {
		return h, routeName(method, path)
	}
	pkg := h[:slash+1+dot]
	rest := h[slash+1+dot+1:]
	// 闭包:F.func1、(*T).M.func1、T.M.func2.1
	anonymous := false
	if i := strings.Index(rest, ".func"); i >= 0 {
		rest, anonymous = rest[:i], true
	}
	recv, fn := "", rest
	if strings.HasPrefix(rest, "(") {
		// (*T).M
		if end := strings.Index(rest, ")."); end > 0 {
			recv, fn = strings.TrimPrefix(rest[1:end], "*"), rest[end+2:]
		}
	} else if i := strings.Index(rest, "."); i >= 0 {
		// T.M(值接收者的方法值)
		recv, fn = rest[:i], rest[i+1:]
	}
	group = pkg
	if recv != "" {
		group = pkg + "." + recv
	}
	if anonymous || fn == "" || strings.Contains(fn, ".") {
		return group, routeName(method, path)
	}
	return group, fn
}

func routeName(method, path string) string {
	var b strings.Builder
	b.WriteString(strings.ToLower(method))
	for _, seg := range strings.Split(path, "/") {
		seg = strings.Trim(seg, ":*{}.")
		if seg == "" {
			continue
		}
		b.WriteByte('_')
		for _, r := range seg {
			if r == '-' || r == '.' {
				r = '_'
			}
			b.WriteRune(r)
		}
	}
	return b.String()
}

// docPath 把 gin 的 :id、*path 转成文档里的 {id}、{path}。
func docPath(p string) string {
	if !strings.ContainsAny(p, ":*") && !strings.Contains(p, "...}") {
		return p
	}
	segs := strings.Split(p, "/")
	for i, s := range segs {
		if strings.HasPrefix(s, ":") || strings.HasPrefix(s, "*") {
			segs[i] = "{" + s[1:] + "}"
		} else if strings.HasPrefix(s, "{") && strings.HasSuffix(s, "...}") {
			segs[i] = strings.TrimSuffix(s, "...}") + "}" // ServeMux 的 {path...}
		}
	}
	return strings.Join(segs, "/")
}

// pathParams 返回路径里的参数名。
func pathParams(p string) map[string]bool {
	out := map[string]bool{}
	for _, s := range strings.Split(docPath(p), "/") {
		if strings.HasPrefix(s, "{") && strings.HasSuffix(s, "}") {
			n := strings.TrimSuffix(strings.TrimPrefix(s, "{"), "}")
			n = strings.TrimSuffix(n, "...") // Go 1.22 ServeMux {path...}
			out[n] = true
		}
	}
	return out
}

func uniq(ss ...string) []string {
	out := []string{}
	seen := map[string]bool{}
	for _, s := range ss {
		if seen[s] {
			continue
		}
		seen[s] = true
		out = append(out, s)
	}
	return out
}
