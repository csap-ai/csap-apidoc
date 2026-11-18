package fieldlib

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"strings"

	yamlv3 "gopkg.in/yaml.v3"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// FormatVersion 是模板文件的格式版本。
const FormatVersion = 1

// ReservedKey 是项目模板在规则存储里的保留键(数据库、Redis);YAML 存储用文件 field-template/api-field-template.yaml。
const ReservedKey = "@templates"

// Template 是一个字段模板。写了的项才会在套用时覆盖字段的对应项。
type Template struct {
	Name        string                  `json:"name" yaml:"name"`
	Title       string                  `json:"title" yaml:"title"`
	Category    string                  `json:"category,omitempty" yaml:"category,omitempty"`
	Description string                  `json:"description,omitempty" yaml:"description,omitempty"`
	Example     string                  `json:"example,omitempty" yaml:"example,omitempty"`
	Required    *bool                   `json:"required,omitempty" yaml:"required,omitempty"`
	Validate    []strategy.ValidateRule `json:"validate,omitempty" yaml:"validate,omitempty"`
}

// File 是模板文件的内容(YAML 或 JSON,结构相同)。
type File struct {
	Version   int        `json:"version" yaml:"version"`
	Templates []Template `json:"templates" yaml:"templates"`
}

// 来源标记。
const (
	SourceBuiltin = "builtin"
	SourceProject = "project"
)

// Entry 是合并后的一条模板,带来源。
type Entry struct {
	Template
	Source string `json:"source"`
}

//go:embed field-templates.yaml
var builtinYAML []byte

var nameRE = regexp.MustCompile(`^[a-z0-9][a-z0-9_-]{0,63}$`)

// ValidName 判断模板名是否合法:小写字母、数字、- _,最长 64。
func ValidName(name string) bool { return nameRE.MatchString(name) }

// ErrNewerVersion 表示文件的格式版本比本库认识的新,整个文件被忽略。
var ErrNewerVersion = errors.New("fieldlib: 模板文件的格式版本比当前库新,已忽略")

// Parse 解析模板文件(YAML 或 JSON)。名字不合法、缺 title 的模板跳过并记入 warnings;同名的后者覆盖前者并警告;
// 版本高于 FormatVersion 时返回 ErrNewerVersion 和空文件。
func Parse(b []byte) (File, []string, error) {
	var f File
	if err := yamlv3.Unmarshal(b, &f); err != nil {
		return File{}, nil, fmt.Errorf("fieldlib: 解析模板文件: %w", err)
	}
	if f.Version > FormatVersion {
		return File{}, nil, ErrNewerVersion
	}
	var warns []string
	idx := map[string]int{}
	out := make([]Template, 0, len(f.Templates))
	for _, t := range f.Templates {
		switch {
		case !ValidName(t.Name):
			warns = append(warns, fmt.Sprintf("模板名 %q 不合法(小写字母、数字、- _,最长 64),已跳过", t.Name))
			continue
		case t.Title == "":
			warns = append(warns, fmt.Sprintf("模板 %s 没有 title,已跳过", t.Name))
			continue
		}
		if i, dup := idx[t.Name]; dup {
			warns = append(warns, fmt.Sprintf("模板 %s 重复,后者覆盖前者", t.Name))
			out[i] = t
			continue
		}
		idx[t.Name] = len(out)
		out = append(out, t)
	}
	f.Templates = out
	if f.Version == 0 {
		f.Version = FormatVersion
	}
	return f, warns, nil
}

// Builtin 返回内置模板。
func Builtin() []Template {
	f, _, err := Parse(builtinYAML)
	if err != nil {
		panic("fieldlib: 内置模板文件损坏: " + err.Error())
	}
	return f.Templates
}

// Merge 合并内置与项目模板:同名时项目模板覆盖内置。结果内置在前、项目在后,各自保持文件里的顺序。
func Merge(builtin, project []Template) []Entry {
	proj := map[string]Template{}
	for _, t := range project {
		proj[t.Name] = t
	}
	var out []Entry
	for _, t := range builtin {
		if _, over := proj[t.Name]; !over {
			out = append(out, Entry{t, SourceBuiltin})
		}
	}
	for _, t := range project {
		out = append(out, Entry{t, SourceProject})
	}
	return out
}

// ---- 套用 ----

// Change 是套用时会变的一项。
type Change struct {
	Item string `json:"item"` // description / example / required / validate / template
	Old  string `json:"old"`
	New  string `json:"new"`
}

func validateText(vs []strategy.ValidateRule) string {
	parts := make([]string, 0, len(vs))
	for _, v := range vs {
		parts = append(parts, fmt.Sprintf("%s(%s) code=%s level=%d %s", v.Type, v.Pattern, v.Code, v.Level, v.Message))
	}
	return strings.Join(parts, "; ")
}

