package apidiff

import (
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"time"
)

// SnapshotFormat 是快照文件的格式标记。
const SnapshotFormat = "csap-apidoc-snapshot/1"

// Doc 是从 /csap/apidoc 抽出来、用于比较的精简文档:去掉了随机 key、时间戳、示例值等每次导出都可能变的内容,
// 所以快照文件内容稳定,适合提交进 Git。
type Doc struct {
	Format    string               `json:"format"`
	Source    string               `json:"source,omitempty"`
	CreatedAt string               `json:"createdAt,omitempty"`
	Title     string               `json:"title,omitempty"`
	Version   string               `json:"version,omitempty"`
	Endpoints map[string]*Endpoint `json:"endpoints"` // 键为接口标识(ID)
	Enums     map[string][]string  `json:"enums,omitempty"`
}

// Endpoint 是一个接口。
type Endpoint struct {
	ID          string           `json:"id"`
	Description string           `json:"description,omitempty"`
	Status      string           `json:"status,omitempty"`
	Methods     []string         `json:"methods"`
	Paths       []string         `json:"paths"`
	ParamType   string           `json:"paramType,omitempty"`
	Headers     map[string]bool  `json:"headers,omitempty"`  // 请求头名 → 是否必填
	Request     map[string]Field `json:"request,omitempty"`  // 键为字段路径:参数名.字段...
	Response    map[string]Field `json:"response,omitempty"` // 键为字段路径:字段...
}

// Field 是一个字段。
type Field struct {
	DataType    string   `json:"dataType,omitempty"`
	ModelType   string   `json:"modelType,omitempty"`
	ParamType   string   `json:"paramType,omitempty"`
	Required    bool     `json:"required,omitempty"`
	Length      int      `json:"length,omitempty"`
	Description string   `json:"description,omitempty"`
	Validate    []string `json:"validate,omitempty"` // 每条规则的规范化文本 type|pattern|code|level
}

// ---- 解析 /csap/apidoc 的 JSON(只取需要的字段,多余字段和形状略有出入都不影响) ----

type rawEnvelope struct {
	Data json.RawMessage `json:"data"`
}

type rawDoc struct {
	ApiInfo struct {
		Title   string `json:"title"`
		Version string `json:"version"`
	} `json:"apiInfo"`
	ApiList     []rawController             `json:"apiList"`
	DocEnumList []rawDocEnum                `json:"docEnumList"`
	EnumList    map[string][]map[string]any `json:"enumList"`
}

type rawDocEnum struct {
	Name     string `json:"name"`
	Value    string `json:"value"`
	EnumList []struct {
		Code string `json:"code"`
		Name string `json:"name"`
	} `json:"enumList"`
}

type rawController struct {
	Hidden       bool        `json:"hidden"`
	HiddenMethod []string    `json:"hiddenMethod"`
	MethodList   []rawMethod `json:"methodList"`
}

type rawMethod struct {
	Key           string      `json:"key"`
	Name          string      `json:"name"`
	Description   string      `json:"description"`
	Value         string      `json:"value"`
	Methods       []string    `json:"methods"`
	Paths         []string    `json:"paths"`
	ApiPath       []string    `json:"apiPath"`
	ParamType     string      `json:"paramType"`
	Status        string      `json:"status"`
	Hidden        bool        `json:"hidden"`
	MethodHeaders []rawHeader `json:"methodHeaders"`
	Request       []*rawModel `json:"request"`
	Response      []*rawModel `json:"response"`
}

type rawHeader struct {
	Key      string `json:"key"`
	Required bool   `json:"required"`
	Hidden   bool   `json:"hidden"`
}

type rawModel struct {
	Name            string      `json:"name"`
	ModelType       string      `json:"modelType"`
	MethodParamName *string     `json:"methodParamName"`
	Parameters      []*rawParam `json:"parameters"`
}

