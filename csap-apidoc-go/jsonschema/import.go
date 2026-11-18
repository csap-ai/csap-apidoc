package jsonschema

import (
	"errors"
	"fmt"
	"sort"
	"strconv"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/typekind"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
)

// Unmatched 是 Schema 里文档没有的字段。导入只更新文档里已存在字段的规则,不创建字段。
type Unmatched struct {
	Path   string `json:"path"`
	Reason string `json:"reason"`
}

// Skipped 是不支持的关键字,不影响同一字段的其他关键字。
type Skipped struct {
	Path    string `json:"path"`
	Keyword string `json:"keyword"`
	Reason  string `json:"reason"`
}

// ImportResult 是导入预览(也是 apply 之后的返回)。Rules 的键是规则键,值里只含 Schema 提供了的项。
type ImportResult struct {
	Key       string                        `json:"key"`
	Kind      string                        `json:"kind"`
	Applied   bool                          `json:"applied"`
	Rules     map[string]strategy.FieldRule `json:"rules"`
	YAML      string                        `json:"yaml"`
	Matched   int                           `json:"matched"`
	Unmatched []Unmatched                   `json:"unmatched"`
	Skipped   []Skipped                     `json:"skipped"`
}

type target struct {
	p       *model.Parameter
	ruleKey string
}

// handled 是认识的关键字(转换或结构);ignored 是静默忽略的元数据。其余的进 skipped。
var handled = map[string]bool{
	"type": true, "properties": true, "items": true, "required": true,
	"description": true, "examples": true, "example": true, "enum": true,
	"minLength": true, "maxLength": true, "minItems": true, "maxItems": true,
	"minimum": true, "maximum": true, "pattern": true,
	"x-csap-validate": true, "x-csap-key": true, "x-csap-mock": true,
}

var ignored = map[string]bool{"title": true, "$schema": true, "$id": true, "$comment": true, "$defs": true}

// Import 把 Schema 转成字段规则的预览(不写入)。kind 为 request 或 response。
// request:schema 是导出形状(顶层 properties 只含 path/query/header/form/body 分区)时按分区匹配,
// 否则必须给 section,schema 当作该分区的对象 Schema。response:schema 就是返回的 Schema。
func Import(m *model.Method, kind, section string, schema map[string]any) (*ImportResult, error) {
	res := &ImportResult{Key: m.Key, Kind: kind, Rules: map[string]strategy.FieldRule{}, Unmatched: []Unmatched{}, Skipped: []Skipped{}}
	im := &importer{res: res}
	switch kind {
	case "request":
		verb := verbOf(m)
		secs := map[string]map[string]target{}
		keys := map[string]map[string]target{}
		for _, loc := range sections {
			secs[loc], keys[loc] = map[string]target{}, map[string]target{}
		}
		for _, md := range m.Request {
			if md == nil {
				continue
			}
			root := ""
			if md.MethodParamName != nil {
				root = *md.MethodParamName
			}
			for _, p := range md.Parameters {
				if typekind.Hidden(p) {
					continue
				}
				loc := Location(p, verb)
				indexParam(p, "", root, true, secs[loc], keys[loc])
			}
		}
		if props := partitions(schema); props != nil {
			for _, loc := range sections {
				sub, ok := props[loc]
				if !ok {
					continue
				}
				im.section(loc, sub, secs[loc], keys[loc])
			}
		} else {
			if !contains(sections, section) {
				return nil, errors.New("schema 不是导出形状(path/query/header/form/body 分区),必须给 section(path / query / header / form / body)")
			}
			im.section(section, schema, secs[section], keys[section])
		}
	case "response":
		idx, keys := map[string]target{}, map[string]target{}
		if len(m.Response) > 0 && m.Response[0] != nil {
			for _, p := range m.Response[0].Parameters {
				indexParam(p, "", "", false, idx, keys)
			}
		}
		im.section("response", schema, idx, keys)
	default:
		return nil, errors.New("kind 只能是 request 或 response")
	}
	mr := strategy.MethodRules{}
	if kind == "request" {
		mr.Request = res.Rules
	} else {
		mr.Response = res.Rules
	}
	if len(res.Rules) > 0 {
		y, err := yaml.Render(m.Key, mr)
		if err != nil {
			return nil, err
		}
		res.YAML = string(y)
	}
	return res, nil
}

