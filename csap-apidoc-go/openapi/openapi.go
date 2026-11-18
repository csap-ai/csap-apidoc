package openapi

import (
	"sort"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// Build 把文档模型转成 OpenAPI 3.1 文档(JSON 对象)。
// 返回的是运行时快照的投影:隐藏字段、规则过滤后的字段与 /csap/apidoc 一致。
func Build(doc *model.Response) map[string]any {
	info := map[string]any{"title": "API", "version": "1.0"}
	if a := doc.ApiInfo; a != nil {
		info = map[string]any{"title": a.Title, "version": a.Version}
		if a.Description != "" {
			info["description"] = a.Description
		}
		if a.License != "" {
			lic := map[string]any{"name": a.License}
			if a.LicenseURL != "" {
				lic["url"] = a.LicenseURL
			}
			info["license"] = lic
		}
		if c := a.Contact; c != nil && (c.Name != "" || c.Email != "" || c.URL != "") {
			info["contact"] = map[string]any{"name": c.Name, "email": c.Email, "url": c.URL}
		}
	}
	paths := map[string]any{}
	tags := []any{}
	for _, c := range doc.ApiList {
		tag := c.Value
		t := map[string]any{"name": tag}
		if c.Description != "" {
			t["description"] = c.Description
		}
		tags = append(tags, t)
		for _, m := range c.MethodList {
			p := methodPath(m)
			item, _ := paths[p].(map[string]any)
			if item == nil {
				item = map[string]any{}
				paths[p] = item
			}
			for _, verb := range m.Methods {
				item[strings.ToLower(verb)] = operation(m, tag, verb)
			}
		}
	}
	return map[string]any{
		"openapi": "3.1.0",
		"info":    info,
		"tags":    tags,
		"paths":   paths,
	}
}

func operation(m *model.Method, tag, verb string) map[string]any {
	op := map[string]any{
		"operationId": m.Key,
		"summary":     m.Value,
		"tags":        []string{tag},
	}
	desc := m.Description
	if m.StatusNote != "" {
		if desc != "" {
			desc += "\n\n"
		}
		desc += m.StatusNote
	}
	if desc != "" {
		op["description"] = desc
	}
	if m.Status == model.StatusDeprecated {
		op["deprecated"] = true
	}
	if m.Status == model.StatusFinish || m.Status == model.StatusDeprecated {
		op["x-csap-status"] = m.Status // DEFAULT(开发中)不输出
	}
	var params []any
	body := map[string]any{}  // 属性名 -> schema
	var bodyRequired []string //
	bodyMedia := "application/json"
	var wholeBody map[string]any // Body(x) 整体声明时
	for _, md := range m.Request {
		if md.ModelType == model.ModelBaseData {
			for _, p := range md.Parameters {
				params = appendParam(params, p, verb)
			}
			continue
		}
		var props []*model.Parameter
		for _, p := range md.Parameters {
			switch pt := effectiveIn(p.ParamType, verb); pt {
			case "path", "query", "header":
				params = appendParam(params, p, verb)
			default:
				if pt == "form" {
					bodyMedia = "application/x-www-form-urlencoded"
				}
				props = append(props, p)
			}
		}
		if md.ModelType == model.ModelArray {
			wholeBody = map[string]any{"type": "array", "items": objectSchema(props)}
			continue
		}
		for _, p := range props {
			body[p.Name] = schema(p)
			if p.Required {
				bodyRequired = append(bodyRequired, p.Name)
			}
		}
	}
	if params != nil {
		op["parameters"] = params
	}
	var bodySchema map[string]any
	switch {
	case wholeBody != nil:
		bodySchema = wholeBody
	case len(body) > 0:
		bodySchema = map[string]any{"type": "object", "properties": body}
		if len(bodyRequired) > 0 {
			bodySchema["required"] = bodyRequired
		}
	}
	if bodySchema != nil {
		op["requestBody"] = map[string]any{
			"required": true,
			"content":  map[string]any{bodyMedia: map[string]any{"schema": bodySchema}},
		}
	}
	resp := map[string]any{"description": "OK"}
	if len(m.Response) > 0 {
		md := m.Response[0]
		var s map[string]any
		switch md.ModelType {
		case model.ModelArray:
			s = map[string]any{"type": "array", "items": objectSchema(md.Parameters)}
		case model.ModelBaseData:
			s = primitive(md.Name)
		default:
			s = objectSchema(md.Parameters)
		}
		resp["content"] = map[string]any{"application/json": map[string]any{"schema": s}}
	}
	op["responses"] = map[string]any{"200": resp}
	return op
}

func effectiveIn(paramType, verb string) string {
	switch paramType {
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
	if verb == "GET" || verb == "DELETE" || verb == "HEAD" {
		return "query"
	}
	return "body"
}

func appendParam(params []any, p *model.Parameter, verb string) []any {
	in := effectiveIn(p.ParamType, verb)
	if in == "body" || in == "form" {
		in = "query"
	}
	q := map[string]any{
		"name":     p.Name,
		"in":       in,
		"required": p.Required || in == "path",
		"schema":   schema(p),
	}
	if d := desc(p); d != "" {
		q["description"] = d
	}
	if p.Example != "" {
		q["example"] = p.Example
	}
	return append(params, q)
}

func desc(p *model.Parameter) string {
	switch {
	case p.Value != "" && p.Description != "" && p.Value != p.Description:
		return p.Value + ":" + p.Description
	case p.Description != "":
		return p.Description
	}
	return p.Value
}

func objectSchema(ps []*model.Parameter) map[string]any {
	props := map[string]any{}
	var required []string
	for _, p := range ps {
		props[p.Name] = schema(p)
		if p.Required {
			required = append(required, p.Name)
		}
	}
	s := map[string]any{"type": "object", "properties": props}
	if len(required) > 0 {
		sort.Strings(required)
		s["required"] = required
	}
	return s
}

func schema(p *model.Parameter) map[string]any {
	var s map[string]any
	mt := ""
	if p.ModelType != nil {
		mt = *p.ModelType
	}
	switch {
	case p.Children != nil && (mt == model.ModelArray || strings.HasPrefix(p.DataType, "List<")):
		s = map[string]any{"type": "array", "items": objectSchema(p.Children.Parameters)}
	case p.Children != nil:
		s = objectSchema(p.Children.Parameters)
	default:
		s = primitive(p.DataType)
	}
	if d := desc(p); d != "" {
		s["description"] = d
	}
	if p.Example != "" {
		s["examples"] = []string{p.Example}
	}
	if p.DefaultValue != "" {
		s["default"] = p.DefaultValue
	}
	for _, v := range p.Validate {
		if v.Type == "Pattern" && v.Pattern != "" && !strings.HasPrefix(v.Pattern, "PATTERN_") {
			s["pattern"] = v.Pattern
		}
	}
	return s
}

// primitive 把 Java 风格的类型名转成 JSON Schema。
func primitive(dataType string) map[string]any {
	t := dataType
	if i := strings.LastIndex(t, "."); i >= 0 && !strings.Contains(t, "<") {
		t = t[i+1:]
	}
	switch t {
	case "String", "string", "Number":
		return map[string]any{"type": "string"}
	case "Integer", "int", "int32", "Short", "Byte":
		return map[string]any{"type": "integer", "format": "int32"}
	case "Long", "int64":
		return map[string]any{"type": "integer", "format": "int64"}
	case "Double", "float64", "BigDecimal":
		return map[string]any{"type": "number", "format": "double"}
	case "Float", "float32":
		return map[string]any{"type": "number", "format": "float"}
	case "Boolean", "bool":
		return map[string]any{"type": "boolean"}
	case "Date", "LocalDateTime", "Time":
		return map[string]any{"type": "string", "format": "date-time"}
	case "LocalDate":
		return map[string]any{"type": "string", "format": "date"}
	}
	if strings.HasPrefix(t, "List<") && strings.HasSuffix(t, ">") {
		return map[string]any{"type": "array", "items": primitive(t[5 : len(t)-1])}
	}
	if strings.HasPrefix(t, "Map<") {
		return map[string]any{"type": "object"}
	}
	return map[string]any{}
}

func methodPath(m *model.Method) string {
	p := formatPath(first(m.ApiPath)) + formatPath(first(m.Paths))
	if p == "" {
		return "/"
	}
	return p
}

func first(ss []string) string {
	if len(ss) > 0 {
		return ss[0]
	}
	return ""
}

func formatPath(p string) string {
	if p == "" || strings.HasPrefix(p, "/") {
		return p
	}
	return "/" + p
}