type rawParam struct {
	Name        string        `json:"name"`
	DataType    string        `json:"dataType"`
	ModelType   *string       `json:"modelType"`
	ParamType   string        `json:"paramType"`
	Required    bool          `json:"required"`
	Hidden      bool          `json:"hidden"`
	Length      int           `json:"length"`
	Description string        `json:"description"`
	Value       string        `json:"value"`
	Validate    []rawValidate `json:"validate"`
	Children    *rawModel     `json:"children"`
	Parameters  []*rawParam   `json:"parameters"`
}

type rawValidate struct {
	Type    string `json:"type"`
	Pattern string `json:"pattern"`
	Code    string `json:"code"`
	Level   int    `json:"level"`
}

// Parse 解析一份文档:支持 /csap/apidoc 的完整返回(带外层 data)、只有 data 的内容,以及 Snapshot 生成的快照文件。
func Parse(b []byte, source string) (*Doc, error) {
	var probe struct {
		Format string `json:"format"`
	}
	if err := json.Unmarshal(b, &probe); err != nil {
		return nil, fmt.Errorf("%s: 不是有效的 JSON: %w", source, err)
	}
	if probe.Format != "" {
		if probe.Format != SnapshotFormat {
			return nil, fmt.Errorf("%s: 不支持的快照格式 %q(支持 %s)", source, probe.Format, SnapshotFormat)
		}
		var d Doc
		if err := json.Unmarshal(b, &d); err != nil {
			return nil, fmt.Errorf("%s: %w", source, err)
		}
		if d.Endpoints == nil {
			d.Endpoints = map[string]*Endpoint{}
		}
		return &d, nil
	}
	data := b
	var env rawEnvelope
	if json.Unmarshal(b, &env) == nil && len(env.Data) > 0 && string(env.Data) != "null" {
		data = env.Data
	}
	var rd rawDoc
	if err := json.Unmarshal(data, &rd); err != nil {
		return nil, fmt.Errorf("%s: 不是 /csap/apidoc 的返回: %w", source, err)
	}
	if rd.ApiList == nil {
		return nil, fmt.Errorf("%s: 没有 apiList,不像是 /csap/apidoc 的返回", source)
	}
	return reduce(&rd, source), nil
}

func reduce(rd *rawDoc, source string) *Doc {
	d := &Doc{Format: SnapshotFormat, Source: source, Title: rd.ApiInfo.Title, Version: rd.ApiInfo.Version,
		Endpoints: map[string]*Endpoint{}, Enums: map[string][]string{}}
	for _, c := range rd.ApiList {
		hiddenNames := map[string]bool{}
		for _, h := range c.HiddenMethod {
			hiddenNames[h] = true
		}
		for _, m := range c.MethodList {
			if c.Hidden || m.Hidden || hiddenNames[m.Name] {
				continue // 隐藏的接口按"不存在"处理:新隐藏等同于下线
			}
			e := &Endpoint{
				Description: firstNonEmpty(m.Description, m.Value),
				Status:      m.Status,
				Methods:     sortedUpper(m.Methods),
				Paths:       fullPaths(m.ApiPath, m.Paths),
				ParamType:   m.ParamType,
				Request:     map[string]Field{},
				Response:    map[string]Field{},
			}
			for _, h := range m.MethodHeaders {
				if h.Hidden || h.Key == "" {
					continue
				}
				if e.Headers == nil {
					e.Headers = map[string]bool{}
				}
				e.Headers[h.Key] = h.Required
			}
			for _, md := range m.Request {
				root := ""
				if md != nil && md.MethodParamName != nil {
					root = *md.MethodParamName
				}
				flatten(md, root, e.Request, 0)
			}
			for _, md := range m.Response {
				flatten(md, "", e.Response, 0)
			}
			id := m.Key
			if id == "" {
				id = strings.Join(e.Methods, ",") + " " + strings.Join(e.Paths, ",")
			}
			e.ID = uniqueID(d.Endpoints, id)
			d.Endpoints[e.ID] = e
		}
	}
	for _, en := range rd.DocEnumList {
		name := firstNonEmpty(en.Name, en.Value)
		for _, c := range en.EnumList {
			d.Enums[name] = append(d.Enums[name], firstNonEmpty(c.Code, c.Name))
		}
		if _, ok := d.Enums[name]; !ok {
			d.Enums[name] = nil
		}
	}
	for name, items := range rd.EnumList {
		for _, it := range items {
			for _, k := range []string{"code", "name", "value"} {
				if v, ok := it[k]; ok && fmt.Sprint(v) != "" {
					d.Enums[name] = append(d.Enums[name], fmt.Sprint(v))
					break
				}
			}
		}
	}
	for k, v := range d.Enums {
		d.Enums[k] = dedupSorted(v)
	}
	if len(d.Enums) == 0 {
		d.Enums = nil
	}
	return d
}

