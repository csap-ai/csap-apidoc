package apidiff

import (
	"fmt"
	"sort"
	"strings"
)

// Level 是变更的严重程度。
type Level string

const (
	Breaking Level = "breaking" // 破坏性变更:老客户端可能直接出错
	Warning  Level = "warning"  // 需要留意:可能影响部分客户端
	Info     Level = "info"     // 兼容的变化:新增、文字说明等
)

func (l Level) rank() int {
	switch l {
	case Breaking:
		return 3
	case Warning:
		return 2
	}
	return 1
}

// ParseLevel 解析 "breaking" / "warning" / "info"。
func ParseLevel(s string) (Level, error) {
	switch Level(strings.ToLower(s)) {
	case Breaking, Warning, Info:
		return Level(strings.ToLower(s)), nil
	}
	return "", fmt.Errorf("未知级别 %q(可选 breaking / warning / info)", s)
}

// Change 是一条差异。Kind 的取值与判定规则见 docs/design/1.4.0-api-diff.md。
type Change struct {
	Level    Level  `json:"level"`
	Kind     string `json:"kind"`
	Endpoint string `json:"endpoint,omitempty"` // 接口标识:Java 是 类全名.方法名,Go 是 分组.接口名
	Where    string `json:"where,omitempty"`    // request / response / header
	Field    string `json:"field,omitempty"`    // 字段路径、请求头名或枚举名
	Message  string `json:"message"`
	Old      string `json:"old,omitempty"`
	New      string `json:"new,omitempty"`
	Ignored  bool   `json:"ignored,omitempty"` // 命中忽略清单
}

// Diff 比较两份文档,返回按接口和字段排序的差异。
func Diff(oldDoc, newDoc *Doc) []Change {
	var out []Change
	add := func(c Change) { out = append(out, c) }

	// 1. 按接口标识配对;只在一边出现的,再按"方法+路径"相同配成改名
	pairs := map[string][2]*Endpoint{}
	var removed, added []*Endpoint
	for id, o := range oldDoc.Endpoints {
		if n, ok := newDoc.Endpoints[id]; ok {
			pairs[id] = [2]*Endpoint{o, n}
		} else {
			removed = append(removed, o)
		}
	}
	for id, n := range newDoc.Endpoints {
		if _, ok := oldDoc.Endpoints[id]; !ok {
			added = append(added, n)
		}
	}
	sort.Slice(removed, func(i, j int) bool { return removed[i].ID < removed[j].ID })
	sort.Slice(added, func(i, j int) bool { return added[i].ID < added[j].ID })
	sig := func(e *Endpoint) string { return strings.Join(e.Methods, ",") + " " + strings.Join(e.Paths, ",") }
	bySig := map[string]*Endpoint{}
	for _, n := range added {
		if _, dup := bySig[sig(n)]; !dup {
			bySig[sig(n)] = n
		}
	}
	taken := map[string]bool{}
	for _, o := range removed {
		if n, ok := bySig[sig(o)]; ok && !taken[n.ID] {
			taken[n.ID] = true
			pairs[o.ID] = [2]*Endpoint{o, n}
			add(Change{Level: Info, Kind: "endpoint.renamed", Endpoint: o.ID, Message: fmt.Sprintf("接口标识 %s 改名为 %s(方法和路径不变)", o.ID, n.ID), Old: o.ID, New: n.ID})
			continue
		}
		add(Change{Level: Breaking, Kind: "endpoint.removed", Endpoint: o.ID, Message: "接口被删除(或被隐藏):" + sig(o), Old: sig(o)})
	}
	for _, n := range added {
		if !taken[n.ID] {
			add(Change{Level: Info, Kind: "endpoint.added", Endpoint: n.ID, Message: "新增接口:" + sig(n), New: sig(n)})
		}
	}

	// 2. 配对的接口逐项比较
	ids := make([]string, 0, len(pairs))
	for id := range pairs {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	for _, id := range ids {
		o, n := pairs[id][0], pairs[id][1]
		out = append(out, diffEndpoint(id, o, n)...)
	}

	// 3. 枚举
	out = append(out, diffEnums(oldDoc.Enums, newDoc.Enums)...)

	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Level.rank() != out[j].Level.rank() {
			return out[i].Level.rank() > out[j].Level.rank()
		}
		if out[i].Endpoint != out[j].Endpoint {
			return out[i].Endpoint < out[j].Endpoint
		}
		return out[i].Field < out[j].Field
	})
	return out
}

