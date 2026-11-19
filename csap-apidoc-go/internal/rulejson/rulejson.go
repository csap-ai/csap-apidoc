// Package rulejson 判断规则内容是否"没有变化",与 Java RuleJson.sameJson 的约定一致。
package rulejson

import (
	"encoding/json"
	"reflect"
)

// Same 判断新内容 after(空串表示删除)与现有内容是否相同:
// 现有内容不存在时,只有删除算相同;否则按 JSON 语义比较,忽略空白和键顺序。
func Same(before string, exists bool, after string) bool {
	if after == "" {
		return !exists
	}
	if !exists {
		return false
	}
	if before == after {
		return true
	}
	var a, b any
	if json.Unmarshal([]byte(before), &a) != nil || json.Unmarshal([]byte(after), &b) != nil {
		return false
	}
	return reflect.DeepEqual(a, b)
}
