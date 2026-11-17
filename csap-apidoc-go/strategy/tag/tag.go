package tag

import (
	"reflect"
	"strconv"
	"strings"
)

// 支持的结构体标签(对应 Java @ApiModelProperty 的属性):
//
//	doc:"标题"              value,字段的简短说明
//	doc_desc:"详细说明"     description;不写时与 doc 相同
//	doc_example:"1"         example
//	doc_default:"10"        defaultValue
//	doc_required:"true"     required;也识别 binding:"required" / validate:"required"
//	doc_hidden:"true"       文档中不显示(对应 hidden)
//	doc_force:"true"        始终显示/输出,不受规则限制(对应 forceReq / forceRep)
//	doc_type:"Long"         覆盖 dataType 的显示名
//	doc_position:"1"        position
//	in:"query|path|header|body|form"  请求参数位置,优先于 gin 的 uri/form/header/json 推断

// Meta 是从一个结构体字段上解析出的文档信息。
type Meta struct {
	Name         string // 序列化名(json 标签),"-" 表示不参与序列化
	Value        string
	Description  string
	Example      string
	DefaultValue string
	Required     bool
	Hidden       bool
	Force        bool
	DataType     string
	Position     int
	In           string // 显式 in 标签,已转为大写的 ParamType
	URI          string // gin uri 标签
	Form         string // gin form 标签
	Header       string // gin header 标签
	HasJSON      bool   // 是否显式写了 json 名
}

// Parse 解析字段标签。
func Parse(f reflect.StructField) Meta {
	m := Meta{Name: f.Name}
	if j, ok := f.Tag.Lookup("json"); ok {
		name := strings.Split(j, ",")[0]
		if name == "-" && !strings.HasPrefix(j, "-,") {
			m.Name = "-"
		} else if name != "" {
			m.Name = name
			m.HasJSON = true
		}
	}
	m.Value = f.Tag.Get("doc")
	m.Description = f.Tag.Get("doc_desc")
	if m.Description == "" {
		m.Description = m.Value
	}
	m.Example = f.Tag.Get("doc_example")
	m.DefaultValue = f.Tag.Get("doc_default")
	m.Required = isTrue(f.Tag.Get("doc_required")) ||
		hasRule(f.Tag.Get("binding"), "required") || hasRule(f.Tag.Get("validate"), "required")
	m.Hidden = isTrue(f.Tag.Get("doc_hidden"))
	m.Force = isTrue(f.Tag.Get("doc_force"))
	m.DataType = f.Tag.Get("doc_type")
	if p, err := strconv.Atoi(f.Tag.Get("doc_position")); err == nil {
		m.Position = p
	}
	if in := f.Tag.Get("in"); in != "" {
		m.In = strings.ToUpper(in)
	}
	m.URI = firstName(f.Tag.Get("uri"))
	m.Form = firstName(f.Tag.Get("form"))
	m.Header = firstName(f.Tag.Get("header"))
	return m
}

func firstName(t string) string {
	n := strings.Split(t, ",")[0]
	if n == "-" {
		return ""
	}
	return n
}

func isTrue(s string) bool {
	b, _ := strconv.ParseBool(s)
	return b
}

func hasRule(t, rule string) bool {
	for _, r := range strings.Split(t, ",") {
		if strings.TrimSpace(r) == rule {
			return true
		}
	}
	return false
}
