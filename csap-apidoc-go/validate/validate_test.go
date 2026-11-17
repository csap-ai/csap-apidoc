package validate

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

type listReq struct {
	ID    string `form:"id" doc:"ID"`
	Limit int    `form:"limit" doc:"条数"`
	Token string `header:"X-Token" binding:"required"`
}

type item struct {
	SKU string `json:"sku" doc:"商品编码"`
	Qty int    `json:"qty"`
}

type createReq struct {
	Username string `json:"username" doc:"用户名"`
	Phone    string `json:"phone" doc:"手机号"`
	Items    []item `json:"items"`
}

type getReq struct {
	ID int64 `uri:"id" doc:"用户ID"`
}

func notEmpty(code, msg string) strategy.ValidateRule {
	return strategy.ValidateRule{Type: "NotEmpty", Pattern: "NotEmpty", Code: code, Message: msg, Level: 1}
}

func newDoc(rules strategy.Rules) *apidoc.Doc {
	d := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{strategy.NewMemory(rules)}})
	d.Register("GET", "/list", apidoc.Op("G", "list").Req(listReq{}), "")
	d.Register("POST", "/users", apidoc.Op("G", "create").Body(createReq{}), "")
	d.Register("GET", "/users/:id", apidoc.Op("G", "get").Req(getReq{}), "")
	return d
}

var rules = strategy.Rules{
	"G.list": {Request: map[string]strategy.FieldRule{
		"id.id":       {Required: model.Bool(true), Validate: []strategy.ValidateRule{notEmpty("1001", "ID不能为空")}},
		"limit.limit": {Validate: []strategy.ValidateRule{{Type: "Pattern", Pattern: `^([1-9][0-9]{0,2}|1000)$`, Code: "1002", Message: "limit 取值 1-1000"}}},
	}},
	"G.create": {Request: map[string]strategy.FieldRule{
		"request.username": {Required: model.Bool(true), Validate: []strategy.ValidateRule{
			{Type: "Length", Pattern: "3,20", Code: "1012", Message: "长度3-20", Level: 2},
			notEmpty("1011", "用户名不能为空"),
		}},
		"request.phone":     {Validate: []strategy.ValidateRule{{Type: "Pattern", Pattern: "PATTERN_35", Code: "1013", Message: "手机号格式不对"}}},
		"request.items":     {},
		"request.items.sku": {Required: model.Bool(true)},
	}},
}

func check(t *testing.T, v *Validator, d *apidoc.Doc, method, route, url, body string, params map[string]string) *Violation {
	t.Helper()
	var r *http.Request
	if body != "" {
		r = httptest.NewRequest(method, url, strings.NewReader(body))
		r.Header.Set("Content-Type", "application/json")
	} else {
		r = httptest.NewRequest(method, url, nil)
	}
	r.Header.Set("X-Token", "t")
	ep := d.Snapshot().Endpoint(method, route)
	if ep == nil {
		t.Fatalf("no endpoint %s %s", method, route)
	}
	viol := v.Check(r, ep, func(k string) string { return params[k] })
	if body != "" {
		b, _ := io.ReadAll(r.Body)
		if string(b) != body {
			t.Fatalf("body not restored: %q", b)
		}
	}
	return viol
}

func TestQueryRules(t *testing.T) {
	d := newDoc(rules)
	v := New(d, Options{Mode: Enforce})
	cases := []struct {
		url, code, msg string
	}{
		{"/list", "1001", "字段id,ID不能为空"},
		{"/list?id=", "1001", "字段id,ID不能为空"},
		{"/list?id=1&limit=0", "1002", "字段limit,limit 取值 1-1000"},
		{"/list?id=1&limit=abc", "400", "条数,数据格式错误"},
		{"/list?id=1&limit=1000", "", ""},
		{"/list?id=1", "", ""}, // limit 非必填、没传:不校验
	}
	for _, c := range cases {
		viol := check(t, v, d, "GET", "/list", c.url, "", nil)
		if c.code == "" {
			if viol != nil {
				t.Errorf("%s: unexpected %v", c.url, viol)
			}
			continue
		}
		if viol == nil || viol.Code != c.code || viol.Message != c.msg {
			t.Errorf("%s: got %+v want %s %s", c.url, viol, c.code, c.msg)
		}
	}
}

