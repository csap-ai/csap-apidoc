package devtools

import (
	"encoding/json"
	"sort"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

// ---------- 输出:字段 ----------

// field 对应 Java devtools 的 Field(/devtools/getFields 的返回)。
// 前端用 name、dataType、value、childrenField;其余字段照 Java 输出,方便排查。
type field struct {
	ChildrenField  []*field `json:"childrenField"`
	DataType       string   `json:"dataType"`
	DefaultValue   string   `json:"defaultValue"`
	Description    string   `json:"description"`
	Example        string   `json:"example"`
	FinalClassName string   `json:"finalClassName,omitempty"`
	Hidden         bool     `json:"hidden"`
	ID             string   `json:"id"`
	Key            string   `json:"key"`
	ModelType      string   `json:"modelType"`
	Name           string   `json:"name"`
	PackageName    string   `json:"packageName"`
	Required       bool     `json:"required"`
	SimpleName     string   `json:"simpleName"`
	Value          string   `json:"value"`
}

// toFields 把文档里的参数树转成 Java Field 树。key 为从顶层开始的点号路径(与 Java 一致),
// exclude 里的路径(及其下级)去掉。
func toFields(ps []*model.Parameter, prefix string, exclude map[string]bool) []*field {
	out := []*field{}
	for _, p := range ps {
		key := prefix + p.Name
		if exclude[key] {
			continue
		}
		mt := model.ModelBaseData
		if p.ModelType != nil {
			mt = *p.ModelType
		}
		pkg := p.DataType
		if p.LongDataType != nil && *p.LongDataType != "" {
			pkg = *p.LongDataType
		}
		f := &field{
			ChildrenField: toFields(p.Parameters, key+".", exclude),
			DataType:      p.DataType,
			DefaultValue:  p.DefaultValue,
			Description:   p.Description,
			Example:       p.Example,
			ID:            key,
			Key:           key,
			ModelType:     mt,
			Name:          p.Name,
			PackageName:   pkg,
			Required:      p.Required,
			SimpleName:    p.DataType,
			Value:         p.Value,
		}
		if p.Children != nil {
			f.FinalClassName = p.Children.Name
		}
		if f.Value == "" {
			f.Value = p.DataType // 与 Java 一致:没有说明时显示类型名
		}
		out = append(out, f)
	}
	return out
}

// ---------- 输入:前端提交的接口参数 ----------

// methodModel 对应 Java devtools MethodModel(addRequestParam / addResponseParam / hiddenMethod 的请求体)。
type methodModel struct {
	ClassName       string         `json:"className"`
	MethodName      string         `json:"methodName"`
	MethodParamName string         `json:"methodParamName"`
	Hidden          *bool          `json:"hidden"`
	RequestParams   []requestParam `json:"requestParams"`
	ReturnType      *responseParam `json:"returnType"`
}

// requestParam 对应 Java RequestParam:一个方法参数(Go 里是一个请求模型)及选中的字段。
type requestParam struct {
	PackageName     string        `json:"packageName"`
	ModelType       string        `json:"modelType"`
	MethodParamName string        `json:"methodParamName"`
	Fields          []*inputField `json:"fields"`
}

// responseParam 对应 Java ResponseParam:返回类型及选中的字段。
type responseParam struct {
	PackageName string        `json:"packageName"`
	ModelType   string        `json:"modelType"`
	AppendName  string        `json:"appendName"`
	Fields      []*inputField `json:"fields"`
}

// inputField 对应 Java RequestField / ResponseField。下级字段在 parameters 里。
type inputField struct {
	Name          string         `json:"name"`
	Required      *bool          `json:"required"`
	ParamType     string         `json:"paramType"`
	Validate      []validateItem `json:"validate"`
	ClickValidate bool           `json:"clickValidate"`
	Parameters    []*inputField  `json:"parameters"`
}

// validateItem 对应 Java Validate.ConstraintValidatorField 写进规则的部分。
type validateItem struct {
	Code    flexString `json:"code"`
	Descr   string     `json:"descr"`
	Level   int        `json:"level"`
	Message string     `json:"message"`
	Pattern string     `json:"pattern"`
	Type    string     `json:"type"`
}

// flexString 兼容 "code": 1001 与 "code": "1001"。
type flexString string

func (f *flexString) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err == nil {
		*f = flexString(s)
		return nil
	}
	var n json.Number
	if err := json.Unmarshal(b, &n); err != nil {
		if string(b) == "null" {
			return nil
		}
		return err
	}
	*f = flexString(n.String())
	return nil
}

