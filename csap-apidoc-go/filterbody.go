package apidoc

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
)

// SuccessField 返回一个可用作 Options.ShouldFilterBody 的判断:响应体是 JSON、path 指向的字段等于 want 时才裁剪,
// 不等于(业务失败)时原样输出。path 用点号分隔,如 "success"、"code"、"result.code"。
//
//	apidoc.Options{ShouldFilterBody: apidoc.SuccessField("success", true)}  // {"success":false,...} 不裁剪
//	apidoc.Options{ShouldFilterBody: apidoc.SuccessField("code", 0)}        // {"code":500,...} 不裁剪
//
// want 可以是 bool、字符串或数字(按 JSON 值比较,0 与 0.0 相等)。
// 响应体不是 JSON、或者没有这个字段时,判断不了,按"裁剪"处理(与不设置钩子时一致)。
func SuccessField(path string, want any) func(r *http.Request, status int, body []byte) bool {
	parts := strings.Split(path, ".")
	wantStr := fmt.Sprint(want)
	return func(_ *http.Request, _ int, body []byte) bool {
		dec := json.NewDecoder(bytes.NewReader(body))
		dec.UseNumber()
		var cur any
		if err := dec.Decode(&cur); err != nil {
			return true
		}
		for _, p := range parts {
			m, ok := cur.(map[string]any)
			if !ok {
				return true
			}
			if cur, ok = m[p]; !ok {
				return true
			}
		}
		switch v := cur.(type) {
		case bool, string:
			return fmt.Sprint(v) == wantStr
		case json.Number:
			return numEqual(v.String(), wantStr)
		}
		return true
	}
}

func numEqual(a, b string) bool {
	if a == b {
		return true
	}
	var x, y json.Number = json.Number(a), json.Number(b)
	fx, err1 := x.Float64()
	fy, err2 := y.Float64()
	return err1 == nil && err2 == nil && fx == fy
}
