// Package typekind 是 Mock 与 JSON Schema 共用的类型归一和文档字段读取,规则见 docs/design/1.4.0-mock-schema.md 2.5.2。
package typekind

import (
	"regexp"
	"strconv"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// Kind 是 dataType 归一后的类型。
type Kind string

const (
	String   Kind = "string"
	Integer  Kind = "integer"
	Number   Kind = "number"
	Boolean  Kind = "boolean"
	Date     Kind = "date"
	DateTime Kind = "datetime"
	Time     Kind = "time"
	Object   Kind = "object"
	Array    Kind = "array"
)

// Of 把 dataType 归一成 kind;数组同时返回元素类型(缺省 "string")。
// 去掉泛型和包名、转小写,按表自上而下命中即止。
func Of(dataType string) (Kind, string) {
	t := strings.TrimSpace(dataType)
	if strings.HasSuffix(t, "[]") {
		return Array, t[:len(t)-2]
	}
	lt, gt := strings.Index(t, "<"), strings.LastIndex(t, ">")
	raw := t
	if lt > 0 {
		raw = t[:lt]
	}
	if i := strings.LastIndex(raw, "."); i >= 0 {
		raw = raw[i+1:]
	}
	raw = strings.ToLower(raw)
	switch {
	case strings.HasSuffix(raw, "list"), strings.HasSuffix(raw, "set"),
		raw == "collection", raw == "iterable", raw == "array":
		elem := "string"
		if lt > 0 && gt > lt {
			elem = t[lt+1 : gt]
		}
		return Array, elem
	}
	switch raw {
	case "int", "integer", "int32", "short", "byte", "long", "int64", "biginteger", "atomicinteger", "atomiclong":
		return Integer, ""
	case "double", "float", "float32", "float64", "bigdecimal", "number":
		return Number, ""
	case "boolean", "bool":
		return Boolean, ""
	case "localdate":
		return Date, ""
	case "localdatetime", "date", "offsetdatetime", "zoneddatetime", "instant", "timestamp", "time":
		return DateTime, ""
	case "localtime":
		return Time, ""
	case "object", "jsonnode", "jsonobject":
		return Object, ""
	}
	if strings.HasSuffix(raw, "map") {
		return Object, ""
	}
	return String, ""
}

// Raw 返回去掉泛型和包名、转小写的类型名(JSON Schema 区分 int32 / int64、double / float 时用)。
func Raw(dataType string) string {
	t := strings.TrimSpace(dataType)
	if i := strings.Index(t, "<"); i >= 0 {
		t = t[:i]
	}
	if i := strings.LastIndex(t, "."); i >= 0 {
		t = t[i+1:]
	}
	return strings.ToLower(t)
}

var intRE = regexp.MustCompile(`^[+-]?\d+$`)

// Coerce 把示例、默认值、候选值按类型转换;失败返回 ok=false。integer 返回 int64(放不下的整数当失败),
// number 返回 float64(非有限当失败),boolean 返回 bool,其余原样字符串。
func Coerce(s string, k Kind) (any, bool) {
	switch k {
	case Integer:
		s = strings.TrimSpace(s)
		if !intRE.MatchString(s) {
			return nil, false
		}
		n, err := strconv.ParseInt(s, 10, 64)
		if err != nil {
			return nil, false
		}
		return n, true
	case Number:
		f, err := strconv.ParseFloat(strings.TrimSpace(s), 64)
		if err != nil || f != f || f > 1.7976931348623157e308 || f < -1.7976931348623157e308 {
			return nil, false
		}
		return f, true
	case Boolean:
		switch strings.ToLower(strings.TrimSpace(s)) {
		case "true":
			return true, true
		case "false":
			return false, true
		}
		return nil, false
	case String, Date, DateTime, Time:
		return s, true
	}
	return nil, false
}

// ParseRange 解析 Length / Size 的 pattern:"n" 恰好 n,"最小,最大","n," 无上限(hi 返回 -1)。
func ParseRange(p string) (lo, hi int, ok bool) {
	parts := strings.SplitN(p, ",", 2)
	l, err := strconv.Atoi(strings.TrimSpace(parts[0]))
	if err != nil {
		return 0, 0, false
	}
	if len(parts) == 1 {
		return l, l, true
	}
	if strings.TrimSpace(parts[1]) == "" {
		return l, -1, true
	}
	h, err := strconv.Atoi(strings.TrimSpace(parts[1]))
	if err != nil {
		return 0, 0, false
	}
	return l, h, true
}

// Children 返回字段的子字段:优先 children.parameters,没有则取字段自己的 parameters。
func Children(p *model.Parameter) []*model.Parameter {
	if p.Children != nil && len(p.Children.Parameters) > 0 {
		return p.Children.Parameters
	}
	return p.Parameters
}

// Validates 返回字段上指定类型(忽略大小写)的校验规则。
func Validates(p *model.Parameter, typ string) []model.ValidateAttribute {
	var out []model.ValidateAttribute
	for _, v := range p.Validate {
		if strings.EqualFold(v.Type, typ) {
			out = append(out, v)
		}
	}
	return out
}

// Hidden 判断字段是否应跳过:隐藏或没有名字。
func Hidden(p *model.Parameter) bool { return p == nil || p.Hidden || p.Name == "" }