const maxDepth = 16

func flatten(m *rawModel, prefix string, out map[string]Field, depth int) {
	if m == nil || depth > maxDepth {
		return
	}
	for _, p := range m.Parameters {
		flattenParam(p, prefix, out, depth)
	}
}

func flattenParam(p *rawParam, prefix string, out map[string]Field, depth int) {
	if p == nil || p.Hidden || p.Name == "" || depth > maxDepth {
		return
	}
	path := p.Name
	if prefix != "" {
		path = prefix + "." + p.Name
	}
	f := Field{DataType: p.DataType, ParamType: p.ParamType, Required: p.Required, Length: p.Length,
		Description: firstNonEmpty(p.Description, p.Value)}
	if p.ModelType != nil {
		f.ModelType = *p.ModelType
	}
	for _, v := range p.Validate {
		f.Validate = append(f.Validate, fmt.Sprintf("%s|%s|%s|%d", v.Type, v.Pattern, v.Code, v.Level))
	}
	sort.Strings(f.Validate)
	out[path] = f
	for _, c := range p.Parameters {
		flattenParam(c, path, out, depth+1)
	}
	if p.Children != nil {
		for _, c := range p.Children.Parameters {
			flattenParam(c, path, out, depth+1)
		}
	}
}

func uniqueID(m map[string]*Endpoint, id string) string {
	if _, ok := m[id]; !ok {
		return id
	}
	for i := 2; ; i++ {
		c := fmt.Sprintf("%s#%d", id, i)
		if _, ok := m[c]; !ok {
			return c
		}
	}
}

func fullPaths(base, sub []string) []string {
	if len(base) == 0 {
		base = []string{""}
	}
	if len(sub) == 0 {
		sub = []string{""}
	}
	var out []string
	for _, b := range base {
		for _, s := range sub {
			p := "/" + strings.Trim(strings.Trim(b, "/")+"/"+strings.Trim(s, "/"), "/")
			p = strings.ReplaceAll(p, "//", "/")
			out = append(out, p)
		}
	}
	return dedupSorted(out)
}

func sortedUpper(in []string) []string {
	out := make([]string, 0, len(in))
	for _, s := range in {
		out = append(out, strings.ToUpper(s))
	}
	return dedupSorted(out)
}

func dedupSorted(in []string) []string {
	m := map[string]bool{}
	out := []string{}
	for _, s := range in {
		if !m[s] {
			m[s] = true
			out = append(out, s)
		}
	}
	sort.Strings(out)
	return out
}

func firstNonEmpty(ss ...string) string {
	for _, s := range ss {
		if s != "" {
			return s
		}
	}
	return ""
}

// Snapshot 把文档序列化成快照文件内容(缩进的 JSON,键有序,内容稳定)。createdAt 为空时用当前时间。
func (d *Doc) Snapshot(createdAt time.Time) ([]byte, error) {
	c := *d
	c.Format = SnapshotFormat
	if createdAt.IsZero() {
		createdAt = time.Now()
	}
	c.CreatedAt = createdAt.UTC().Format(time.RFC3339)
	b, err := json.MarshalIndent(&c, "", "  ")
	if err != nil {
		return nil, err
	}
	return append(b, '\n'), nil
}