// Merge 把导入的规则合并进接口现有的规则:同一字段整体替换 Schema 提供了的 description / example / validate / mock / required,
// 没提供的项保持原样。不改入参。
func Merge(existing strategy.MethodRules, kind string, imported map[string]strategy.FieldRule) strategy.MethodRules {
	out := strategy.Clone(existing)
	dst := out.Response
	if kind == "request" {
		dst = out.Request
	}
	if dst == nil {
		dst = map[string]strategy.FieldRule{}
	}
	for k, in := range imported {
		cur := dst[k]
		if in.Description != "" {
			cur.Description = in.Description
		}
		if in.Required != nil {
			cur.Required = in.Required
		}
		if in.Example != "" {
			cur.Example = in.Example
		}
		if in.Validate != nil {
			cur.Validate = in.Validate
		}
		if in.Mock != nil {
			cur.Mock = in.Mock
		}
		dst[k] = cur
	}
	if kind == "request" {
		out.Request = dst
	} else {
		out.Response = dst
	}
	return out
}

func contains(ss []string, s string) bool {
	for _, x := range ss {
		if x == s {
			return true
		}
	}
	return false
}

// partitions 判断 schema 是不是导出的请求形状:顶层 properties 非空且键全是分区名。
func partitions(schema map[string]any) map[string]any {
	props, _ := schema["properties"].(map[string]any)
	if len(props) == 0 {
		return nil
	}
	for k := range props {
		if !contains(sections, k) {
			return nil
		}
	}
	return props
}

// indexParam 把字段树展开成 点号路径 → 字段(数组元素透明),同时按 keyName 建索引。
func indexParam(p *model.Parameter, prefix, root string, request bool, idx, keys map[string]target) {
	if typekind.Hidden(p) {
		return
	}
	path := p.Name
	if prefix != "" {
		path = prefix + "." + p.Name
	}
	rk := p.KeyName
	if rk == "" {
		if request && root != "" {
			rk = root + "." + path
		} else {
			rk = path
		}
	}
	t := target{p, rk}
	idx[path] = t
	if p.KeyName != "" {
		keys[p.KeyName] = t
	}
	for _, c := range typekind.Children(p) {
		indexParam(c, path, root, request, idx, keys)
	}
}

type importer struct{ res *ImportResult }

func (im *importer) section(name string, schema any, idx, keys map[string]target) {
	obj, _ := schema.(map[string]any)
	if obj == nil {
		return
	}
	// 分区本身是数组(整个 body 是数组):字段在 items 里
	if t, _ := obj["type"].(string); t == "array" {
		if items, ok := obj["items"].(map[string]any); ok {
			obj = items
		}
	}
	im.walk(name, obj, nil, idx, keys)
}

func (im *importer) walk(section string, obj map[string]any, path []string, idx, keys map[string]target) {
	props, _ := obj["properties"].(map[string]any)
	required := map[string]bool{}
	if rs, ok := obj["required"].([]any); ok {
		for _, r := range rs {
			if s, ok := r.(string); ok {
				required[s] = true
			}
		}
	}
	names := make([]string, 0, len(props))
	for n := range props {
		names = append(names, n)
	}
	sort.Strings(names)
	for _, name := range names {
		sub, ok := props[name].(map[string]any)
		if !ok {
			continue
		}
		fp := append(append([]string(nil), path...), name)
		dotted := strings.Join(fp, ".")
		shown := section + "." + dotted
		rule, skipped := ruleFrom(sub, required[name])
		for _, kw := range skipped {
			im.res.Skipped = append(im.res.Skipped, Skipped{Path: shown, Keyword: kw, Reason: "不支持的关键字"})
		}
		var tg target
		found := false
		if k, _ := sub["x-csap-key"].(string); k != "" {
			tg, found = keys[k]
		}
		if !found {
			tg, found = idx[dotted]
		}
		if found {
			im.res.Matched++
			if !isEmptyRule(rule) {
				im.res.Rules[tg.ruleKey] = rule
			}
		} else {
			im.res.Unmatched = append(im.res.Unmatched, Unmatched{Path: shown, Reason: "字段不在文档中"})
		}
		// 递归:对象的子字段,数组元素透明
		child := sub
		if t, _ := sub["type"].(string); t == "array" {
			if items, ok := sub["items"].(map[string]any); ok {
				child = items
			}
		}
		if _, ok := child["properties"].(map[string]any); ok {
			im.walk(section, child, fp, idx, keys)
		}
	}
}