func (v validateItem) rule() strategy.ValidateRule {
	pattern := v.Pattern
	if !strings.EqualFold(v.Type, "NotNull") && !strings.EqualFold(v.Type, "NotEmpty") {
		pattern = validate.GoPattern(pattern) // 保存成 RE2 也认、Java 也认的写法见 GoPattern;这里统一成 RE2
	}
	return strategy.ValidateRule{Type: v.Type, Pattern: pattern, Code: string(v.Code), Message: v.Message, Descr: v.Descr, Level: v.Level}
}

func toItems(rs []strategy.ValidateRule) []validateItem {
	out := make([]validateItem, 0, len(rs))
	for _, r := range rs {
		out = append(out, validateItem{Code: flexString(r.Code), Descr: r.Descr, Level: r.Level, Message: r.Message,
			Pattern: validate.JavaPattern(r.Pattern), Type: r.Type})
	}
	return out
}

// paramData 对应 Java ParamGroupMethodProperty.ParamDataValidate(规则文件里一个字段的配置)。
// hidden、example 是 Go 版新增。
type paramData struct {
	Description string         `json:"description,omitempty"`
	Example     string         `json:"example,omitempty"`
	Hidden      *bool          `json:"hidden,omitempty"`
	Include     *bool          `json:"include,omitempty"`
	ParamType   string         `json:"paramType,omitempty"`
	Required    bool           `json:"required"`
	Validate    []validateItem `json:"validate"`
}

func fromFieldRule(fr strategy.FieldRule) paramData {
	pd := paramData{Description: fr.Description, Example: fr.Example, Hidden: fr.Hidden, Include: fr.Include,
		ParamType: fr.ParamType, Validate: toItems(fr.Validate)}
	if fr.Required != nil {
		pd.Required = *fr.Required
	}
	return pd
}

// methodProperty 对应 Java ParamGroupMethodProperty(writeFieldMethod / write 的请求体)。
type methodProperty struct {
	Request  map[string]paramInput `json:"request"`
	Response map[string]paramInput `json:"response"`
}

type paramInput struct {
	Description string         `json:"description"`
	Example     string         `json:"example"`
	Hidden      *bool          `json:"hidden"`
	Include     *bool          `json:"include"`
	ParamType   string         `json:"paramType"`
	Required    *bool          `json:"required"`
	Validate    []validateItem `json:"validate"`
}

func (p methodProperty) toRules() strategy.MethodRules {
	conv := func(in map[string]paramInput) map[string]strategy.FieldRule {
		if len(in) == 0 {
			return nil
		}
		out := make(map[string]strategy.FieldRule, len(in))
		for k, v := range in {
			fr := strategy.FieldRule{Description: v.Description, Example: v.Example, Hidden: v.Hidden, Include: v.Include,
				ParamType: strings.ToUpper(v.ParamType), Required: v.Required}
			for _, it := range v.Validate {
				fr.Validate = append(fr.Validate, it.rule())
			}
			out[k] = fr
		}
		return out
	}
	return strategy.MethodRules{Request: conv(p.Request), Response: conv(p.Response)}
}

// ---------- 规则计算 ----------

func ptr(b bool) *bool { return &b }