func TestBodyRules(t *testing.T) {
	d := newDoc(rules)
	v := New(d, Options{Mode: Enforce})
	cases := []struct{ body, code string }{
		{`{"phone":"13800000000"}`, "1011"},            // 必填缺失,NotEmpty(level 1)先于 Length(level 2)
		{`{"username":"ab"}`, "1012"},                  // 长度不够
		{`{"username":"abc","phone":"12345"}`, "1013"}, // 内置手机号正则
		{`{"username":"abc","phone":"19912345678"}`, ""},
		{`{"username":"abc","items":[{"sku":"a"},{"qty":1}]}`, "400"}, // 数组里第二个元素缺 sku(只配必填)
		{`{"username":"abc","items":[{"sku":"a"}]}`, ""},
		{`{"username":"abc","items":null}`, ""}, // 上级为 null,不级联
		{`not json`, ""},                        // 请求体不是 JSON:交给 handler
	}
	for _, c := range cases {
		viol := check(t, v, d, "POST", "/users", "/users", c.body, nil)
		got := ""
		if viol != nil {
			got = viol.Code
		}
		if got != c.code {
			t.Errorf("%s: got %+v want %s", c.body, viol, c.code)
		}
	}
	viol := check(t, v, d, "POST", "/users", "/users", `{"username":"abc","items":[{}]}`, nil)
	if viol == nil || viol.Message != "字段sku,不能为空" || viol.Field != "request.items.sku" {
		t.Fatalf("required message: %+v", viol)
	}
}

func TestPathAndHeader(t *testing.T) {
	d := newDoc(rules)
	v := New(d, Options{Mode: Enforce})
	if viol := check(t, v, d, "GET", "/users/:id", "/users/x", "", map[string]string{"id": "x"}); viol == nil || viol.Rule != "Type" {
		t.Fatalf("path type: %+v", viol)
	}
	if viol := check(t, v, d, "GET", "/users/:id", "/users/7", "", map[string]string{"id": "7"}); viol != nil {
		t.Fatalf("path ok: %+v", viol)
	}
	r := httptest.NewRequest("GET", "/list?id=1", nil)
	viol := v.Check(r, d.Snapshot().Endpoint("GET", "/list"), nil)
	if viol == nil || viol.Field != "X-Token.X-Token" {
		t.Fatalf("missing header: %+v", viol)
	}
}

func TestModesAndRespond(t *testing.T) {
	d := newDoc(rules)
	var seen []*Violation
	on := func(_ *http.Request, v *Violation) { seen = append(seen, v) }

	run := func(v *Validator) (*httptest.ResponseRecorder, bool) {
		rec := httptest.NewRecorder()
		r := httptest.NewRequest("GET", "/list", nil)
		r.Header.Set("X-Token", "t")
		return rec, v.Handle(rec, r, "/list", nil)
	}
	// 默认关闭
	if _, ok := run(New(d, Options{OnViolation: on})); !ok || len(seen) != 0 {
		t.Fatal("off mode must not check")
	}
	// 观察模式:记录但放行
	if rec, ok := run(New(d, Options{Mode: Observe, OnViolation: on})); !ok || len(seen) != 1 || rec.Body.Len() != 0 || seen[0].Mode != Observe {
		t.Fatal("observe mode must record and pass")
	}
	// 拦截模式:与 Java 一致,HTTP 200 + ApidocResult
	rec, ok := run(New(d, Options{Mode: Enforce, OnViolation: on, TipType: Tip3}))
	if ok || rec.Code != 200 {
		t.Fatalf("enforce: ok=%v code=%d", ok, rec.Code)
	}
	var res map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatal(err)
	}
	if res["code"] != "1001" || res["message"] != "ID,ID不能为空" || res["success"] != false || res["data"] != nil {
		t.Fatalf("response: %v", res)
	}
	if !strings.HasPrefix(rec.Body.String(), "{\n\t\"code\":\"1001\",\n\t\"language\":\"ZH\",") {
		t.Fatalf("format: %q", rec.Body.String())
	}
	// 接口级覆盖
	if _, ok := run(New(d, Options{Mode: Enforce, Endpoints: map[string]Mode{"G.list": Off}})); !ok {
		t.Fatal("endpoint override")
	}
}

func TestRulesHotReload(t *testing.T) {
	mem := strategy.NewMemory(nil)
	d := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{mem}})
	d.Register("GET", "/list", apidoc.Op("G", "list").Req(listReq{}), "")
	v := New(d, Options{Mode: Enforce})
	r := httptest.NewRequest("GET", "/list?id=", nil)
	r.Header.Set("X-Token", "t")
	if viol := v.Check(r, d.Snapshot().Endpoint("GET", "/list"), nil); viol != nil {
		t.Fatalf("no rules yet: %+v", viol)
	}
	_, _ = mem.Save(r.Context(), "G.list", rules["G.list"], "test")
	_ = d.Refresh(r.Context())
	if viol := v.Check(r, d.Snapshot().Endpoint("GET", "/list"), nil); viol == nil || viol.Code != "1001" {
		t.Fatalf("after reload: %+v", viol)
	}
}