func diffEndpoint(id string, o, n *Endpoint) []Change {
	var out []Change
	add := func(l Level, kind, where, field, msg, ov, nv string) {
		out = append(out, Change{Level: l, Kind: kind, Endpoint: id, Where: where, Field: field, Message: msg, Old: ov, New: nv})
	}
	for _, m := range minus(o.Methods, n.Methods) {
		add(Breaking, "endpoint.method.removed", "", m, "不再支持 HTTP 方法 "+m, m, "")
	}
	for _, m := range minus(n.Methods, o.Methods) {
		add(Info, "endpoint.method.added", "", m, "新增 HTTP 方法 "+m, "", m)
	}
	for _, p := range minus(o.Paths, n.Paths) {
		add(Breaking, "endpoint.path.removed", "", p, "路径不再可用:"+p+"(现为 "+strings.Join(n.Paths, ",")+")", p, strings.Join(n.Paths, ","))
	}
	for _, p := range minus(n.Paths, o.Paths) {
		add(Info, "endpoint.path.added", "", p, "新增路径 "+p, "", p)
	}
	if o.ParamType != n.ParamType && o.ParamType != "" && n.ParamType != "" {
		add(Breaking, "endpoint.paramtype.changed", "", "", fmt.Sprintf("参数位置 %s 变为 %s", o.ParamType, n.ParamType), o.ParamType, n.ParamType)
	}
	if n.Status == "DEPRECATED" && o.Status != "DEPRECATED" {
		add(Warning, "endpoint.deprecated", "", "", "接口被标记为废弃", o.Status, n.Status)
	}
	if o.Description != n.Description {
		add(Info, "endpoint.description.changed", "", "", "接口说明变化", o.Description, n.Description)
	}
	// 请求头
	for _, name := range sortedKeys(n.Headers) {
		req := n.Headers[name]
		oreq, existed := o.Headers[name]
		switch {
		case !existed && req:
			add(Breaking, "header.required.added", "header", name, "新增必填请求头 "+name, "", name)
		case !existed:
			add(Info, "header.added", "header", name, "新增可选请求头 "+name, "", name)
		case !oreq && req:
			add(Breaking, "header.required.added", "header", name, "请求头 "+name+" 由可选变为必填", "optional", "required")
		case oreq && !req:
			add(Info, "header.required.removed", "header", name, "请求头 "+name+" 由必填变为可选", "required", "optional")
		}
	}
	for _, name := range sortedKeys(o.Headers) {
		if _, ok := n.Headers[name]; !ok {
			add(Info, "header.removed", "header", name, "请求头 "+name+" 不再声明", name, "")
		}
	}
	// 字段
	for _, c := range diffFields("request", o.Request, n.Request) {
		c.Endpoint = id
		out = append(out, c)
	}
	for _, c := range diffFields("response", o.Response, n.Response) {
		c.Endpoint = id
		out = append(out, c)
	}
	return out
}

