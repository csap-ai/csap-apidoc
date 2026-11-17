package filter

import (
	"bytes"
	"encoding/json"
	"errors"
)

// Mode 是返回字段过滤的模式。
type Mode int

const (
	// ModePath 是路径白名单(默认):接口配置了任意返回规则后,只保留 include: true 的路径、
	// doc_force 字段,以及它们的上级节点,其余去掉。没有返回规则的接口不过滤。
	ModePath Mode = iota
	// ModeCsap 复刻 Java 的行为:按"类型"挂过滤器,同一类型出现在多个路径时规则合并;
	// 某类型的白名单为空时,改为"除黑名单外全部输出"。从 Java 迁移、要求返回逐字节一致时用。
	ModeCsap
)

// Node 是编译好的字段掩码。nil 表示原样保留整个值。
// 对数组,掩码作用于每个元素(多层数组同理)。
type Node struct {
	// Fields 是允许输出的键及其子掩码(子掩码为 nil 表示该键的值原样保留)。
	Fields map[string]*Node
	// Open 为 true 时,不在 Deny 里的键都输出(Java 白名单为空时的语义);Fields 只提供子掩码。
	Open bool
	// Deny 是 Open 模式下不输出的键。
	Deny map[string]bool
	// MapValues 不为 nil 时,对象被当成 map:所有键都保留,每个值套用这个掩码。
	MapValues *Node

	// keys/kids 是 Fields 的线性副本,字段不多时比 map 查找快。由 Seal 生成。
	keys [][]byte
	kids []*Node
}

const linearMax = 24

// Seal 为掩码树生成查找用的辅助结构。Compile 和 apidoc 生成的掩码已经调用过;
// 手工构造 Node 时可以调用它提速,不调用也能正确工作。Seal 之后不要再修改 Fields。
func (n *Node) Seal() *Node {
	seen := map[*Node]bool{}
	var walk func(*Node)
	walk = func(x *Node) {
		if x == nil || seen[x] {
			return
		}
		seen[x] = true
		if len(x.Fields) <= linearMax {
			x.keys, x.kids = x.keys[:0], x.kids[:0]
			for k, c := range x.Fields {
				x.keys = append(x.keys, []byte(k))
				x.kids = append(x.kids, c)
			}
		}
		for _, c := range x.Fields {
			walk(c)
		}
		walk(x.MapValues)
	}
	walk(n)
	return n
}

// allow 返回键是否输出,以及值要用的子掩码。
func (n *Node) allow(key string) (bool, *Node) { return n.allowBytes([]byte(key)) }

// allowBytes 同 allow;m[string(b)] 形式的查找不会分配内存。
func (n *Node) allowBytes(key []byte) (bool, *Node) {
	if n.MapValues != nil {
		return true, n.MapValues
	}
	if n.Open {
		if n.Deny[string(key)] {
			return false, nil
		}
		return true, n.Fields[string(key)]
	}
	if n.keys != nil || (len(n.Fields) == 0 && n.kids != nil) {
		for i, k := range n.keys {
			if len(k) == len(key) && string(k) == string(key) {
				return true, n.kids[i]
			}
		}
		return false, nil
	}
	child, ok := n.Fields[string(key)]
	return ok, child
}

var errSyntax = errors.New("filter: invalid JSON")

// Prune 按掩码裁剪 JSON,保持原有的键顺序和格式(被去掉的成员连同分隔符一起去掉)。
// mask 为 nil 时原样返回。src 不是合法 JSON 时返回错误,调用方应原样输出 src。
func Prune(src []byte, mask *Node) ([]byte, error) {
	if mask == nil {
		return src, nil
	}
	p := pruner{src: src, out: make([]byte, 0, len(src))}
	i := p.ws(0)
	p.out = append(p.out, src[:i]...)
	end, err := p.value(i, mask)
	if err != nil {
		return nil, err
	}
	p.out = append(p.out, src[end:]...) // 末尾空白(如换行)原样保留
	if j := p.ws(end); j != len(src) {
		return nil, errSyntax
	}
	return p.out, nil
}

type pruner struct {
	src []byte
	out []byte
}

func (p *pruner) ws(i int) int {
	for i < len(p.src) {
		switch p.src[i] {
		case ' ', '\t', '\n', '\r':
			i++
		default:
			return i
		}
	}
	return i
}

// value 处理从 i 开始的一个值,写入 out,返回值结束的位置。
func (p *pruner) value(i int, mask *Node) (int, error) {
	if i >= len(p.src) {
		return 0, errSyntax
	}
	switch p.src[i] {
	case '{':
		if mask == nil {
			end, err := skip(p.src, i)
			if err != nil {
				return 0, err
			}
			p.out = append(p.out, p.src[i:end]...)
			return end, nil
		}
		return p.object(i, mask)
	case '[':
		if mask == nil {
			end, err := skip(p.src, i)
			if err != nil {
				return 0, err
			}
			p.out = append(p.out, p.src[i:end]...)
			return end, nil
		}
		return p.array(i, mask)
	default:
		end, err := skip(p.src, i)
		if err != nil {
			return 0, err
		}
		p.out = append(p.out, p.src[i:end]...)
		return end, nil
	}
}

