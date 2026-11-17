package model

import (
	"bytes"
	"encoding/json"
	"sort"
	"strconv"
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
//   - 空数组写成 [];元素全是基本类型的数组写在一行(methods、paramNames 除外),其余逐个换行;
//   - 参数节点同时有 children 和 parameters 时,parameters 的元素写成 {"$ref":"<路径>.children.parameters[i]"}。
//     Java 里 CsapDocParameter.getParameters() 返回的就是 children.parameters 同一个 List,
//     fastjson 的循环引用检测把第二次出现的元素写成 $ref,这里照样输出。
//
// depth 是 v 所在的缩进层级:0 表示 v 是顶层(JSONPath 为 $),1 表示 v 是 ApidocResult 的 data($.data)。
func MarshalCsap(v any, depth int) ([]byte, error) {
	path := "$"
	if depth == 1 {
		path = "$.data"
	}
	return MarshalCsapAt(v, depth, path)
}

// MarshalCsapAt 同 MarshalCsap,path 是 v 在整个输出里的 JSONPath(用于生成 $ref)。
func MarshalCsapAt(v any, depth int, path string) ([]byte, error) {
	tree, err := toTree(v)
	if err != nil {
		return nil, err
	}
	var buf bytes.Buffer
	if err := writeValue(&buf, tree, depth, "", path); err != nil {
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
	_ = writeValue(&buf, m, 0, "", "$")
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

func writeValue(buf *bytes.Buffer, v any, depth int, key, path string) error {
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
		refParams := isRefParams(t)
		buf.WriteString("{\n")
		for i, k := range keys {
			indent(buf, depth+1)
			writeString(buf, k)
			buf.WriteByte(':')
			if k == "parameters" && refParams {
				writeRefs(buf, t[k].([]any), depth+1, path+".children.parameters")
			} else if err := writeValue(buf, t[k], depth+1, k, path+"."+k); err != nil {
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
				if err := writeValue(buf, e, depth, "", ""); err != nil {
					return err
				}
			}
			buf.WriteByte(']')
			return nil
		}
		buf.WriteString("[\n")
		for i, e := range t {
			indent(buf, depth+1)
			if err := writeValue(buf, e, depth+1, "", path+"["+strconv.Itoa(i)+"]"); err != nil {
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

// isRefParams 判断 m 是不是"有子节点的参数":children 是带 parameters 的对象,自身也有 parameters 数组。
func isRefParams(m map[string]any) bool {
	ps, ok := m["parameters"].([]any)
	if !ok {
		return false
	}
	c, ok := m["children"].(map[string]any)
	if !ok {
		return false
	}
	cps, ok := c["parameters"].([]any)
	return ok && len(cps) == len(ps)
}

func writeRefs(buf *bytes.Buffer, ps []any, depth int, base string) {
	if len(ps) == 0 {
		buf.WriteString("[]")
		return
	}
	buf.WriteString("[\n")
	for i := range ps {
		indent(buf, depth+1)
		buf.WriteString(`{"$ref":`)
		writeString(buf, base+"["+strconv.Itoa(i)+"]")
		buf.WriteByte('}')
		if i < len(ps)-1 {
			buf.WriteByte(',')
		}
		buf.WriteByte('\n')
	}
	indent(buf, depth)
	buf.WriteByte(']')
}
