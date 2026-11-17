package model

import (
	"bytes"
	"encoding/json"
	"sort"
	"time"
)

// Str 返回 s 的指针,方便给可为 null 的字段赋值。
func Str(s string) *string { return &s }

// Bool 返回 b 的指针。
func Bool(b bool) *bool { return &b }

// Raw 是已经按 csap 格式编码好的 JSON 片段,编码器原样写出。
// 生成时的缩进层级必须与写出位置一致。
type Raw []byte

// inlineExcept 里的键,即使数组元素都是基本类型也逐个换行。
// Java 里这两个字段是 List(fastjson 对 List 逐个换行),其余基本类型数组是 String[] / Set,写在一行。
var inlineExcept = map[string]bool{"methods": true, "paramNames": true}

// MarshalCsap 按 Java 版 fastjson 的格式输出 JSON:
//   - tab 缩进,冒号后无空格;
//   - 对象的键按字母序;
//   - 值为 null 的键省略;
//   - 空数组写成 [];元素全是基本类型的数组写在一行(methods、paramNames 除外),其余逐个换行。
//
// depth 是 v 所在的缩进层级(顶层为 0)。
func MarshalCsap(v any, depth int) ([]byte, error) {
	tree, err := toTree(v)
	if err != nil {
		return nil, err
	}
	var buf bytes.Buffer
	if err := writeValue(&buf, tree, depth, ""); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// ResultJSON 生成 Java ApidocResult 外层,data 是已按 depth=1 编码好的片段。
func ResultJSON(code, message string, data Raw) []byte {
	m := map[string]any{
		"code":     code,
		"language": "ZH",
		"message":  message,
		"success":  code == "0",
		"time":     json.Number(itoa(time.Now().UnixMilli())),
	}
	if data != nil {
		m["data"] = data
	}
	var buf bytes.Buffer
	_ = writeValue(&buf, m, 0, "")
	return buf.Bytes()
}

func itoa(n int64) string {
	b, _ := json.Marshal(n)
	return string(b)
}

func toTree(v any) (any, error) {
	if r, ok := v.(Raw); ok {
		return r, nil
	}
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		return nil, err
	}
	dec := json.NewDecoder(&buf)
	dec.UseNumber()
	var tree any
	if err := dec.Decode(&tree); err != nil {
		return nil, err
	}
	return tree, nil
}

func indent(buf *bytes.Buffer, depth int) {
	for i := 0; i < depth; i++ {
		buf.WriteByte('\t')
	}
}

func isScalar(v any) bool {
	switch v.(type) {
	case map[string]any, []any, Raw:
		return false
	}
	return true
}

func writeString(buf *bytes.Buffer, s string) {
	b, _ := marshalNoEscape(s)
	buf.Write(b)
}

func marshalNoEscape(v any) ([]byte, error) {
	var b bytes.Buffer
	enc := json.NewEncoder(&b)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		return nil, err
	}
	return bytes.TrimRight(b.Bytes(), "\n"), nil
}

func writeValue(buf *bytes.Buffer, v any, depth int, key string) error {
	switch t := v.(type) {
	case nil:
		buf.WriteString("null")
	case Raw:
		buf.Write(t)
	case string:
		writeString(buf, t)
	case json.Number:
		buf.WriteString(t.String())
	case bool:
		if t {
			buf.WriteString("true")
		} else {
			buf.WriteString("false")
		}
	case map[string]any:
		keys := make([]string, 0, len(t))
		for k, val := range t {
			if val == nil {
				continue
			}
			keys = append(keys, k)
		}
		if len(keys) == 0 {
			buf.WriteString("{}")
			return nil
		}
		sort.Strings(keys)
		buf.WriteString("{\n")
		for i, k := range keys {
			indent(buf, depth+1)
			writeString(buf, k)
			buf.WriteByte(':')
			if err := writeValue(buf, t[k], depth+1, k); err != nil {
				return err
			}
			if i < len(keys)-1 {
				buf.WriteByte(',')
			}
			buf.WriteByte('\n')
		}
		indent(buf, depth)
		buf.WriteByte('}')
	case []any:
		if len(t) == 0 {
			buf.WriteString("[]")
			return nil
		}
		inline := !inlineExcept[key]
		for _, e := range t {
			if !isScalar(e) {
				inline = false
				break
			}
		}
		if inline {
			buf.WriteByte('[')
			for i, e := range t {
				if i > 0 {
					buf.WriteByte(',')
				}
				if err := writeValue(buf, e, depth, ""); err != nil {
					return err
				}
			}
			buf.WriteByte(']')
			return nil
		}
		buf.WriteString("[\n")
		for i, e := range t {
			indent(buf, depth+1)
			if err := writeValue(buf, e, depth+1, ""); err != nil {
				return err
			}
			if i < len(t)-1 {
				buf.WriteByte(',')
			}
			buf.WriteByte('\n')
		}
		indent(buf, depth)
		buf.WriteByte(']')
	default:
		b, err := marshalNoEscape(t)
		if err != nil {
			return err
		}
		buf.Write(b)
	}
	return nil
}
