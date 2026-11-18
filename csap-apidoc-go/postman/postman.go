package postman

// 与 Java PostmanConverterService 的已知差别(其余逐项一致):
//
//  1. Java 里为 null 的字符串(如 method.value、apiInfo.title/description、查询参数 key)会输出
//     JSON null;Go 模型用的是 string 值类型,无法区分 null 与 "",一律输出 ""。
//  2. Java 在 methods 为 null 且 request 非空时访问 getMethods().contains(...) 会抛 NPE,
//     整个导出失败;Go 把 nil methods 当作空列表处理(method 取 "GET",不生成 body)。
//  3. Java 的 controller.path[0] / method.paths[0] 若为 null 会拼出 "null";Go 没有 null 元素。
//  4. doc 为 nil 时 Java 抛 NPE(包装成 RuntimeException),Go 返回 error。
//
// 有意保留的 Java 行为(看起来像缺陷,但为对齐而照搬):
//   - 请求体 raw 只平铺各请求模型的顶层参数,不展开嵌套对象/数组,也不按 paramType 过滤、不跳过 hidden;
//   - HEADER 类型的参数不会进 header,header 只来自 methodHeaders(且不过滤 hidden);
//   - 相对路径 URL 不输出 host;
//   - request.url 只有在 paramNames 非空时才带 query,而 originalRequest.url 不看 paramNames;
//   - 响应体示例字符串里的参数名不做转义。