func isEmptyRule(r strategy.FieldRule) bool {
	return r.Description == "" && r.Required == nil && r.Example == "" && r.Validate == nil && r.Mock == nil
}

// ruleFrom 把一个属性的 Schema 转成字段规则,并返回不支持的关键字。
func ruleFrom(s map[string]any, required bool) (strategy.FieldRule, []string) {
	var r strategy.FieldRule
	var skipped []string
	if d, ok := s["description"].(string); ok {
		r.Description = d
	}
	if required {
		t := true
		r.Required = &t // 只会写 true,不会因为"没列出"改成 false
	}
	if ex, ok := s["examples"].([]any); ok && len(ex) > 0 {
		r.Example = scalarText(ex[0])
	} else if ex, ok := s["example"]; ok {
		r.Example = scalarText(ex)
	}
	if xv, ok := s["x-csap-validate"].([]any); ok && len(xv) > 0 {
		for _, e := range xv { // 无损,优先
			m, _ := e.(map[string]any)
			v := strategy.ValidateRule{Type: scalarText(m["type"]), Pattern: scalarText(m["pattern"]),
				Message: scalarText(m["message"]), Code: scalarText(m["code"])}
			if lv, ok := m["level"].(float64); ok {
				v.Level = int(lv)
			}
			r.Validate = append(r.Validate, v)
		}
	} else {
		r.Validate = standardValidate(s)
	}
	if mk, ok := s["x-csap-mock"]; ok {
		r.Mock = mk
	}
	for k, v := range s {
		if handled[k] || ignored[k] || strings.HasPrefix(k, "x-") {
			continue
		}
		if k == "allOf" && onlyPatterns(v) {
			continue // 导出时多条 pattern 的附加项
		}
		skipped = append(skipped, k)
	}
	sort.Strings(skipped)
	return r, skipped
}

func onlyPatterns(v any) bool {
	list, ok := v.([]any)
	if !ok {
		return false
	}
	for _, e := range list {
		m, ok := e.(map[string]any)
		if !ok || len(m) != 1 {
			return false
		}
		if _, ok := m["pattern"]; !ok {
			return false
		}
	}
	return true
}

// standardValidate 把标准关键字转成校验规则:新条目 code=1001、level=1、message 为空。
func standardValidate(s map[string]any) []strategy.ValidateRule {
	var out []strategy.ValidateRule
	add := func(typ, pattern string) {
		out = append(out, strategy.ValidateRule{Type: typ, Pattern: pattern, Code: "1001", Level: 1})
	}
	num := func(k string) (float64, bool) { f, ok := s[k].(float64); return f, ok }
	rng := func(loKey, hiKey string) (string, bool) {
		lo, hasLo := num(loKey)
		hi, hasHi := num(hiKey)
		switch {
		case hasLo && hasHi:
			return fmtInt(lo) + "," + fmtInt(hi), true
		case hasLo:
			return fmtInt(lo) + ",", true
		case hasHi:
			return "0," + fmtInt(hi), true
		}
		return "", false
	}
	if p, ok := rng("minLength", "maxLength"); ok {
		add("Length", p)
	}
	if p, ok := rng("minItems", "maxItems"); ok {
		add("Size", p)
	}
	if v, ok := num("minimum"); ok {
		add("Min", fmtNum(v))
	}
	if v, ok := num("maximum"); ok {
		add("Max", fmtNum(v))
	}
	if p, ok := s["pattern"].(string); ok && p != "" {
		add("Pattern", p)
	}
	if en, ok := s["enum"].([]any); ok && len(en) > 0 {
		parts := make([]string, 0, len(en))
		for _, e := range en {
			parts = append(parts, scalarText(e))
		}
		add("Enum", strings.Join(parts, ","))
	}
	return out
}

func fmtInt(f float64) string { return strconv.FormatInt(int64(f), 10) }

func fmtNum(f float64) string { return strconv.FormatFloat(f, 'f', -1, 64) }

// scalarText 把 JSON 标量转成字符串(整数值的数字不带小数点)。
func scalarText(v any) string {
	switch x := v.(type) {
	case nil:
		return ""
	case string:
		return x
	case float64:
		return fmtNum(x)
	case bool:
		return strconv.FormatBool(x)
	}
	return fmt.Sprint(v)
}
