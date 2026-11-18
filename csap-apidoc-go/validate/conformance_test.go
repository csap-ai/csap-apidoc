package validate

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// 黄金用例(Java 与 Go 共用,docs/design/1.4.0-validation-alignment.md)。仓库里没有该文件时跳过。
const conformanceFile = "../../docs/conformance/validation-cases.json"

type conformanceCase struct {
	ID    string `json:"id"`
	Desc  string `json:"desc"`
	Field struct {
		In       string                  `json:"in"`
		Name     string                  `json:"name"`
		Path     string                  `json:"path"`
		Required bool                    `json:"required"`
		Rules    []strategy.ValidateRule `json:"rules"`
	} `json:"field"`
	Request struct {
		Query      string            `json:"query"`
		Headers    map[string]string `json:"headers"`
		PathParams map[string]string `json:"pathParams"`
		Body       json.RawMessage   `json:"body"`
	} `json:"request"`
	Expect struct {
		OK   bool   `json:"ok"`
		Rule string `json:"rule"`
		Code string `json:"code"`
	} `json:"expect"`
}

func TestConformanceCases(t *testing.T) {
	raw, err := os.ReadFile(conformanceFile)
	if os.IsNotExist(err) {
		t.Skip("没有 " + conformanceFile + ",跳过")
	}
	if err != nil {
		t.Fatal(err)
	}
	var file struct {
		Cases []conformanceCase `json:"cases"`
	}
	if err := json.Unmarshal(raw, &file); err != nil {
		t.Fatal(err)
	}
	if len(file.Cases) < 66 {
		t.Fatalf("黄金用例应不少于 66 条,实际 %d", len(file.Cases))
	}
	v := New(apidoc.New(apidoc.Options{}), Options{})
	for _, c := range file.Cases {
		t.Run(c.ID, func(t *testing.T) {
			check := apidoc.FieldCheck{In: c.Field.In, Name: c.Field.Name, FieldName: c.Field.Name,
				Field: c.Field.Name, Required: c.Field.Required, Rules: c.Field.Rules}
			if c.Field.In == "BODY" {
				check.Path = strings.Split(c.Field.Path, ".")
				check.Field = c.Field.Path
				check.FieldName = check.Path[len(check.Path)-1]
			}
			ep := &apidoc.Endpoint{Key: "conformance." + c.ID, Checks: []apidoc.FieldCheck{check}}
			target := "/x"
			if c.Request.Query != "" {
				target += "?" + c.Request.Query
			}
			var r *http.Request
			if c.Field.In == "BODY" {
				r = httptest.NewRequest("POST", target, strings.NewReader(string(c.Request.Body)))
				r.Header.Set("Content-Type", "application/json")
			} else {
				r = httptest.NewRequest("GET", target, nil)
			}
			for k, val := range c.Request.Headers {
				r.Header.Set(k, val)
			}
			viol := v.Check(r, ep, func(name string) string { return c.Request.PathParams[name] })
			switch {
			case c.Expect.OK && viol != nil:
				t.Fatalf("%s\n期望通过,实际失败: %s %s", c.Desc, viol.Rule, viol.Code)
			case !c.Expect.OK && viol == nil:
				t.Fatalf("%s\n期望失败 %s/%s,实际通过", c.Desc, c.Expect.Rule, c.Expect.Code)
			case !c.Expect.OK && (viol.Rule != c.Expect.Rule || viol.Code != c.Expect.Code):
				t.Fatalf("%s\n期望 %s/%s,实际 %s/%s", c.Desc, c.Expect.Rule, c.Expect.Code, viol.Rule, viol.Code)
			}
		})
	}
}

// Pattern 是全匹配(与 Java Matcher.matches() 一致):不带 ^$ 的正则也必须匹配整个值。
func TestPatternIsFullMatch(t *testing.T) {
	re, err := compile(`[0-9]+`)
	if err != nil {
		t.Fatal(err)
	}
	for in, want := range map[string]bool{"123": true, "a123": false, "123a": false, "": false, "1\n": false} {
		if got := re.MatchString(in); got != want {
			t.Errorf("%q: got %v want %v", in, got, want)
		}
	}
	re, _ = compile(`a|b`) // 或运算不能因为加锚点被拆开
	if !re.MatchString("a") || re.MatchString("ab") || re.MatchString("xb") {
		t.Fatal("a|b 应整串匹配 a 或 b")
	}
}