import (
	"errors"
	"strconv"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

const (
	schemaURL       = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
	baseURLVar      = "{{baseUrl}}/"
	dateTimeExample = "2024-01-01T00:00:00"
)

// Build 返回与 Java convertToPostmanCollection 相同的 Postman Collection JSON。
func Build(doc *model.Response) ([]byte, error) {
	if doc == nil {
		return nil, errors.New("postman: nil document")
	}
	items := list{}
	for _, c := range doc.GlobalApiList {
		items = append(items, controllerItems(c)...)
	}
	for _, c := range doc.ApiList {
		items = append(items, controllerItems(c)...)
	}
	root := obj{
		{"info", collectionInfo(doc)},
		{"item", items},
		{"variable", list{obj{
			{"key", "baseUrl"},
			{"value", "http://localhost:8080"},
			{"description", "API基础URL"},
		}}},
	}
	var b strings.Builder
	writeValue(&b, root)
	return []byte(b.String()), nil
}

func collectionInfo(doc *model.Response) obj {
	name := "CSAP API Collection"
	desc := "Generated from CSAP Framework API Documentation"
	if doc.ApiInfo != nil {
		name = doc.ApiInfo.Title
		desc = doc.ApiInfo.Description
	}
	return obj{{"name", name}, {"description", desc}, {"schema", schemaURL}}
}

// controllerItems 对应 buildControllerItems:每个 Controller 一个文件夹。
func controllerItems(c *model.Controller) list {
	if c == nil || len(c.MethodList) == 0 {
		return nil
	}
	folder := c.Value
	if folder == "" {
		folder = c.SimpleName
	}
	if folder == "" {
		folder = "default"
	}
	children := list{}
	for _, m := range c.MethodList {
		if m == nil || m.Hidden {
			continue
		}
		children = append(children, requestItem(m, c))
	}
	if len(children) == 0 {
		return nil
	}
	return list{obj{{"name", folder}, {"item", children}}}
}

func firstMethod(m *model.Method) string {
	if len(m.Methods) > 0 {
		return m.Methods[0]
	}
	return "GET"
}

func hasBodyMethod(m *model.Method) bool {
	for _, v := range m.Methods {
		if v == "POST" || v == "PUT" || v == "PATCH" {
			return true
		}
	}
	return false
}

func requestItem(m *model.Method, c *model.Controller) obj {
	var u obj
	if len(m.ParamNames) > 0 && m.ParamType != model.ParamBody {
		u = urlWithQuery(m, c)
	} else {
		u = parseURL(buildURL(m, c), nil)
	}
	req := obj{{"method", firstMethod(m)}, {"url", u}}
	if len(m.MethodHeaders) > 0 {
		req = append(req, kv{"header", headers(m.MethodHeaders)})
	}
	if len(m.Request) > 0 && hasBodyMethod(m) {
		req = append(req, kv{"body", requestBody(m.Request)})
	}
	item := obj{{"name", m.Value}, {"request", req}}
	if len(m.Response) > 0 {
		item = append(item, kv{"response", responses(m, c)})
	}
	return item
}

// buildURL 对应 Java buildUrl:控制器路径 + 方法路径,中间只保留一个 /。
func buildURL(m *model.Method, c *model.Controller) string {
	u := ""
	if len(c.Path) > 0 {
		u = c.Path[0]
	}
	if len(m.Paths) > 0 {
		mp := m.Paths[0]
		if u != "" && mp != "" {
			u = strings.TrimRight(u, "/") + "/" + strings.TrimLeft(mp, "/")
		} else {
			u += mp
		}
	}
	return u
}

// urlWithQuery 对应 Java buildUrlWithQueryParams。
func urlWithQuery(m *model.Method, c *model.Controller) obj {
	var query list
	if len(m.Request) > 0 && m.ParamType != model.ParamBody {
		query = list{}
		for _, rm := range m.Request {
			if rm == nil {
				continue
			}
			for _, p := range rm.Parameters {
				if p == nil || (p.ParamType != model.ParamQuery && p.ParamType != model.ParamDefault) {
					continue
				}
				v := p.Example
				if isBlank(v) {
					v = defaultValue(p.Name)
				}
				query = append(query, queryParam(p.Name, v, p.Description, !p.Required))
			}
		}
		if len(query) == 0 {
			for _, n := range m.ParamNames {
				query = append(query, queryParam(n, defaultValue(n), paramDescription(n, m), true))
			}
		}
	}
	return parseURL(buildURL(m, c), query)
}

func queryParam(key, value, desc string, disabled bool) obj {
	q := obj{{"key", key}, {"value", value}}
	if desc != "" {
		q = append(q, kv{"description", desc})
	}
	if disabled {
		q = append(q, kv{"disabled", true})
	}
	return q
}

// parseURL 对应 PostmanUrl 构造函数 + 序列化(raw, protocol, host, path, query)。
func parseURL(raw string, query list) obj {
	var protocol string
	var host, path []string
	if strings.HasPrefix(raw, "http://") || strings.HasPrefix(raw, "https://") {
		rest := raw
		if strings.HasPrefix(raw, "https://") {
			protocol, rest = "https", raw[8:]
		} else {
			protocol, rest = "http", raw[7:]
		}
		parts := strings.SplitN(rest, "/", 2)
		host = javaSplit(parts[0], ".")
		if len(parts) > 1 {
			path = javaSplit(parts[1], "/")
		}
	} else {
		rel := strings.TrimLeft(raw, "/")
		raw = baseURLVar + rel
		for _, s := range strings.Split(rel, "/") {
			if s != "" {
				path = append(path, s)
			}
		}
	}
	u := obj{{"raw", raw}}
	if protocol != "" {
		u = append(u, kv{"protocol", protocol})
	}
	if len(host) > 0 {
		u = append(u, kv{"host", strList(host)})
	}
	if len(path) > 0 {
		u = append(u, kv{"path", strList(path)})
	}
	if len(query) > 0 {
		u = append(u, kv{"query", query})
	}
	return u
}

// javaSplit 模拟 Java String.split(literal) 的语义:去掉末尾空串;无匹配时返回原串。
func javaSplit(s, sep string) []string {
	parts := strings.Split(s, sep)
	if len(parts) == 1 {
		return parts
	}
	for len(parts) > 0 && parts[len(parts)-1] == "" {
		parts = parts[:len(parts)-1]
	}
	return parts
}

func strList(ss []string) list {
	l := make(list, len(ss))
	for i, s := range ss {
		l[i] = s
	}
	return l
}

// isBlank 对应 Java s == null || s.trim().isEmpty()(trim 去掉 <= ' ' 的字符)。
func isBlank(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] > ' ' {
			return false
		}
	}
	return true
}

var defaultValues = map[string]string{
	"id":            "1",
	"currentPage":   "1",
	"pageSize":      "20",
	"ids":           "1,2,3",
	"isSearchCount": "true",
	"leader":        "张三",
	"name":          "技术部",
	"parentId":      "0",
	"phone":         "13800138000",
	"status":        "1",
	"seq":           "1",
	"column":        "create_time",
	"asc":           "true",
	"updateTime":    dateTimeExample,
	"createTime":    dateTimeExample,
	"del":           "false",
	"updateId":      "admin",
	"createId":      "admin",
}

