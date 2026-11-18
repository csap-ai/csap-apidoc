package validate

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"mime"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
	"unicode/utf8"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// Mode 是校验模式。
type Mode int

const (
	// Off 不校验(默认,与 Java csap.validation.enabled 默认不开一致)。
	Off Mode = iota
	// Observe 观察模式:校验并调用 OnViolation 记录,但放行请求。
	// 适合规则写了但从没生效过的项目,先跑一段时间确认不会误拦截正常调用方。
	Observe
	// Enforce 拦截模式:校验失败直接返回错误,不进业务 handler。
	Enforce
)

// TipType 是错误信息的格式,与 Java ValidationTipType 一致。
type TipType string

const (
	Tip1  TipType = "TIP_1"   // 字段%s,%s     字段名,message
	Tip11 TipType = "TIP_11"  // 字段%s%s
	Tip2  TipType = "TIP_2"   // %s,%s         字段名,message
	Tip22 TipType = "TIP_22"  // %s%s
	Tip3  TipType = "TIP_3"   // %s,%s         字段说明,message
	Tip33 TipType = "TIP_33"  // %s%s
	Tip31 TipType = "TIP_3_1" // %s            只有 message
)

// Options 是 New 的参数。
type Options struct {
	// Mode 是全局模式,默认 Off。
	Mode Mode
	// Endpoints 按接口(规则的键 group.name)覆盖全局模式。
	Endpoints map[string]Mode
	// TipType 是错误信息格式,默认 TIP_1("字段username,用户名不能为空")。
	TipType TipType
	// MaxBodyBytes 是为校验读取请求体的上限,默认 1MB;超过时跳过请求体里的字段。
	MaxBodyBytes int64
	// RequiredCode / RequiredMessage 是只配了必填、没配校验规则时的错误码和信息,默认 "400" / "不能为空"。
	RequiredCode, RequiredMessage string
	// OnViolation 在校验失败时调用(两种模式都会调用),默认用 slog 记一条 Warn。
	OnViolation func(r *http.Request, v *Violation)
	// Respond 在拦截模式下输出错误,默认与 Java 一致:HTTP 200 + ApidocResult{code, message}。
	Respond func(w http.ResponseWriter, r *http.Request, v *Violation)
}

// Violation 是一次校验失败。
type Violation struct {
	Endpoint string // 接口规则的键
	// Field 是出错的字段:请求体字段为规则路径(如 request.items.name);
	// 路径、查询、请求头、表单这类单值参数为参数名(如 updated_at),与 Java 的 ValidationViolation.field 一致,
	// 不是规则里的 "updated_at.updated_at" 写法。
	Field   string
	Rule    string // 失败的规则类型:Required / NotNull / NotEmpty / Pattern / Type / Min ...
	Code    string
	Message string // 已按 TipType 格式化
	Mode    Mode
}

func (v *Violation) Error() string { return v.Code + " " + v.Message }

// Validator 执行请求参数校验。规则来自 Doc 的当前快照,规则变更后自动生效。
type Validator struct {
	doc  *apidoc.Doc
	opts Options
}

// New 创建校验器。
func New(doc *apidoc.Doc, opts Options) *Validator {
	if opts.TipType == "" {
		opts.TipType = Tip1
	}
	if opts.MaxBodyBytes == 0 {
		opts.MaxBodyBytes = 1 << 20
	}
	if opts.RequiredCode == "" {
		opts.RequiredCode = "400"
	}
	if opts.RequiredMessage == "" {
		opts.RequiredMessage = "不能为空"
	}
	if opts.OnViolation == nil {
		opts.OnViolation = func(r *http.Request, v *Violation) {
			slog.Warn("apidoc: request validation failed", "mode", modeName(v.Mode), "endpoint", v.Endpoint,
				"field", v.Field, "rule", v.Rule, "code", v.Code, "message", v.Message,
				"method", r.Method, "path", r.URL.Path)
		}
	}
	if opts.Respond == nil {
		opts.Respond = func(w http.ResponseWriter, _ *http.Request, v *Violation) {
			w.Header().Set("Content-Type", "application/json;charset=UTF-8")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(model.ResultJSON(v.Code, v.Message, nil))
		}
	}
	return &Validator{doc: doc, opts: opts}
}

func modeName(m Mode) string {
	switch m {
	case Observe:
		return "observe"
	case Enforce:
		return "enforce"
	}
	return "off"
}

