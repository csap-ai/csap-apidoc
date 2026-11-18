// Package jsonschema 把文档里的接口导出成 JSON Schema 2020-12,并支持把(导出再被修改的)Schema 里的说明、必填、示例、
// 校验导入成字段规则。规则与 Java 版一致,见 docs/design/1.4.0-mock-schema.md 第 4、5 节。
package jsonschema

import (
	"math"
	"strconv"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/ordered"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/typekind"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// Draft 是 Schema 方言。
const Draft = "https://json-schema.org/draft/2020-12/schema"

// maxDepth 是嵌套上限,超过后对象写 {"type":"object"},数组写 {"type":"array","items":{"type":"object"}}。
const maxDepth = 12

// SchemaVersion 是合集文件里 x-csap-schema-version 的值。
const SchemaVersion = 1

// MethodDoc 是 /csap/schema/method 的内容。
type MethodDoc struct {
	OperationID string `json:"operationId"`
	Method      string `json:"method"`
	Path        string `json:"path"`
	Summary     string `json:"summary"`
	Deprecated  bool   `json:"deprecated"`
	// Request / Response 各自是带 $schema 的独立 Schema,没有时为 null。
	Request  *ordered.Map `json:"request"`
	Response *ordered.Map `json:"response"`
}

// Method 导出一个接口的请求、返回 Schema。
func Method(m *model.Method) *MethodDoc {
	return &MethodDoc{
		OperationID: m.Key,
		Method:      verbOf(m),
		Path:        FullPath(m),
		Summary:     m.Value,
		Deprecated:  m.Status == model.StatusDeprecated,
		Request:     RequestSchema(m),
		Response:    ResponseSchema(m),
	}
}

func verbOf(m *model.Method) string {
	if len(m.Methods) > 0 && m.Methods[0] != "" {
		return m.Methods[0]
	}
	return "GET"
}

// FullPath 拼出接口的完整路径:apiPath[0] + paths[0],各段首尾 / 规整为单个 /,保留 {name}。
func FullPath(m *model.Method) string {
	base, sub := "", ""
	if len(m.ApiPath) > 0 {
		base = m.ApiPath[0]
	}
	if len(m.Paths) > 0 {
		sub = m.Paths[0]
	}
	p := strings.TrimRight(base, "/") + "/" + strings.Trim(sub, "/")
	p = strings.Trim(p, "/")
	if p == "" {
		return "/"
	}
	return "/" + p
}

// Collection 导出全部接口的合集文件(隐藏接口不导出),接口顺序与文档一致。
func Collection(doc *model.Response) *ordered.Map {
	ops := []any{}
	defs := ordered.New()
	for _, c := range doc.ApiList {
		if c == nil || c.Hidden {
			continue
		}
		hidden := map[string]bool{}
		for _, h := range c.HiddenMethod {
			hidden[h] = true
		}
		for _, m := range c.MethodList {
			if m == nil || m.Hidden || hidden[m.Name] {
				continue
			}
			md := Method(m)
			op := ordered.New().Set("operationId", md.OperationID).Set("method", md.Method).Set("path", md.Path).
				Set("summary", md.Summary).Set("deprecated", md.Deprecated)
			for _, side := range []struct {
				name string
				s    *ordered.Map
			}{{"request", md.Request}, {"response", md.Response}} {
				if side.s == nil {
					op.Set(side.name, nil)
					continue
				}
				name := md.OperationID + "." + side.name
				body := ordered.New()
				for _, k := range side.s.Keys() {
					if k != "$schema" {
						v, _ := side.s.Get(k)
						body.Set(k, v)
					}
				}
				defs.Set(name, body)
				op.Set(side.name, "#/$defs/"+strings.NewReplacer("~", "~0", "/", "~1").Replace(name))
			}
			ops = append(ops, op)
		}
	}
	title := ""
	if doc.ApiInfo != nil {
		title = doc.ApiInfo.Title
	}
	return ordered.New().Set("$schema", Draft).Set("title", title).Set("x-csap-schema-version", SchemaVersion).
		Set("x-csap-operations", ops).Set("$defs", defs)
}

// ---- 返回 ----

// ResponseSchema 取第一个返回模型:OBJECT / T_OBJECT → 对象,ARRAY → 数组,BASE_DATA → 标量。没有返回模型返回 nil。
func ResponseSchema(m *model.Method) *ordered.Map {
	if len(m.Response) == 0 || m.Response[0] == nil {
		return nil
	}
	md := m.Response[0]
	var s *ordered.Map
	switch md.ModelType {
	case model.ModelBaseData:
		s = baseSchema(md.Name)
	case model.ModelArray:
		s = ordered.New().Set("type", "array").Set("items", objectSchema(md.Parameters, 1))
	default:
		s = objectSchema(md.Parameters, 1)
	}
	return withDraft(s)
}

func withDraft(s *ordered.Map) *ordered.Map {
	out := ordered.New().Set("$schema", Draft)
	for _, k := range s.Keys() {
		v, _ := s.Get(k)
		out.Set(k, v)
	}
	return out
}

// ---- 请求 ----

var sections = []string{"path", "query", "header", "form", "body"}

// Location 返回字段所在的请求分区:PATH→path,QUERY→query,HEADER→header,FORM/FORM_DATA→form,BODY→body;
// DEFAULT(或缺省)按方法:GET / DELETE / HEAD → query,其他 → body。
func Location(p *model.Parameter, verb string) string {
	switch p.ParamType {
	case model.ParamPath:
		return "path"
	case model.ParamQuery:
		return "query"
	case model.ParamHeader:
		return "header"
	case model.ParamForm, model.ParamFormData:
		return "form"
	case model.ParamBody:
		return "body"
	}
	switch strings.ToUpper(verb) {
	case "GET", "DELETE", "HEAD":
		return "query"
	}
	return "body"
}

// RequestSchema 按位置分区导出请求:{type: object, properties: {path, query, header, form, body}},只含有字段的分区。
// 没有任何请求字段返回 nil。
func RequestSchema(m *model.Method) *ordered.Map {
	verb := verbOf(m)
	secs := map[string][]*model.Parameter{}
	var whole []*model.Parameter
	for _, md := range m.Request {
		if md == nil {
			continue
		}
		for _, p := range md.Parameters {
			if typekind.Hidden(p) {
				continue
			}
			loc := Location(p, verb)
			secs[loc] = append(secs[loc], p)
		}
		if md.ModelType == model.ModelArray {
			var body []*model.Parameter
			for _, p := range md.Parameters {
				if !typekind.Hidden(p) && Location(p, verb) == "body" {
					body = append(body, p)
				}
			}
			if len(body) > 0 {
				whole = body
				inBody := map[*model.Parameter]bool{}
				for _, p := range body {
					inBody[p] = true
				}
				var rest []*model.Parameter
				for _, q := range secs["body"] {
					if !inBody[q] {
						rest = append(rest, q)
					}
				}
				if len(rest) == 0 {
					delete(secs, "body")
				} else {
					secs["body"] = rest
				}
			}
		}
	}
	props := ordered.New()
	var req []any
	for _, loc := range sections {
		if loc == "body" && whole != nil {
			props.Set("body", ordered.New().Set("type", "array").Set("items", objectSchema(whole, 1)))
			req = append(req, "body")
			continue
		}
		if ps, ok := secs[loc]; ok {
			o := objectSchema(ps, 1)
			props.Set(loc, o)
			if o.Has("required") {
				req = append(req, loc)
			}
		}
	}
	if props.Len() == 0 {
		return nil
	}
	s := ordered.New().Set("$schema", Draft).Set("type", "object").Set("properties", props)
	if len(req) > 0 {
		s.Set("required", req)
	}
	return s
}

// ---- 字段 → Schema ----

func objectSchema(params []*model.Parameter, depth int) *ordered.Map {
	props := ordered.New()
	var req []any
	seen := map[string]bool{}
	for _, p := range params {
		if typekind.Hidden(p) {
			continue
		}
		props.Set(p.Name, fieldSchema(p, depth))
		if p.Required && !seen[p.Name] {
			seen[p.Name] = true
			req = append(req, p.Name)
		}
	}
	s := ordered.New().Set("type", "object").Set("properties", props)
	if len(req) > 0 {
		s.Set("required", req)
	}
	return s
}

func baseSchema(dataType string) *ordered.Map {
	kind, elem := typekind.Of(dataType)
	raw := typekind.Raw(dataType)
	switch kind {
	case typekind.Array:
		return ordered.New().Set("type", "array").Set("items", baseSchema(elem))
	case typekind.Integer:
		f := "int32"
		switch raw {
		case "long", "int64", "biginteger", "atomiclong":
			f = "int64"
		}
		return ordered.New().Set("type", "integer").Set("format", f)
	case typekind.Number:
		switch raw {
		case "float", "float32":
			return ordered.New().Set("type", "number").Set("format", "float")
		case "bigdecimal":
			return ordered.New().Set("type", "number")
		}
		return ordered.New().Set("type", "number").Set("format", "double")
	case typekind.Boolean:
		return ordered.New().Set("type", "boolean")
	case typekind.Date:
		return ordered.New().Set("type", "string").Set("format", "date")
	case typekind.DateTime:
		return ordered.New().Set("type", "string").Set("format", "date-time")
	case typekind.Time:
		return ordered.New().Set("type", "string").Set("format", "time")
	case typekind.Object:
		return ordered.New().Set("type", "object")
	}
	return ordered.New().Set("type", "string")
}

func fieldSchema(p *model.Parameter, depth int) *ordered.Map {
	kids := typekind.Children(p)
	kind, elem := typekind.Of(p.DataType)
	if p.ModelType != nil && *p.ModelType == model.ModelArray && kind != typekind.Array {
		kind = typekind.Array
	}
	var s *ordered.Map
	switch {
	case len(kids) > 0 && depth < maxDepth:
		obj := objectSchema(kids, depth+1)
		if kind == typekind.Array {
			s = ordered.New().Set("type", "array").Set("items", obj)
		} else {
			s = obj
		}
	case len(kids) > 0:
		if kind == typekind.Array {
			s = ordered.New().Set("type", "array").Set("items", ordered.New().Set("type", "object"))
		} else {
			s = ordered.New().Set("type", "object")
		}
	case kind == typekind.Array:
		s = ordered.New().Set("type", "array").Set("items", baseSchema(elem))
	case p.ModelType != nil && (*p.ModelType == model.ModelObject || *p.ModelType == model.ModelTObject) && kind == typekind.String:
		s = ordered.New().Set("type", "object")
	default:
		s = baseSchema(p.DataType)
	}
	if d := firstNonEmpty(p.Description, p.Value); d != "" {
		s.Set("description", d)
	}
	ek, _ := s.Get("type")
	scalar := ek == "string" || ek == "integer" || ek == "number" || ek == "boolean"
	if p.Example != "" && scalar {
		s.Set("examples", []any{coerceOr(p.Example, ek.(string))})
	}
	if p.DefaultValue != "" && scalar {
		s.Set("default", coerceOr(p.DefaultValue, ek.(string)))
	}
	applyValidate(s, p)
	var names []any
	for _, e := range p.ExtendDescr {
		if n := e["name"]; n != "" {
			names = append(names, n)
		}
	}
	if len(names) > 0 && !s.Has("enum") && ek == "string" {
		s.Set("enum", names)
	}
	if xv := xValidate(p); len(xv) > 0 {
		s.Set("x-csap-validate", xv)
	}
	if p.KeyName != "" {
		s.Set("x-csap-key", p.KeyName)
	}
	return s
}

func firstNonEmpty(a ...string) string {
	for _, s := range a {
		if s != "" {
			return s
		}
	}
	return ""
}

// coerceOr 按 Schema 类型转换文本,失败用原字符串。
func coerceOr(s, t string) any {
	if v, ok := typekind.Coerce(s, typekind.Kind(t)); ok {
		return v
	}
	return s
}

func xValidate(p *model.Parameter) []any {
	var out []any
	for _, v := range p.Validate {
		e := ordered.New()
		if v.Type != "" {
			e.Set("type", v.Type)
		}
		if v.Pattern != "" {
			e.Set("pattern", v.Pattern)
		}
		if v.Message != "" {
			e.Set("message", v.Message)
		}
		if v.Code != "" {
			e.Set("code", v.Code)
		}
		if v.Level != 0 {
			e.Set("level", v.Level)
		}
		out = append(out, e)
	}
	return out
}

// applyValidate 把校验规则映射成标准关键字,作用在字段自身的 Schema(数组字段作用在数组层);
// 不适用的类型忽略(仍保留在 x-csap-validate 里)。多个规则落到同一关键字时,下限取最大、上限取最小。
func applyValidate(s *ordered.Map, p *model.Parameter) {
	t, _ := s.Get("type")
	ts, _ := t.(string)
	var pats []string
	setLo := func(key string, v float64) {
		if cur, ok := s.Get(key); ok {
			if c := toFloat(cur); c > v {
				v = c
			}
		}
		s.Set(key, numValue(v))
	}
	setHi := func(key string, v float64) {
		if cur, ok := s.Get(key); ok {
			if c := toFloat(cur); c < v {
				v = c
			}
		}
		s.Set(key, numValue(v))
	}
	for _, v := range p.Validate {
		vt := strings.ToLower(v.Type)
		pat := v.Pattern
		switch vt {
		case "notempty", "notblank":
			switch ts {
			case "string":
				setLo("minLength", 1)
			case "array":
				setLo("minItems", 1)
			case "object":
				setLo("minProperties", 1)
			}
		case "length", "size":
			lo, hi, ok := typekind.ParseRange(pat)
			if !ok {
				continue
			}
			var k [2]string
			switch ts {
			case "string":
				k = [2]string{"minLength", "maxLength"}
			case "array":
				k = [2]string{"minItems", "maxItems"}
			case "object":
				k = [2]string{"minProperties", "maxProperties"}
			default:
				continue
			}
			setLo(k[0], float64(lo))
			if hi >= 0 {
				setHi(k[1], float64(hi))
			}
		case "min", "max":
			if ts != "integer" && ts != "number" {
				continue
			}
			n, err := strconv.ParseFloat(strings.TrimSpace(pat), 64)
			if err != nil {
				continue
			}
			if vt == "min" {
				setLo("minimum", n)
			} else {
				setHi("maximum", n)
			}
		case "pattern":
			if ts == "string" && pat != "" && !strings.HasPrefix(pat, "PATTERN_") {
				pats = append(pats, pat)
			}
		case "enum":
			var out []any
			for _, x := range strings.Split(pat, ",") {
				if x = strings.TrimSpace(x); x == "" {
					continue
				}
				k := typekind.String
				if ts == "integer" || ts == "number" || ts == "boolean" {
					k = typekind.Kind(ts)
				}
				if c, ok := typekind.Coerce(x, k); ok {
					out = append(out, c)
				}
			}
			if len(out) > 0 {
				s.Set("enum", out)
			}
		}
	}
	if len(pats) > 0 {
		s.Set("pattern", pats[0])
		if len(pats) > 1 {
			var all []any
			for _, x := range pats[1:] {
				all = append(all, ordered.New().Set("pattern", x))
			}
			s.Set("allOf", all)
		}
	}
}

func toFloat(v any) float64 {
	switch x := v.(type) {
	case int64:
		return float64(x)
	case int:
		return float64(x)
	case float64:
		return x
	}
	return 0
}

// numValue 整数值写成整数,否则保持小数。
func numValue(f float64) any {
	if f == math.Trunc(f) && math.Abs(f) < 1<<53 {
		return int64(f)
	}
	return f
}
