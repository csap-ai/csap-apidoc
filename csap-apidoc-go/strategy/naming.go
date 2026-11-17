package strategy

import (
	"strings"
	"unicode"
)

// Lookup 按路径查规则,依次尝试原样、各段转下划线、各段转驼峰。
// Java 的规则路径用 Java 字段名(驼峰,如 data.rows.leagueId),而返回 JSON 常用下划线
// (league_id),Go 结构体的 json 名可能是任一种,所以两种写法都认。
func Lookup(m map[string]FieldRule, path string) (FieldRule, bool) {
	if len(m) == 0 {
		return FieldRule{}, false
	}
	if r, ok := m[path]; ok {
		return r, true
	}
	if u := UnderlinePath(path); u != path {
		if r, ok := m[u]; ok {
			return r, true
		}
	}
	if c := CamelPath(path); c != path {
		if r, ok := m[c]; ok {
			return r, true
		}
	}
	return FieldRule{}, false
}

// UnderlinePath 把路径每一段转成下划线写法:data.rows.leagueId -> data.rows.league_id。
func UnderlinePath(p string) string { return mapSegments(p, ToUnderline) }

// CamelPath 把路径每一段转成驼峰写法:data.rows.league_id -> data.rows.leagueId。
func CamelPath(p string) string { return mapSegments(p, ToCamel) }

func mapSegments(p string, f func(string) string) string {
	if !strings.Contains(p, ".") {
		return f(p)
	}
	segs := strings.Split(p, ".")
	for i, s := range segs {
		segs[i] = f(s)
	}
	return strings.Join(segs, ".")
}

// ToUnderline 与 Hutool StrUtil.toUnderlineCase 一致:leagueId -> league_id,userID -> user_id。
func ToUnderline(s string) string {
	if s == "" {
		return s
	}
	rs := []rune(s)
	var b strings.Builder
	for i, r := range rs {
		if unicode.IsUpper(r) {
			if i > 0 {
				prev := rs[i-1]
				nextLower := i+1 < len(rs) && unicode.IsLower(rs[i+1])
				if prev != '_' && (unicode.IsLower(prev) || unicode.IsDigit(prev) || (unicode.IsUpper(prev) && nextLower)) {
					b.WriteByte('_')
				}
			}
			b.WriteRune(unicode.ToLower(r))
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

// ToCamel 与 Hutool StrUtil.toCamelCase 一致:league_id -> leagueId。不含下划线时原样返回。
func ToCamel(s string) string {
	if !strings.Contains(s, "_") {
		return s
	}
	var b strings.Builder
	up := false
	for _, r := range s {
		if r == '_' {
			up = b.Len() > 0
			continue
		}
		if up {
			b.WriteRune(unicode.ToUpper(r))
			up = false
		} else {
			b.WriteRune(unicode.ToLower(r))
		}
	}
	return b.String()
}