// defaultValue 对应 Java getDefaultValueForParam。
func defaultValue(name string) string { return defaultValues[name] }

// paramDescription 对应 Java getDescriptionForParam。
func paramDescription(name string, m *model.Method) string {
	for _, rm := range m.Request {
		if rm == nil {
			continue
		}
		for _, p := range rm.Parameters {
			if p != nil && p.Name == name {
				return p.Description
			}
		}
	}
	switch name {
	case "id":
		return "资源ID"
	case "currentPage":
		return "当前页数"
	case "pageSize":
		return "每页显示条数"
	case "ids":
		return "资源ID数组"
	}
	return ""
}

func header(key, value, desc string) obj {
	return obj{
		{"key", key},
		{"value", value},
		{"name", key},
		{"description", obj{{"content", desc}, {"type", "text/plain"}}},
	}
}

func headers(hs []model.MethodHeader) list {
	l := make(list, 0, len(hs))
	for _, h := range hs {
		l = append(l, header(h.Key, h.Value, h.Description))
	}
	return l
}

// requestBody 对应 Java buildRequestBody(只平铺顶层参数)。
func requestBody(models []*model.Model) obj {
	var b strings.Builder
	b.WriteString("{\n")
	first := true
	for _, rm := range models {
		if rm == nil {
			continue
		}
		for _, p := range rm.Parameters {
			if p == nil {
				continue
			}
			if !first {
				b.WriteString(",\n")
			}
			first = false
			b.WriteString(`  "`)
			b.WriteString(p.Name)
			b.WriteString(`": `)
			b.WriteString(jsonDefault(p))
		}
	}
	b.WriteString("\n}")
	return obj{
		{"mode", "raw"},
		{"raw", b.String()},
		{"options", obj{{"raw", obj{{"language", "json"}}}}},
	}
}

// jsonDefault 对应 Java getDefaultJsonValueForParam。
func jsonDefault(p *model.Parameter) string {
	dt := strings.ToLower(p.DataType)
	has := !isBlank(p.Example)
	switch {
	case strings.Contains(dt, "string"):
		if has {
			return `"` + escape(p.Example) + `"`
		}
		return `"示例值"`
	case strings.Contains(dt, "long") || strings.Contains(dt, "int"):
		if has {
			return p.Example
		}
		return "0"
	case strings.Contains(dt, "boolean"):
		if has {
			return p.Example
		}
		return "true"
	case strings.Contains(dt, "date") || strings.Contains(dt, "time"):
		return `"` + dateTimeExample + `"`
	default:
		if has {
			return `"` + escape(p.Example) + `"`
		}
		return `""`
	}
}

func originalRequest(m *model.Method, c *model.Controller) obj {
	o := obj{{"method", firstMethod(m)}}
	if len(m.MethodHeaders) > 0 {
		o = append(o, kv{"header", headers(m.MethodHeaders)})
	}
	if len(m.Request) > 0 && hasBodyMethod(m) {
		o = append(o, kv{"body", requestBody(m.Request)})
	}
	if len(m.Request) > 0 && m.ParamType != model.ParamBody {
		o = append(o, kv{"url", urlWithQuery(m, c)})
	} else {
		o = append(o, kv{"url", parseURL(buildURL(m, c), nil)})
	}
	return o
}

func response(m *model.Method, c *model.Controller, name, status string, code int, body string) obj {
	return obj{
		{"name", name},
		{"originalRequest", originalRequest(m, c)},
		{"status", status},
		{"code", code},
		{"_postman_previewlanguage", "json"},
		{"header", list{header("Content-Type", "application/json", "")}},
		{"cookie", list{}},
		{"body", body},
	}
}