func diffFields(where string, o, n map[string]Field) []Change {
	var out []Change
	req := where == "request"
	add := func(l Level, kind, path, msg, ov, nv string) {
		out = append(out, Change{Level: l, Kind: kind, Where: where, Field: path, Message: msg, Old: ov, New: nv})
	}
	for _, p := range sortedKeys(o) {
		of := o[p]
		nf, ok := n[p]
		if !ok {
			if req {
				add(Warning, "field.removed", p, "请求字段被删除:"+p, of.DataType, "")
			} else {
				add(Breaking, "field.removed", p, "返回字段被删除:"+p, of.DataType, "")
			}
			continue
		}
		if of.DataType != nf.DataType {
			add(Breaking, "field.type.changed", p, fmt.Sprintf("字段类型 %s 变为 %s", of.DataType, nf.DataType), of.DataType, nf.DataType)
		} else if of.ModelType != nf.ModelType && of.ModelType != "" && nf.ModelType != "" {
			add(Breaking, "field.type.changed", p, fmt.Sprintf("字段结构 %s 变为 %s", of.ModelType, nf.ModelType), of.ModelType, nf.ModelType)
		}
		if req {
			switch {
			case !of.Required && nf.Required:
				add(Breaking, "field.required.added", p, "请求字段由可选变为必填", "optional", "required")
			case of.Required && !nf.Required:
				add(Info, "field.required.removed", p, "请求字段由必填变为可选", "required", "optional")
			}
			if of.ParamType != nf.ParamType && of.ParamType != "" && nf.ParamType != "" {
				add(Breaking, "field.paramtype.changed", p, fmt.Sprintf("参数位置 %s 变为 %s", of.ParamType, nf.ParamType), of.ParamType, nf.ParamType)
			}
			if added := minus(nf.Validate, of.Validate); len(added) > 0 {
				add(Warning, "field.validate.changed", p, "校验规则变严或被修改", strings.Join(of.Validate, ";"), strings.Join(nf.Validate, ";"))
			} else if len(minus(of.Validate, nf.Validate)) > 0 {
				add(Info, "field.validate.relaxed", p, "校验规则被放宽", strings.Join(of.Validate, ";"), strings.Join(nf.Validate, ";"))
			}
			if of.Length > 0 && nf.Length > 0 && nf.Length < of.Length {
				add(Warning, "field.length.decreased", p, fmt.Sprintf("长度上限 %d 缩小为 %d", of.Length, nf.Length), fmt.Sprint(of.Length), fmt.Sprint(nf.Length))
			}
		} else if of.Required && !nf.Required {
			add(Warning, "field.required.removed", p, "返回字段不再保证一定存在", "required", "optional")
		}
		if of.Description != nf.Description {
			add(Info, "field.description.changed", p, "字段说明变化", of.Description, nf.Description)
		}
	}
	for _, p := range sortedKeys(n) {
		if _, ok := o[p]; ok {
			continue
		}
		nf := n[p]
		if req && nf.Required {
			add(Breaking, "field.required.added", p, "新增必填请求字段:"+p, "", nf.DataType)
		} else {
			add(Info, "field.added", p, fmt.Sprintf("新增%s字段:%s", map[bool]string{true: "请求", false: "返回"}[req], p), "", nf.DataType)
		}
	}
	return out
}

func diffEnums(o, n map[string][]string) []Change {
	var out []Change
	for _, name := range sortedKeys(o) {
		nv, ok := n[name]
		if !ok {
			out = append(out, Change{Level: Breaking, Kind: "enum.removed", Where: "enum", Field: name, Message: "枚举被删除:" + name, Old: strings.Join(o[name], ",")})
			continue
		}
		if rm := minus(o[name], nv); len(rm) > 0 {
			out = append(out, Change{Level: Breaking, Kind: "enum.value.removed", Where: "enum", Field: name, Message: "枚举 " + name + " 减少了取值:" + strings.Join(rm, ","), Old: strings.Join(rm, ",")})
		}
		if ad := minus(nv, o[name]); len(ad) > 0 {
			out = append(out, Change{Level: Info, Kind: "enum.value.added", Where: "enum", Field: name, Message: "枚举 " + name + " 新增取值:" + strings.Join(ad, ","), New: strings.Join(ad, ",")})
		}
	}
	for _, name := range sortedKeys(n) {
		if _, ok := o[name]; !ok {
			out = append(out, Change{Level: Info, Kind: "enum.added", Where: "enum", Field: name, Message: "新增枚举:" + name})
		}
	}
	return out
}

// minus 返回在 a 里、不在 b 里的元素(保持 a 的顺序)。
func minus(a, b []string) []string {
	set := map[string]bool{}
	for _, s := range b {
		set[s] = true
	}
	var out []string
	for _, s := range a {
		if !set[s] {
			out = append(out, s)
		}
	}
	return out
}

func sortedKeys[V any](m map[string]V) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}
