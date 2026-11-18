package apidiff

import (
	"bufio"
	"fmt"
	"io"
	"strings"
)

// IgnoreRule 是忽略清单里的一条规则:接口、变更类型、字段三段都命中才忽略。
type IgnoreRule struct {
	Endpoint, Kind, Field string
	Line                  int
}

// ParseIgnore 解析忽略清单。每行一条规则:`接口 [变更类型 [字段]]`,空白分隔,`*` 匹配任意字符(含 . 和 /),
// 缺省的段等于 `*`;`#` 开头是注释。例:
//
//	com.example.OrderController.legacy            # 这个接口整体不检查
//	*  field.removed  data.legacyId               # 所有接口的 data.legacyId 删除都放行
//	example.UserController.list endpoint.removed
func ParseIgnore(r io.Reader) ([]IgnoreRule, error) {
	var out []IgnoreRule
	sc := bufio.NewScanner(r)
	for n := 1; sc.Scan(); n++ {
		line := sc.Text()
		if i := strings.Index(line, "#"); i >= 0 {
			line = line[:i]
		}
		f := strings.Fields(line)
		if len(f) == 0 {
			continue
		}
		if len(f) > 3 {
			return nil, fmt.Errorf("忽略清单第 %d 行:最多三段(接口 变更类型 字段),实际 %d 段", n, len(f))
		}
		rule := IgnoreRule{Endpoint: f[0], Kind: "*", Field: "*", Line: n}
		if len(f) > 1 {
			rule.Kind = f[1]
		}
		if len(f) > 2 {
			rule.Field = f[2]
		}
		out = append(out, rule)
	}
	return out, sc.Err()
}

// ApplyIgnore 给命中规则的差异打上 Ignored 标记,返回被忽略的条数。
func ApplyIgnore(changes []Change, rules []IgnoreRule) int {
	n := 0
	for i := range changes {
		c := &changes[i]
		for _, r := range rules {
			// 枚举等没有接口的变更,接口段按空串比较,所以只有 `*` 能命中
			if glob(r.Endpoint, c.Endpoint) && glob(r.Kind, c.Kind) && glob(r.Field, c.Field) {
				c.Ignored = true
				n++
				break
			}
		}
	}
	return n
}

// glob 做只支持 * 的通配匹配。
func glob(pattern, s string) bool {
	parts := strings.Split(pattern, "*")
	if len(parts) == 1 {
		return pattern == s
	}
	if !strings.HasPrefix(s, parts[0]) {
		return false
	}
	s = s[len(parts[0]):]
	for _, p := range parts[1 : len(parts)-1] {
		i := strings.Index(s, p)
		if i < 0 {
			return false
		}
		s = s[i+len(p):]
	}
	last := parts[len(parts)-1]
	return strings.HasSuffix(s, last)
}
