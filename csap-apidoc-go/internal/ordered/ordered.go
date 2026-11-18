// Package ordered 提供保持插入顺序的 JSON 对象,Mock 输出和 JSON Schema 的字段顺序要与文档一致。
package ordered

import (
	"bytes"
	"encoding/json"
)

// Map 是保持插入顺序的 JSON 对象。零值可用。
type Map struct {
	keys []string
	vals map[string]any
}

// New 创建空对象。
func New() *Map { return &Map{vals: map[string]any{}} }

// Set 设置键;已存在时覆盖值,位置不变。
func (m *Map) Set(k string, v any) *Map {
	if m.vals == nil {
		m.vals = map[string]any{}
	}
	if _, ok := m.vals[k]; !ok {
		m.keys = append(m.keys, k)
	}
	m.vals[k] = v
	return m
}

// Get 读取键。
func (m *Map) Get(k string) (any, bool) {
	v, ok := m.vals[k]
	return v, ok
}

// Has 判断键是否存在。
func (m *Map) Has(k string) bool { _, ok := m.vals[k]; return ok }

// Keys 返回按插入顺序的键。
func (m *Map) Keys() []string { return append([]string(nil), m.keys...) }

// Len 返回键数。
func (m *Map) Len() int { return len(m.keys) }

// MarshalJSON 按插入顺序输出;不转义 < > &(文档里常有泛型写法)。
func (m *Map) MarshalJSON() ([]byte, error) {
	if m == nil {
		return []byte("null"), nil
	}
	var buf bytes.Buffer
	buf.WriteByte('{')
	for i, k := range m.keys {
		if i > 0 {
			buf.WriteByte(',')
		}
		kb, err := encode(k)
		if err != nil {
			return nil, err
		}
		buf.Write(kb)
		buf.WriteByte(':')
		vb, err := encode(m.vals[k])
		if err != nil {
			return nil, err
		}
		buf.Write(vb)
	}
	buf.WriteByte('}')
	return buf.Bytes(), nil
}

func encode(v any) ([]byte, error) {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		return nil, err
	}
	return bytes.TrimRight(buf.Bytes(), "\n"), nil
}