// ModeFor 返回某个接口生效的模式。
func (v *Validator) ModeFor(endpointKey string) Mode {
	if m, ok := v.opts.Endpoints[endpointKey]; ok {
		return m
	}
	return v.opts.Mode
}

// Handle 校验请求并按模式处理。返回 false 表示已经输出错误,调用方应停止处理。
// route 是注册时的路由路径(gin 的 c.FullPath()),pathParam 取路径参数。
func (v *Validator) Handle(w http.ResponseWriter, r *http.Request, route string, pathParam func(string) string) bool {
	ep := v.doc.Snapshot().Endpoint(r.Method, route)
	if ep == nil || len(ep.Checks) == 0 {
		return true
	}
	mode := v.ModeFor(ep.Key)
	if mode == Off {
		return true
	}
	viol := v.Check(r, ep, pathParam)
	if viol == nil {
		return true
	}
	viol.Mode = mode
	v.opts.OnViolation(r, viol)
	if mode == Observe {
		return true
	}
	v.opts.Respond(w, r, viol)
	return false
}

// Middleware 返回 net/http 中间件。route 从请求里取注册时的路由路径(Go 1.23+ 可用 r.Pattern)。
func (v *Validator) Middleware(route func(*http.Request) string, pathParam func(r *http.Request, name string) string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			pp := func(name string) string { return pathParam(r, name) }
			if v.Handle(w, r, route(r), pp) {
				next.ServeHTTP(w, r)
			}
		})
	}
}

// Check 按接口的校验项检查请求,返回第一条失败(没有失败返回 nil)。不看模式。
// 读取请求体后会放回 r.Body,后续 handler 照常能读。
func (v *Validator) Check(r *http.Request, ep *apidoc.Endpoint, pathParam func(string) string) *Violation {
	var (
		body       any
		bodyOK     bool
		form       url.Values
		bodyLoaded bool
	)
	loadBody := func() {
		if bodyLoaded {
			return
		}
		bodyLoaded = true
		raw, ok := v.readBody(r)
		if !ok || len(bytes.TrimSpace(raw)) == 0 {
			return
		}
		ct, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
		switch {
		case ct == "application/x-www-form-urlencoded":
			form, _ = url.ParseQuery(string(raw))
		default:
			dec := json.NewDecoder(bytes.NewReader(raw))
			dec.UseNumber()
			if dec.Decode(&body) == nil {
				bodyOK = true
			}
		}
	}

	for i := range ep.Checks {
		c := &ep.Checks[i]
		var values []value
		switch c.In {
		case model.ParamPath:
			s := ""
			if pathParam != nil {
				s = pathParam(c.Name)
			}
			values = []value{{v: s, present: s != ""}}
		case model.ParamHeader:
			vs, ok := r.Header[http.CanonicalHeaderKey(c.Name)]
			values = []value{strValue(vs, ok)}
		case model.ParamForm, model.ParamFormData:
			loadBody()
			if vs, ok := form[c.Name]; ok {
				values = []value{strValue(vs, true)}
			} else {
				vs, ok := r.URL.Query()[c.Name]
				values = []value{strValue(vs, ok)}
			}
		case model.ParamBody:
			loadBody()
			if !bodyOK {
				if form != nil { // 表单提交时按参数名取
					vs, ok := form[c.Name]
					values = []value{strValue(vs, ok)}
					break
				}
				continue // 请求体不是 JSON 或超过上限,交给业务 handler 处理
			}
			values = lookup(body, c.Path)
		default: // QUERY 及其他
			vs, ok := r.URL.Query()[c.Name]
			values = []value{strValue(vs, ok)}
		}
		for _, val := range values {
			if viol := v.checkValue(ep.Key, c, val); viol != nil {
				return viol
			}
		}
	}
	return nil
}

func (v *Validator) readBody(r *http.Request) ([]byte, bool) {
	if r.Body == nil || r.Body == http.NoBody {
		return nil, false
	}
	ct, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if strings.HasPrefix(ct, "multipart/") {
		return nil, false
	}
	buf, err := io.ReadAll(io.LimitReader(r.Body, v.opts.MaxBodyBytes+1))
	if err != nil {
		r.Body = io.NopCloser(io.MultiReader(bytes.NewReader(buf), r.Body))
		return nil, false
	}
	if int64(len(buf)) > v.opts.MaxBodyBytes {
		r.Body = io.NopCloser(io.MultiReader(bytes.NewReader(buf), r.Body))
		return nil, false
	}
	_ = r.Body.Close()
	r.Body = io.NopCloser(bytes.NewReader(buf))
	return buf, true
}