// responses 对应 Java buildResponses:成功 + 400 + 404 + 429。
func responses(m *model.Method, c *model.Controller) list {
	success := "{\n" +
		"    \"code\": \"0\",\n" +
		"    \"message\": \"成功\",\n" +
		"    \"data\": " + dataExample(m.Response) + ",\n" +
		"    \"language\": \"\",\n" +
		"    \"time\": 1540483321460\n" +
		"}"
	return list{
		response(m, c, "Successful Response", "OK", 200, success),
		response(m, c, "Bad Request", "Bad Request", 400,
			"{\n    \"code\": \"400\",\n    \"message\": \"请求参数错误\",\n    \"data\": null\n}"),
		response(m, c, "Not Found", "Not Found", 404,
			"{\n    \"code\": \"404\",\n    \"message\": \"资源不存在\",\n    \"data\": null\n}"),
		response(m, c, "Rate Limit Exceeded", "Too Many Requests", 429,
			"{\n    \"error\": \"rateLimited\",\n    \"message\": \"Rate limit exceeded. Please retry later\"\n}"),
	}
}

// dataExample 对应 Java buildResponseDataExample:取第一个响应模型里名为 data 的字段。
func dataExample(models []*model.Model) string {
	if len(models) == 0 || models[0] == nil || len(models[0].Parameters) == 0 {
		return "{}"
	}
	for _, p := range models[0].Parameters {
		if p != nil && p.Name == "data" {
			return paramValueJSON(p)
		}
	}
	return "{}"
}

func paramValueJSON(p *model.Parameter) string {
	if len(p.Parameters) > 0 {
		if isArray(p) {
			return "[" + objectFromParams(p.Parameters) + "]"
		}
		return objectFromParams(p.Parameters)
	}
	if isArray(p) {
		return "[" + jsonDefault(p) + "]"
	}
	return jsonDefault(p)
}

func isArray(p *model.Parameter) bool {
	if p.ModelType != nil && *p.ModelType == model.ModelArray {
		return true
	}
	dt := strings.ToLower(p.DataType)
	return strings.Contains(dt, "[]") || strings.Contains(dt, "list<") || strings.Contains(dt, "set<")
}

// objectFromParams 对应 Java buildJsonObjectFromParams(结尾缩进是 "\n  }",照搬)。
func objectFromParams(params []*model.Parameter) string {
	var b strings.Builder
	b.WriteString("{\n")
	first := true
	for _, p := range params {
		if p == nil || p.Hidden {
			continue
		}
		if !first {
			b.WriteString(",\n")
		}
		first = false
		b.WriteString(`    "`)
		b.WriteString(p.Name)
		b.WriteString(`": `)
		switch {
		case isArray(p):
			if len(p.Parameters) > 0 {
				b.WriteString("[" + objectFromParams(p.Parameters) + "]")
			} else {
				b.WriteString("[" + jsonDefault(p) + "]")
			}
		case len(p.Parameters) > 0:
			b.WriteString(objectFromParams(p.Parameters))
		default:
			b.WriteString(paramValueJSON(p))
		}
	}
	b.WriteString("\n  }")
	return b.String()
}

// ---- 与 Java convertObjectToJsonString 相同的手写 JSON 序列化 ----

type kv struct {
	k string
	v any
}

// obj 是保序对象(对应 Java LinkedHashMap)。
type obj []kv

type list []any

// escape 对应 Java escapeJsonString:只转义 \ " \n \r \t,其余字符(含中文、其它控制字符)原样输出。
var escaper = strings.NewReplacer(`\`, `\\`, `"`, `\"`, "\n", `\n`, "\r", `\r`, "\t", `\t`)

func escape(s string) string { return escaper.Replace(s) }

func writeValue(b *strings.Builder, v any) {
	switch x := v.(type) {
	case nil:
		b.WriteString("null")
	case string:
		b.WriteByte('"')
		b.WriteString(escape(x))
		b.WriteByte('"')
	case int:
		b.WriteString(strconv.Itoa(x))
	case bool:
		b.WriteString(strconv.FormatBool(x))
	case list:
		b.WriteByte('[')
		for i, e := range x {
			if i > 0 {
				b.WriteByte(',')
			}
			writeValue(b, e)
		}
		b.WriteByte(']')
	case obj:
		b.WriteByte('{')
		for i, e := range x {
			if i > 0 {
				b.WriteByte(',')
			}
			b.WriteByte('"')
			b.WriteString(escape(e.k))
			b.WriteString(`":`)
			writeValue(b, e.v)
		}
		b.WriteByte('}')
	}
}