// applyMethodModel 按 Java ApidocDevtools.addMethodParam 的语义把前端提交合并进已有规则:
//
//   - 请求:对每个提交的参数(methodParamName),删掉该参数下原有的全部规则,换成提交的字段;
//     其他参数的规则不动。必填、位置取提交的值;校验规则只有在前端打开过"校验"弹窗(clickValidate)
//     时才用提交的,否则保留原来的。
//   - 返回:整个替换成提交的字段,全部 include: true。
//
// 与 Java 不同的地方:Java 删除旧规则时用的是 startsWith(参数名),参数名是另一个参数的前缀时
// (如 id 与 idCard)会误删,这里按 "<参数名>." 匹配;字段原有的说明、示例、hidden 保留(Java 会丢掉)。
func applyMethodModel(old strategy.MethodRules, m methodModel) strategy.MethodRules {
	rules := strategy.Clone(old)
	if len(m.RequestParams) > 0 {
		req := map[string]strategy.FieldRule{}
		for k, v := range rules.Request {
			req[k] = v
		}
		for _, rp := range m.RequestParams {
			name := rp.MethodParamName
			if name == "" {
				name = m.MethodParamName
			}
			if name == "" {
				continue
			}
			for k := range req {
				if k == name || strings.HasPrefix(k, name+".") {
					delete(req, k)
				}
			}
			flattenRequest(rp.Fields, name+".", old.Request, req)
		}
		rules.Request = req
	}
	if m.ReturnType != nil {
		prefix := m.ReturnType.AppendName
		if prefix != "" && !strings.HasSuffix(prefix, ".") {
			prefix += "."
		}
		resp := map[string]strategy.FieldRule{}
		flattenResponse(m.ReturnType.Fields, prefix, old.Response, resp)
		rules.Response = resp
	}
	return rules
}

func flattenRequest(fs []*inputField, prefix string, old, out map[string]strategy.FieldRule) {
	for _, f := range fs {
		if f == nil || f.Name == "" {
			continue
		}
		key := prefix + f.Name
		prev := old[key]
		fr := strategy.FieldRule{Description: prev.Description, Example: prev.Example, Hidden: prev.Hidden,
			Required: ptr(f.Required != nil && *f.Required), ParamType: strings.ToUpper(f.ParamType), Validate: prev.Validate}
		if fr.ParamType == "" || fr.ParamType == model.ParamDefault {
			fr.ParamType = prev.ParamType // DEFAULT 是前端的占位值,表示未指定
		}
		if f.ClickValidate {
			fr.Validate = nil
			for _, v := range f.Validate {
				fr.Validate = append(fr.Validate, v.rule())
			}
			sort.SliceStable(fr.Validate, func(i, j int) bool { return fr.Validate[i].Level < fr.Validate[j].Level })
		}
		out[key] = fr
		flattenRequest(f.Parameters, key+".", old, out)
	}
}

func flattenResponse(fs []*inputField, prefix string, old, out map[string]strategy.FieldRule) {
	for _, f := range fs {
		if f == nil || f.Name == "" {
			continue
		}
		key := prefix + f.Name
		prev := old[key]
		fr := strategy.FieldRule{Description: prev.Description, Example: prev.Example, Hidden: prev.Hidden, Include: ptr(true)}
		if f.Required != nil && *f.Required {
			fr.Required = ptr(true)
		}
		out[key] = fr
		flattenResponse(f.Parameters, key+".", old, out)
	}
}

// initialRules 按接口的完整字段生成初始规则:请求字段全部列出(必填、位置取代码里的声明),返回字段全部放行。
func initialRules(m *model.Method) strategy.MethodRules {
	if m == nil {
		return strategy.MethodRules{}
	}
	rules := strategy.MethodRules{}
	for i, md := range m.Request {
		name := ""
		if md.MethodParamName != nil {
			name = *md.MethodParamName
		} else if i < len(m.ParamNames) {
			name = m.ParamNames[i]
		}
		if name == "" {
			continue
		}
		if rules.Request == nil {
			rules.Request = map[string]strategy.FieldRule{}
		}
		walkParams(md.Parameters, name+".", func(key string, p *model.Parameter) {
			rules.Request[key] = strategy.FieldRule{Required: ptr(p.Required), ParamType: p.ParamType}
		})
	}
	if len(m.Response) > 0 {
		rules.Response = map[string]strategy.FieldRule{}
		walkParams(m.Response[0].Parameters, "", func(key string, p *model.Parameter) {
			rules.Response[key] = strategy.FieldRule{Include: ptr(true)}
		})
		if len(rules.Response) == 0 {
			rules.Response = nil
		}
	}
	return rules
}

func walkParams(ps []*model.Parameter, prefix string, fn func(key string, p *model.Parameter)) {
	for _, p := range ps {
		key := prefix + p.Name
		fn(key, p)
		walkParams(p.Parameters, key+".", fn)
	}
}