// value 是取到的一个值。present=false 表示没传(对应 Java 的 null)。
type value struct {
	v       any // string / json.Number / bool / []any / map[string]any / nil
	present bool
}

func strValue(vs []string, ok bool) value {
	if !ok || len(vs) == 0 {
		return value{}
	}
	return value{v: vs[0], present: true}
}

// lookup 沿 JSON 路径取值,经过数组时展开每个元素。
func lookup(root any, path []string) []value {
	cur := []any{root}
	for i, seg := range path {
		var next []any
		for _, c := range cur {
			for _, item := range expand(c) {
				m, ok := item.(map[string]any)
				if !ok {
					continue
				}
				x, ok := m[seg]
				if !ok {
					x, ok = m[strategy.ToUnderline(seg)]
				}
				if !ok {
					x, ok = m[strategy.ToCamel(seg)]
				}
				if !ok || x == nil {
					if i == len(path)-1 {
						next = append(next, nil)
					}
					// 上级对象不存在时不校验下级(与 Java 一致,null 对象不级联)
					continue
				}
				next = append(next, x)
			}
		}
		cur = next
	}
	out := make([]value, 0, len(cur))
	for _, c := range cur {
		out = append(out, value{v: c, present: c != nil})
	}
	return out
}

func expand(x any) []any {
	if a, ok := x.([]any); ok {
		var out []any
		for _, e := range a {
			out = append(out, expand(e)...)
		}
		return out
	}
	return []any{x}
}

// violationField 返回报告用的字段名:单值参数用参数名,请求体字段用规则路径。
func violationField(c *apidoc.FieldCheck) string {
	if c.In != model.ParamBody && c.Name != "" {
		return c.Name
	}
	return c.Field
}

func (v *Validator) checkValue(endpoint string, c *apidoc.FieldCheck, val value) *Violation {
	fail := func(rule, code, msg string) *Violation {
		return &Violation{Endpoint: endpoint, Field: violationField(c), Rule: rule, Code: code, Message: msg}
	}
	// 类型(对应 Java MethodArgumentTypeMismatch / fastjson DeserializerValidate:code 400,"<说明>,数据格式错误")
	if c.DataType != "" && val.present && !typeOK(c.DataType, val.v) {
		msg := "数据格式错误"
		if c.Remark != "" && c.Remark != c.FieldName {
			msg = c.Remark + "," + msg
		}
		return fail("Type", "400", msg)
	}
	if !c.Required && !val.present {
		return nil // 非必填且没传:不校验
	}
	rules := c.Rules
	if len(rules) == 0 {
		if c.Required && isEmptyValue(val, true) {
			return fail("Required", v.opts.RequiredCode, v.tip(c, v.opts.RequiredMessage))
		}
		return nil
	}
	sorted := make([]strategy.ValidateRule, len(rules))
	copy(sorted, rules)
	sort.SliceStable(sorted, func(i, j int) bool { return sorted[i].Level < sorted[j].Level })
	for _, rule := range sorted {
		ok, err := run(rule, val)
		if err != nil {
			slog.Error("apidoc: bad validate rule", "endpoint", endpoint, "field", c.Field, "rule", rule.Type, "err", err)
			continue
		}
		if !ok {
			code := rule.Code
			if code == "" {
				code = "400"
			}
			msg := rule.Message
			if msg == "" {
				msg = rule.Descr
			}
			return fail(rule.Type, code, v.tip(c, msg))
		}
	}
	return nil
}

func (v *Validator) tip(c *apidoc.FieldCheck, msg string) string {
	switch v.opts.TipType {
	case Tip11:
		return "字段" + c.FieldName + msg
	case Tip2:
		return c.FieldName + "," + msg
	case Tip22:
		return c.FieldName + msg
	case Tip3:
		return c.Remark + "," + msg
	case Tip33:
		return c.Remark + msg
	case Tip31:
		return msg
	}
	return "字段" + c.FieldName + "," + msg
}

func typeOK(dataType string, x any) bool {
	var s string
	switch t := x.(type) {
	case string:
		s = strings.TrimSpace(t)
		if s == "" {
			return true // 空串交给必填规则
		}
	case json.Number:
		s = t.String()
	case bool:
		return dataType == "Boolean"
	default:
		return false
	}
	switch dataType {
	case "Integer":
		_, err := strconv.ParseInt(s, 10, 32)
		return err == nil
	case "Long":
		_, err := strconv.ParseInt(s, 10, 64)
		return err == nil
	case "Double", "Float":
		_, err := strconv.ParseFloat(s, 64)
		return err == nil
	case "Boolean":
		_, err := strconv.ParseBool(s)
		return err == nil
	}
	return true
}