func boolText(b *bool) string {
	if b == nil {
		return ""
	}
	return fmt.Sprint(*b)
}

// Preview 列出把模板 t 套用到字段 r 会改哪些项(不改 r)。模板里没写的项不动;validate 是整体替换。
func Preview(r strategy.FieldRule, t Template) []Change {
	var out []Change
	if t.Description != "" && t.Description != r.Description {
		out = append(out, Change{"description", r.Description, t.Description})
	}
	if t.Example != "" && t.Example != r.Example {
		out = append(out, Change{"example", r.Example, t.Example})
	}
	if t.Required != nil && (r.Required == nil || *r.Required != *t.Required) {
		out = append(out, Change{"required", boolText(r.Required), boolText(t.Required)})
	}
	if len(t.Validate) > 0 && !sameValidate(r.Validate, t.Validate) {
		out = append(out, Change{"validate", validateText(r.Validate), validateText(t.Validate)})
	}
	if r.Template != t.Name {
		out = append(out, Change{"template", r.Template, t.Name})
	}
	return out
}

func sameValidate(a, b []strategy.ValidateRule) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// Apply 把模板套用到字段:模板里写了的 description / example / required 各自覆盖,validate 整体替换(不是追加,
// 重复套用不会叠加),没写的项不动;字段记下 template: 模板名。返回新的字段规则,不改入参。
func Apply(r strategy.FieldRule, t Template) strategy.FieldRule {
	if t.Description != "" {
		r.Description = t.Description
	}
	if t.Example != "" {
		r.Example = t.Example
	}
	if t.Required != nil {
		b := *t.Required
		r.Required = &b
	}
	if len(t.Validate) > 0 {
		r.Validate = append([]strategy.ValidateRule(nil), t.Validate...)
	}
	r.Template = t.Name
	return r
}

// Diverges 判断已套用模板的字段与模板当前内容是否不一致(不算 template 字段本身),用于"与模板不一致"的提示。
func Diverges(r strategy.FieldRule, t Template) bool {
	for _, c := range Preview(r, t) {
		if c.Item != "template" {
			return true
		}
	}
	return false
}

// FromRule 把一个字段当前的说明、示例、必填、校验规则存成模板("另存为模板")。
func FromRule(name, title string, r strategy.FieldRule) (Template, error) {
	if !ValidName(name) {
		return Template{}, fmt.Errorf("模板名 %q 不合法(小写字母、数字、- _,最长 64)", name)
	}
	if title == "" {
		return Template{}, errors.New("title 必填")
	}
	t := Template{Name: name, Title: title, Description: r.Description, Example: r.Example,
		Validate: append([]strategy.ValidateRule(nil), r.Validate...)}
	if r.Required != nil {
		b := *r.Required
		t.Required = &b
	}
	return t, nil
}

// ---- 项目模板的存取(经规则存储的保留键) ----

// LoadProject 读取项目模板。存储里还没有时返回空文件。
func LoadProject(ctx context.Context, rs strategy.RawStore) (File, []string, error) {
	raw, ok, err := rs.LoadRaw(ctx, ReservedKey)
	if err != nil {
		return File{}, nil, err
	}
	if !ok || strings.TrimSpace(raw) == "" {
		return File{Version: FormatVersion}, nil, nil
	}
	return Parse([]byte(raw))
}

// SaveProject 保存项目模板(与规则一样走版本号、修改记录和多实例同步)。没有模板时删除存储里的条目。
func SaveProject(ctx context.Context, rs strategy.RawStore, f File, by string) (int64, error) {
	f.Version = FormatVersion
	if len(f.Templates) == 0 {
		return rs.SaveRaw(ctx, ReservedKey, "", by)
	}
	b, err := json.Marshal(f)
	if err != nil {
		return 0, err
	}
	return rs.SaveRaw(ctx, ReservedKey, string(b), by)
}

// Upsert 新增或覆盖一个项目模板。
func (f *File) Upsert(t Template) error {
	if !ValidName(t.Name) {
		return fmt.Errorf("模板名 %q 不合法(小写字母、数字、- _,最长 64)", t.Name)
	}
	if t.Title == "" {
		return errors.New("title 必填")
	}
	for i := range f.Templates {
		if f.Templates[i].Name == t.Name {
			f.Templates[i] = t
			return nil
		}
	}
	f.Templates = append(f.Templates, t)
	return nil
}

// Remove 删除一个项目模板,返回是否存在。
func (f *File) Remove(name string) bool {
	for i := range f.Templates {
		if f.Templates[i].Name == name {
			f.Templates = append(f.Templates[:i], f.Templates[i+1:]...)
			return true
		}
	}
	return false
}
