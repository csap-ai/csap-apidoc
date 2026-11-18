// Package mock 按文档里的返回结构生成可复现的 Mock 数据,规则与 Java 版一致,见 docs/design/1.4.0-mock-schema.md。
// 本包只依赖 model,不依赖 HTTP;/csap/mock 的路由在 apidoc 包里。
package mock

import (
	"fmt"
	"math"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/ordered"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/typekind"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// 默认值与取值范围。
const (
	DefaultSeed      = "csap"
	DefaultArraySize = 2
	DefaultMaxDepth  = 6
)

// Options 是生成参数。
type Options struct {
	// Seed 是随机种子,默认 "csap"(结果可复现)。
	Seed string
	// ArraySize 是数组默认长度,范围 0~10,默认 2。
	ArraySize *int
	// MaxDepth 是对象嵌套深度上限,范围 1~20,默认 6。
	MaxDepth int
	// DisableHeuristics 为 true 时关闭字段名启发式(email、phone、id、name 等按名字推测取值),只按类型取默认值。默认开启。
	DisableHeuristics bool
}

func (o Options) normalized() Options {
	if o.Seed == "" {
		o.Seed = DefaultSeed
	}
	n := DefaultArraySize
	if o.ArraySize != nil {
		n = *o.ArraySize
	}
	o.ArraySize = clampPtr(n, 0, 10)
	if o.MaxDepth == 0 {
		o.MaxDepth = DefaultMaxDepth
	}
	if o.MaxDepth < 1 {
		o.MaxDepth = 1
	}
	if o.MaxDepth > 20 {
		o.MaxDepth = 20
	}
	return o
}

func clampPtr(n, lo, hi int) *int {
	if n < lo {
		n = lo
	}
	if n > hi {
		n = hi
	}
	return &n
}

// Size 是 ArraySize 的便捷写法。
func Size(n int) *int { return &n }

type gen struct {
	o Options
	n int // 数组默认长度(已夹到 0~10)
}

// Generate 按接口的第一个返回模型生成 Mock 数据。OBJECT / T_OBJECT → 对象,ARRAY → 数组(元素是对象),
// BASE_DATA → 单个标量。没有返回模型时 ok 为 false(调用方回 200 + 空 body)。
// 返回值可以直接 json.Marshal;对象保持文档里的字段顺序。
func Generate(m *model.Method, o Options) (v any, ok bool) {
	if m == nil || len(m.Response) == 0 || m.Response[0] == nil {
		return nil, false
	}
	return GenerateModel(m.Response[0], o), true
}

// GenerateModel 按一个返回模型生成数据。
func GenerateModel(md *model.Model, o Options) any {
	o = o.normalized()
	g := &gen{o: o, n: *o.ArraySize}
	switch md.ModelType {
	case model.ModelBaseData:
		k, _ := typekind.Of(md.Name)
		return g.scalar(&model.Parameter{}, k, "$")
	case model.ModelArray:
		out := make([]any, g.n)
		for i := range out {
			out[i] = g.object(md.Parameters, fmt.Sprintf("$[%d]", i), 1)
		}
		return out
	}
	return g.object(md.Parameters, "$", 1)
}

func (g *gen) object(params []*model.Parameter, path string, depth int) *ordered.Map {
	out := ordered.New()
	for _, p := range params {
		if typekind.Hidden(p) {
			continue
		}
		out.Set(p.Name, g.field(p, path+"."+p.Name, depth))
	}
	return out
}

// field 生成一个字段的值;depth 是所在对象的深度(根对象为 1)。
func (g *gen) field(p *model.Parameter, path string, depth int) any {
	kind, elem := typekind.Of(p.DataType)
	kids := typekind.Children(p)
	if p.ModelType != nil && *p.ModelType == model.ModelArray && kind != typekind.Array {
		kind = typekind.Array
		if elem == "" {
			elem = "string"
		}
	}
	if p.Mock != nil {
		return g.mockValue(p.Mock, kind, rnd(g.o.Seed, path))
	}
	if kind == typekind.Array {
		n := g.arrayLen(p)
		if len(kids) > 0 {
			if depth+1 > g.o.MaxDepth {
				return []any{}
			}
			out := make([]any, n)
			for i := range out {
				out[i] = g.object(kids, fmt.Sprintf("%s[%d]", path, i), depth+1)
			}
			return out
		}
		ek, _ := typekind.Of(elem)
		ep := &model.Parameter{Name: p.Name, DataType: elem}
		out := make([]any, n)
		for i := range out {
			out[i] = g.scalar(ep, ek, fmt.Sprintf("%s[%d]", path, i))
		}
		return out
	}
	if len(kids) > 0 {
		if depth+1 > g.o.MaxDepth {
			return nil
		}
		return g.object(kids, path, depth+1)
	}
	return g.scalar(p, kind, path)
}

func (g *gen) arrayLen(p *model.Parameter) int {
	n := g.n
	for _, v := range append(typekind.Validates(p, "size"), typekind.Validates(p, "length")...) {
		lo, hi, ok := typekind.ParseRange(v.Pattern)
		if !ok {
			continue
		}
		if lo > n {
			n = lo
		}
		if hi >= 0 && hi < n {
			n = hi
		}
	}
	if len(typekind.Validates(p, "notempty")) > 0 && n < 1 {
		n = 1
	}
	if n < 0 {
		n = 0
	}
	if n > 10 {
		n = 10
	}
	return n
}

var (
	idRE      = regexp.MustCompile(`(?i:^id$)|^.*[a-z0-9](?:Id|ID)$|^.*_id$`)
	mockIntRE = regexp.MustCompile(`^@int\(\s*(-?\d+)\s*,\s*(-?\d+)\s*\)$`)
	oneOfRE   = regexp.MustCompile(`(?s)^@oneOf\((.*)\)$`)
	names     = []string{"Alice", "Bob", "Carol", "David"}
)

func (g *gen) mockValue(m any, kind typekind.Kind, r uint64) any {
	s, ok := m.(string)
	if !ok {
		return m // 不是字符串的 mock 原样输出
	}
	if strings.HasPrefix(s, "@@") {
		return s[1:]
	}
	if !strings.HasPrefix(s, "@") {
		return s
	}
	if mm := mockIntRE.FindStringSubmatch(s); mm != nil {
		lo, _ := strconv.ParseInt(mm[1], 10, 64)
		hi, _ := strconv.ParseInt(mm[2], 10, 64)
		if hi < lo {
			return lo
		}
		return lo + int64(r%uint64(hi-lo+1))
	}
	if mm := oneOfRE.FindStringSubmatch(s); mm != nil {
		items := strings.Split(mm[1], ",")
		for i := range items {
			items[i] = strings.TrimSpace(items[i])
		}
		pick := items[r%uint64(len(items))]
		if v, ok := typekind.Coerce(pick, kind); ok {
			return v
		}
		return pick
	}
	if s == "@uuid" {
		h := fmt.Sprintf("%016x%016x", r, splitmix64(r))
		return h[0:8] + "-" + h[8:12] + "-4" + h[13:16] + "-8" + h[17:20] + "-" + h[20:32]
	}
	return s // 未知表达式原样输出
}

func enumValues(p *model.Parameter) []string {
	for _, v := range typekind.Validates(p, "enum") {
		var vals []string
		for _, x := range strings.Split(v.Pattern, ",") {
			if x = strings.TrimSpace(x); x != "" {
				vals = append(vals, x)
			}
		}
		if len(vals) > 0 {
			return vals
		}
	}
	var names []string
	for _, e := range p.ExtendDescr {
		if n := e["name"]; n != "" {
			names = append(names, n)
		}
	}
	return names
}

func (g *gen) scalar(p *model.Parameter, kind typekind.Kind, path string) any {
	r := rnd(g.o.Seed, path)
	name := p.Name
	low := strings.ToLower(strings.NewReplacer("_", "", "-", "").Replace(name))
	heur := !g.o.DisableHeuristics
	// 1 mock
	if p.Mock != nil {
		return g.mockValue(p.Mock, kind, r)
	}
	// 2 example、3 defaultValue
	for _, s := range []string{p.Example, p.DefaultValue} {
		if s != "" {
			if v, ok := typekind.Coerce(s, kind); ok {
				return v
			}
		}
	}
	// 4 枚举
	if ev := enumValues(p); len(ev) > 0 {
		if v, ok := typekind.Coerce(ev[r%uint64(len(ev))], kind); ok {
			return v
		}
	}
	// 5 约束、6 字段名启发式、7 类型默认值
	switch kind {
	case typekind.Integer:
		mins, maxs := bounds(p)
		if len(mins) == 0 && len(maxs) == 0 && heur {
			switch {
			case idRE.MatchString(name):
				return int64(1000 + r%9000)
			case low == "code":
				return int64(200)
			case in(low, "page", "pageno", "pagenum", "current", "pageindex"):
				return int64(1)
			case in(low, "pagesize", "size", "limit", "perpage"):
				return int64(10)
			case low == "age":
				return int64(18 + r%60)
			}
		}
		lo := int64(1)
		if len(mins) > 0 {
			lo = int64(maxOf(mins))
		}
		var hi int64
		switch {
		case len(maxs) > 0:
			hi = int64(minOf(maxs))
		case len(mins) > 0:
			hi = lo + 99
		default:
			hi = 100
		}
		if hi < lo {
			hi = lo
		}
		span := uint64(hi-lo) + 1
		if span > 1<<53 || span == 0 {
			span = 1 << 53
		}
		return lo + int64(r%span)
	case typekind.Number:
		mins, maxs := bounds(p)
		lo := 1.0
		if len(mins) > 0 {
			lo = maxOf(mins)
		}
		var hi float64
		switch {
		case len(maxs) > 0:
			hi = minOf(maxs)
		case len(mins) > 0:
			hi = lo + 99
		default:
			hi = 100.0
		}
		if hi < lo {
			hi = lo
		}
		steps := uint64(math.RoundToEven((hi - lo) * 100))
		return round2(lo + float64(r%(steps+1))/100.0)
	case typekind.Boolean:
		if heur && low == "success" {
			return true
		}
		return r%2 == 0
	case typekind.Date:
		return day(r)
	case typekind.DateTime:
		return day(r) + "T" + clock(r) + "Z"
	case typekind.Time:
		return clock(r)
	case typekind.Object:
		return ordered.New()
	}
	// string
	s := "string"
	if heur {
		switch {
		case in(low, "message", "msg"):
			s = "ok"
		case low == "code":
			s = "200"
		case low == "email":
			s = fmt.Sprintf("user%d@example.com", r%1000)
		case in(low, "phone", "mobile", "tel", "telephone", "cellphone"):
			s = fmt.Sprintf("13%09d", r%1_000_000_000)
		case in(low, "name", "username", "nickname", "realname", "fullname"):
			s = names[r%4]
		case idRE.MatchString(name):
			s = fmt.Sprintf("id-%08x", r>>32)
		case in(low, "url", "link", "website", "homepage", "avatar", "image", "img", "icon", "logo", "picture"):
			s = "https://example.com/" + name
		}
	}
	return fitString(s, p)
}

func in(s string, set ...string) bool {
	for _, x := range set {
		if s == x {
			return true
		}
	}
	return false
}

func bounds(p *model.Parameter) (mins, maxs []float64) {
	for _, v := range typekind.Validates(p, "min") {
		if f, err := strconv.ParseFloat(strings.TrimSpace(v.Pattern), 64); err == nil {
			mins = append(mins, f)
		}
	}
	for _, v := range typekind.Validates(p, "max") {
		if f, err := strconv.ParseFloat(strings.TrimSpace(v.Pattern), 64); err == nil {
			maxs = append(maxs, f)
		}
	}
	return
}

func maxOf(a []float64) float64 {
	m := a[0]
	for _, x := range a[1:] {
		if x > m {
			m = x
		}
	}
	return m
}

func minOf(a []float64) float64 {
	m := a[0]
	for _, x := range a[1:] {
		if x < m {
			m = x
		}
	}
	return m
}

func round2(f float64) float64 { return math.Round(f*100) / 100 }

var epoch = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)

func day(r uint64) string { return epoch.AddDate(0, 0, int(r%365)).Format("2006-01-02") }

func clock(r uint64) string {
	secs := (r >> 16) % 86400
	return fmt.Sprintf("%02d:%02d:%02d", secs/3600, secs%3600/60, secs%60)
}

// fitString 按字段上的 Length 校验补齐或截断字符串(按字符,不是字节):不足最小长度用 x 在末尾补齐,超过最大长度从末尾截断。
// 只作用于第 6、7 步得到的字符串;mock、example、defaultValue、枚举得到的值不裁剪。
func fitString(s string, p *model.Parameter) string {
	for _, v := range typekind.Validates(p, "length") {
		lo, hi, ok := typekind.ParseRange(v.Pattern)
		if !ok {
			continue
		}
		if n := utf8.RuneCountInString(s); n < lo {
			s += strings.Repeat("x", lo-n)
		}
		if hi >= 0 {
			if rs := []rune(s); len(rs) > hi {
				s = string(rs[:hi])
			}
		}
	}
	return s
}
