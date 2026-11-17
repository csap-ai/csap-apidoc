package filter

import (
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// Compile 按路径白名单编译掩码(ModePath)。
//
// include 是要输出的字段路径(点号分隔,数组不写下标);上级路径自动放行。
// 某个路径没有任何下级出现在 include 里时,它的值原样输出。
// mapPaths 是值为 map 的路径:该节点的所有键都保留,下级路径作用于每个值。
// 每一段同时放行驼峰和下划线两种写法(与 Java 的 response-type 别名一致)。
func Compile(include []string, mapPaths map[string]bool) *Node {
	root := &Node{Fields: map[string]*Node{}}
	for _, p := range include {
		if p == "" {
			continue
		}
		n := root
		segs := strings.Split(p, ".")
		for i, seg := range segs {
			prefix := strings.Join(segs[:i+1], ".")
			n = n.child(seg)
			if mapPaths[prefix] && n.MapValues == nil {
				n.MapValues = &Node{Fields: map[string]*Node{}}
			}
			if n.MapValues != nil && i < len(segs)-1 {
				n = n.MapValues // 下一段在 map 值里继续
			}
		}
	}
	// 根节点始终是白名单(即使为空也不能变成"原样输出")
	seen := map[*Node]*Node{}
	for k, c := range root.Fields {
		if f, ok := seen[c]; ok {
			root.Fields[k] = f
			continue
		}
		f := finalize(c)
		seen[c] = f
		root.Fields[k] = f
	}
	return root.Seal()
}

// child 返回(必要时创建)键为 seg 的子节点,并为另一种命名写法建别名。
func (n *Node) child(seg string) *Node {
	if c := n.Fields[seg]; c != nil {
		return c
	}
	c := &Node{Fields: map[string]*Node{}}
	for _, k := range aliases(seg) {
		if existing := n.Fields[k]; existing != nil {
			c = existing
			break
		}
	}
	for _, k := range aliases(seg) {
		n.Fields[k] = c
	}
	return c
}

func aliases(seg string) []string {
	out := []string{seg}
	if u := strategy.ToUnderline(seg); u != seg {
		out = append(out, u)
	}
	if c := strategy.ToCamel(seg); c != seg {
		out = append(out, c)
	}
	return out
}

// finalize 把没有下级的节点变成 nil(原样保留整个值)。Compile 之后、使用之前调用。
func finalize(n *Node) *Node {
	if n == nil {
		return nil
	}
	if n.MapValues != nil {
		n.MapValues = finalize(n.MapValues)
		if n.MapValues == nil {
			return nil
		}
		return n
	}
	if len(n.Fields) == 0 && !n.Open {
		return nil
	}
	seen := map[*Node]*Node{}
	for k, c := range n.Fields {
		if f, ok := seen[c]; ok {
			n.Fields[k] = f
			continue
		}
		f := finalize(c)
		seen[c] = f
		n.Fields[k] = f
	}
	return n
}