func (p *pruner) object(i int, mask *Node) (int, error) {
	src := p.src
	p.out = append(p.out, '{')
	i++
	// 保留第一个成员前的空白(缩进格式),逗号后的空白随成员一起写
	lead := p.ws(i)
	leadWS := src[i:lead]
	i = lead
	if i < len(src) && src[i] == '}' {
		p.out = append(p.out, src[i-len(leadWS):i+1]...)
		return i + 1, nil
	}
	wrote := false
	var lastWS []byte // 最后一个成员与 '}' 之间的空白
	for {
		if i >= len(src) || src[i] != '"' {
			return 0, errSyntax
		}
		keyStart := i
		keyEnd, escaped, err := scanString(src, i)
		if err != nil {
			return 0, err
		}
		key := src[keyStart+1 : keyEnd-1]
		if escaped {
			var ok bool
			if key, ok = unquote(src[keyStart:keyEnd]); !ok {
				return 0, errSyntax
			}
		}
		j := p.ws(keyEnd)
		if j >= len(src) || src[j] != ':' {
			return 0, errSyntax
		}
		vstart := p.ws(j + 1)
		keep, child := mask.allowBytes(key)
		var vend int
		if keep {
			if wrote {
				p.out = append(p.out, ',')
				p.out = append(p.out, leadWS...)
			} else {
				p.out = append(p.out, leadWS...)
			}
			p.out = append(p.out, src[keyStart:vstart]...)
			vend, err = p.value(vstart, child)
			wrote = true
		} else {
			vend, err = skip(src, vstart)
		}
		if err != nil {
			return 0, err
		}
		k := p.ws(vend)
		if k >= len(src) {
			return 0, errSyntax
		}
		if src[k] == ',' {
			i = p.ws(k + 1)
			continue
		}
		if src[k] != '}' {
			return 0, errSyntax
		}
		lastWS = src[vend:k]
		if wrote {
			p.out = append(p.out, lastWS...)
		}
		p.out = append(p.out, '}')
		return k + 1, nil
	}
}

func (p *pruner) array(i int, mask *Node) (int, error) {
	src := p.src
	p.out = append(p.out, '[')
	i++
	for {
		j := p.ws(i)
		p.out = append(p.out, src[i:j]...)
		i = j
		if i >= len(src) {
			return 0, errSyntax
		}
		if src[i] == ']' {
			p.out = append(p.out, ']')
			return i + 1, nil
		}
		end, err := p.value(i, mask)
		if err != nil {
			return 0, err
		}
		k := p.ws(end)
		p.out = append(p.out, src[end:k]...)
		if k >= len(src) {
			return 0, errSyntax
		}
		switch src[k] {
		case ',':
			p.out = append(p.out, ',')
			i = k + 1
		case ']':
			p.out = append(p.out, ']')
			return k + 1, nil
		default:
			return 0, errSyntax
		}
	}
}

// skip 返回从 i 开始的值结束的位置,不做输出。
func skip(src []byte, i int) (int, error) {
	if i >= len(src) {
		return 0, errSyntax
	}
	switch c := src[i]; c {
	case '"':
		return skipString(src, i)
	case '{', '[':
		depth := 0
		for i < len(src) {
			switch src[i] {
			case '"':
				end, err := skipString(src, i)
				if err != nil {
					return 0, err
				}
				i = end
				continue
			case '{', '[':
				depth++
			case '}', ']':
				depth--
				if depth == 0 {
					return i + 1, nil
				}
			}
			i++
		}
		return 0, errSyntax
	default:
		j := i
		for j < len(src) {
			switch src[j] {
			case ',', '}', ']', ' ', '\t', '\n', '\r':
				if j == i {
					return 0, errSyntax
				}
				return j, nil
			}
			j++
		}
		if j == i {
			return 0, errSyntax
		}
		return j, nil
	}
}

func skipString(src []byte, i int) (int, error) {
	end, _, err := scanString(src, i)
	return end, err
}

// scanString 返回字符串结束位置,以及其中是否有转义。
func scanString(src []byte, i int) (int, bool, error) {
	if src[i] != '"' {
		return 0, false, errSyntax
	}
	esc, any := false, false
	for j, c := range src[i+1:] {
		if esc {
			esc = false
			continue
		}
		switch c {
		case '\\':
			esc, any = true, true
		case '"':
			return i + 2 + j, any, nil
		}
	}
	return 0, false, errSyntax
}

// unquote 解析键名。键名几乎都不含转义,走快路径(不分配)。
func unquote(b []byte) ([]byte, bool) {
	inner := b[1 : len(b)-1]
	if bytes.IndexByte(inner, '\\') < 0 {
		return inner, true
	}
	var s string
	if err := jsonUnmarshalString(b, &s); err != nil {
		return nil, false
	}
	return []byte(s), true
}

func jsonUnmarshalString(b []byte, s *string) error { return json.Unmarshal(b, s) }