func TestCustomRuleAndTips(t *testing.T) {
	Register("Dict", func(x any, present bool, rule strategy.ValidateRule) (bool, error) {
		return !present || x == "A" || x == "B", nil
	})
	d := newDoc(strategy.Rules{"G.list": {Request: map[string]strategy.FieldRule{
		"id.id": {Validate: []strategy.ValidateRule{{Type: "Dict", Code: "2001", Message: "不在字典里"}}},
	}}})
	r := httptest.NewRequest("GET", "/list?id=C", nil)
	r.Header.Set("X-Token", "t")
	ep := d.Snapshot().Endpoint("GET", "/list")
	want := map[TipType]string{
		Tip1: "字段id,不在字典里", Tip11: "字段id不在字典里", Tip2: "id,不在字典里", Tip22: "id不在字典里",
		Tip3: "ID,不在字典里", Tip33: "ID不在字典里", Tip31: "不在字典里",
	}
	for tip, msg := range want {
		viol := New(d, Options{TipType: tip}).Check(r, ep, nil)
		if viol == nil || viol.Message != msg {
			t.Errorf("%s: %+v", tip, viol)
		}
	}
}

func TestRunRules(t *testing.T) {
	pv := func(s string) value { return value{v: s, present: true} }
	cases := []struct {
		rule strategy.ValidateRule
		val  value
		ok   bool
	}{
		{strategy.ValidateRule{Type: "NotNull"}, value{}, false},
		{strategy.ValidateRule{Type: "NotNull"}, pv(""), true},
		{strategy.ValidateRule{Type: "NotEmpty"}, pv(""), false},
		{strategy.ValidateRule{Type: "NotEmpty"}, value{v: []any{}, present: true}, false},
		{strategy.ValidateRule{Type: "NotBlank"}, pv("  "), false},
		{strategy.ValidateRule{Type: "Pattern", Pattern: `\d+`}, pv("12a"), false}, // 整串匹配
		{strategy.ValidateRule{Type: "Pattern", Pattern: `\d+`}, value{v: json.Number("12"), present: true}, true},
		{strategy.ValidateRule{Type: "Pattern", Pattern: `\d+`}, value{}, true},
		{strategy.ValidateRule{Type: "Min", Pattern: "1"}, pv("0"), false},
		{strategy.ValidateRule{Type: "Max", Pattern: "10"}, value{v: json.Number("10"), present: true}, true},
		{strategy.ValidateRule{Type: "Length", Pattern: "2,"}, pv("中文"), true},
		{strategy.ValidateRule{Type: "Size", Pattern: "1,2"}, value{v: []any{1, 2, 3}, present: true}, false},
		{strategy.ValidateRule{Type: "Enum", Pattern: "a, b"}, pv("b"), true},
	}
	for _, c := range cases {
		ok, err := run(c.rule, c.val)
		if err != nil || ok != c.ok {
			t.Errorf("%+v %+v: got %v %v", c.rule, c.val, ok, err)
		}
	}
	if _, err := run(strategy.ValidateRule{Type: "Nope"}, pv("x")); err == nil {
		t.Error("unknown type should error")
	}
}

func TestBuiltinPatterns(t *testing.T) {
	for _, p := range Patterns {
		if p.Type == 1 {
			continue
		}
		if _, err := compile(p.Name); err != nil {
			t.Errorf("%s: %v", p.Name, err)
		}
	}
	cases := map[string]map[string]bool{
		"PATTERN_12": {"-12": true, "-0": false, "12": false},
		"PATTERN_14": {"0": true, "-3": true, "10": false},
		"PATTERN_21": {"abc123": true, "a-b": false},
		"PATTERN_27": {"a_b1": true, "a b": false},
		"PATTERN_30": {"hello": true, "a=b": false},
		"PATTERN_35": {"19912345678": true, "16612345678": true, "12345678901": false},
		"PATTERN_36": {"010-12345678": true, "(010)1234567": true, "12345678": true, "1-2": false},
		"PATTERN_41": {"31": true, "01": true, "32": false},
		"PATTERN_42": {"my-file.xml": true, "a\\.xml": false},
		"PATTERN_43": {"100000": true, "1000001": false, "012345": false},
		"PATTERN_44": {"192.168.1.1": true, "256.1.1.1": false},
	}
	for name, vals := range cases {
		re, _ := compile(name)
		for s, want := range vals {
			if re.MatchString(s) != want {
				t.Errorf("%s(%q) = %v, want %v", name, s, !want, want)
			}
		}
	}
	if len(PatternNames()) != len(Patterns) {
		t.Error("PatternNames")
	}
}