// isEmptyValue:NotEmpty 的判定(null、空串、空数组、空对象);strict=false 时只看 null(NotNull)。
func isEmptyValue(val value, strict bool) bool {
	if !val.present || val.v == nil {
		return true
	}
	if !strict {
		return false
	}
	switch t := val.v.(type) {
	case string:
		return t == ""
	case []any:
		return len(t) == 0
	case map[string]any:
		return len(t) == 0
	}
	return false
}

// toString 对应 Java value.toString(),用于正则、长度等规则。
func toString(x any) string {
	switch t := x.(type) {
	case string:
		return t
	case json.Number:
		return t.String()
	case bool:
		return strconv.FormatBool(t)
	case nil:
		return ""
	}
	b, _ := json.Marshal(x)
	return string(b)
}

// Func 是自定义校验规则:返回 true 表示通过。present=false 表示没传。
type Func func(value any, present bool, rule strategy.ValidateRule) (bool, error)

var (
	customMu sync.RWMutex
	custom   = map[string]Func{}
)

// Register 注册自定义规则,规则的 type 填 name 即可使用,如业务字典校验。
func Register(name string, fn Func) {
	customMu.Lock()
	defer customMu.Unlock()
	custom[name] = fn
}

func run(rule strategy.ValidateRule, val value) (bool, error) {
	switch strings.ToLower(rule.Type) {
	case "notnull":
		return !isEmptyValue(val, false), nil
	case "notempty", "notblank":
		if strings.EqualFold(rule.Type, "NotBlank") {
			if s, ok := val.v.(string); ok {
				return strings.TrimSpace(s) != "", nil
			}
		}
		return !isEmptyValue(val, true), nil
	case "pattern":
		if !val.present {
			return true, nil // 与 Java 一致:null 不做正则校验
		}
		re, err := compile(rule.Pattern)
		if err != nil {
			return false, err
		}
		return re.MatchString(toString(val.v)), nil
	case "min", "max":
		if !val.present {
			return true, nil
		}
		bound, err := strconv.ParseFloat(strings.TrimSpace(rule.Pattern), 64)
		if err != nil {
			return false, fmt.Errorf("%s 的 pattern 应为数字: %q", rule.Type, rule.Pattern)
		}
		n, err := strconv.ParseFloat(toString(val.v), 64)
		if err != nil {
			return false, nil
		}
		if strings.EqualFold(rule.Type, "min") {
			return n >= bound, nil
		}
		return n <= bound, nil
	case "length", "size":
		if !val.present {
			return true, nil
		}
		lo, hi, err := parseRange(rule.Pattern)
		if err != nil {
			return false, err
		}
		var n int
		switch t := val.v.(type) {
		case []any:
			n = len(t)
		case map[string]any:
			n = len(t)
		default:
			n = utf8.RuneCountInString(toString(t))
		}
		return n >= lo && (hi < 0 || n <= hi), nil
	case "enum":
		if !val.present {
			return true, nil
		}
		s := toString(val.v)
		for _, e := range strings.Split(rule.Pattern, ",") {
			if strings.TrimSpace(e) == s {
				return true, nil
			}
		}
		return false, nil
	}
	customMu.RLock()
	fn := custom[rule.Type]
	customMu.RUnlock()
	if fn == nil {
		return false, fmt.Errorf("未知的校验类型 %q", rule.Type)
	}
	return fn(val.v, val.present, rule)
}

// parseRange 解析 "1,20"、"1,"(不限上限)、"5"(恰好 5)。
func parseRange(p string) (int, int, error) {
	parts := strings.SplitN(p, ",", 2)
	lo, err := strconv.Atoi(strings.TrimSpace(parts[0]))
	if err != nil {
		return 0, 0, fmt.Errorf("length 的 pattern 应为 \"最小,最大\": %q", p)
	}
	if len(parts) == 1 {
		return lo, lo, nil
	}
	if strings.TrimSpace(parts[1]) == "" {
		return lo, -1, nil
	}
	hi, err := strconv.Atoi(strings.TrimSpace(parts[1]))
	if err != nil {
		return 0, 0, fmt.Errorf("length 的 pattern 应为 \"最小,最大\": %q", p)
	}
	return lo, hi, nil
}
