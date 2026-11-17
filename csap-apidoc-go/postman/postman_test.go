package postman

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

func sp(s string) *string { return &s }

func sampleDoc() *model.Response {
	return &model.Response{
		ApiInfo: &model.ApiInfo{Title: "演示 API", Description: "demo"},
		GlobalApiList: []*model.Controller{{
			SimpleName: "GlobalCtl",
			Path:       []string{"/global/"},
			MethodList: []*model.Method{{Value: "健康检查", Methods: []string{"GET"}, Paths: []string{"/health"}}},
		}},
		ApiList: []*model.Controller{
			{
				Value: "用户管理",
				Path:  []string{"/user/"},
				MethodList: []*model.Method{
					{
						Value:      "查询用户",
						Methods:    []string{"GET"},
						Paths:      []string{"/find"},
						ParamType:  model.ParamDefault,
						ParamNames: []string{"name", "currentPage", "token"},
						MethodHeaders: []model.MethodHeader{
							{Key: "Authorization", Value: "Bearer x", Description: "令牌"},
						},
						Request: []*model.Model{{Parameters: []*model.Parameter{
							{Name: "name", DataType: "String", ParamType: model.ParamQuery, Description: "姓名", Required: true},
							{Name: "currentPage", DataType: "Integer", ParamType: model.ParamDefault, Example: "3"},
							{Name: "token", DataType: "String", ParamType: model.ParamHeader},
						}}},
						Response: []*model.Model{{Parameters: []*model.Parameter{
							{Name: "code", DataType: "String"},
							{Name: "data", DataType: "List<User>", Parameters: []*model.Parameter{
								{Name: "id", DataType: "Long", Example: "7"},
								{Name: "secret", DataType: "String", Hidden: true},
								{Name: "tags", DataType: "String[]"},
								{Name: "roles", ModelType: sp(model.ModelArray), Parameters: []*model.Parameter{
									{Name: "code", DataType: "String", Example: `a"b`},
								}},
								{Name: "dept", DataType: "Dept", Parameters: []*model.Parameter{
									{Name: "createTime", DataType: "LocalDateTime"},
								}},
							}},
						}}},
					},
					{
						Value:     "按ID获取",
						Methods:   []string{"GET"},
						Paths:     []string{"{id}"},
						ParamType: model.ParamPath,
						Request: []*model.Model{{Parameters: []*model.Parameter{
							{Name: "id", DataType: "Long", ParamType: model.ParamPath, Required: true},
						}}},
					},
					{
						Value:     "新增用户",
						Methods:   []string{"POST"},
						Paths:     []string{"/add"},
						ParamType: model.ParamBody,
						Request: []*model.Model{{Parameters: []*model.Parameter{
							{Name: "name", DataType: "java.lang.String", Example: "李\"四\n"},
							{Name: "age", DataType: "int"},
							{Name: "vip", DataType: "Boolean"},
							{Name: "birthday", DataType: "Date"},
							{Name: "address", DataType: "Address", Parameters: []*model.Parameter{{Name: "city", DataType: "String"}}},
							{Name: "items", DataType: "List<Item>", ModelType: sp(model.ModelArray)},
						}}},
						Response: []*model.Model{{Parameters: []*model.Parameter{{Name: "data", DataType: "Boolean"}}}},
					},
					{Value: "隐藏", Hidden: true, Methods: []string{"DELETE"}},
				},
			},
			{Value: "空分组"},
			{MethodList: []*model.Method{{Value: "only hidden", Hidden: true}}},
			{MethodList: []*model.Method{{Value: "绝对地址", Paths: []string{"https://api.example.com/v1/x/"}}}},
		},
	}
}

func build(t *testing.T, d *model.Response) map[string]any {
	t.Helper()
	b, err := Build(d)
	if err != nil {
		t.Fatal(err)
	}
	var m map[string]any
	if err := json.Unmarshal(b, &m); err != nil {
		t.Fatalf("invalid json: %v\n%s", err, b)
	}
	return m
}

func get(v any, path ...any) any {
	for _, p := range path {
		switch k := p.(type) {
		case string:
			v = v.(map[string]any)[k]
		case int:
			v = v.([]any)[k]
		}
	}
	return v
}

func TestBuildStructure(t *testing.T) {
	m := build(t, sampleDoc())

	if get(m, "info", "name") != "演示 API" || get(m, "info", "description") != "demo" ||
		get(m, "info", "schema") != schemaURL {
		t.Fatalf("info: %v", m["info"])
	}
	if v := get(m, "variable", 0); v.(map[string]any)["key"] != "baseUrl" ||
		v.(map[string]any)["value"] != "http://localhost:8080" {
		t.Fatalf("variable: %v", v)
	}

	items := m["item"].([]any)
	var names []string
	for _, it := range items {
		names = append(names, it.(map[string]any)["name"].(string))
	}
	if strings.Join(names, ",") != "GlobalCtl,用户管理,default" {
		t.Fatalf("folders: %v", names)
	}

	// global
	g := get(items[0], "item", 0, "request")
	if get(g, "url", "raw") != "{{baseUrl}}/global/health" {
		t.Fatalf("global url: %v", get(g, "url"))
	}
	if _, ok := get(g, "url").(map[string]any)["host"]; ok {
		t.Fatal("relative url should not have host")
	}

	user := get(items[1], "item").([]any)
	if len(user) != 3 {
		t.Fatalf("hidden method not skipped: %d", len(user))
	}

	// GET with query + header
	find := user[0].(map[string]any)
	req := find["request"]
	if get(req, "method") != "GET" || get(req, "url", "raw") != "{{baseUrl}}/user/find" {
		t.Fatalf("find req: %v", req)
	}
	if p := get(req, "url", "path").([]any); len(p) != 2 || p[0] != "user" || p[1] != "find" {
		t.Fatalf("path: %v", p)
	}
	q := get(req, "url", "query").([]any)
	if len(q) != 2 {
		t.Fatalf("query: %v", q)
	}
	q0, q1 := q[0].(map[string]any), q[1].(map[string]any)
	if q0["key"] != "name" || q0["value"] != "技术部" || q0["description"] != "姓名" || q0["disabled"] != nil {
		t.Fatalf("q0: %v", q0)
	}
	if q1["key"] != "currentPage" || q1["value"] != "3" || q1["disabled"] != true {
		t.Fatalf("q1: %v", q1)
	}
	h := get(req, "header", 0).(map[string]any)
	if h["key"] != "Authorization" || h["name"] != "Authorization" || h["value"] != "Bearer x" ||
		get(h, "description", "content") != "令牌" || get(h, "description", "type") != "text/plain" {
		t.Fatalf("header: %v", h)
	}
	if _, ok := req.(map[string]any)["body"]; ok {
		t.Fatal("GET must not have body")
	}
	resp := find["response"].([]any)
	if len(resp) != 4 {
		t.Fatalf("responses: %d", len(resp))
	}
	wantCodes := []float64{200, 400, 404, 429}
	for i, r := range resp {
		if get(r, "code") != wantCodes[i] {
			t.Fatalf("resp %d code %v", i, get(r, "code"))
		}
	}
	okBody := get(resp[0], "body").(string)
	wantData := "[{\n" +
		"    \"id\": 7,\n" +
		"    \"tags\": [\"示例值\"],\n" +
		"    \"roles\": [{\n    \"code\": \"a\\\"b\"\n  }],\n" +
		"    \"dept\": {\n    \"createTime\": \"2024-01-01T00:00:00\"\n  }\n" +
		"  }]"
	if !strings.Contains(okBody, "\"data\": "+wantData+",\n") {
		t.Fatalf("success body:\n%s", okBody)
	}
	if get(resp[0], "originalRequest", "url", "query") == nil || get(resp[0], "originalRequest", "header") == nil {
		t.Fatalf("originalRequest: %v", get(resp[0], "originalRequest"))
	}
	if len(get(resp[1], "cookie").([]any)) != 0 || get(resp[1], "_postman_previewlanguage") != "json" {
		t.Fatalf("bad request resp: %v", resp[1])
	}

	// PATH param: paramNames empty -> no query; trailing slash joined once
	byID := user[1].(map[string]any)["request"]
	if get(byID, "url", "raw") != "{{baseUrl}}/user/{id}" {
		t.Fatalf("byId url: %v", get(byID, "url"))
	}
	if _, ok := get(byID, "url").(map[string]any)["query"]; ok {
		t.Fatal("path method should not have query")
	}

	// POST body
	add := user[2].(map[string]any)
	body := get(add, "request", "body")
	if get(body, "mode") != "raw" || get(body, "options", "raw", "language") != "json" {
		t.Fatalf("body: %v", body)
	}
	wantRaw := "{\n" +
		"  \"name\": \"李\\\"四\\n\",\n" +
		"  \"age\": 0,\n" +
		"  \"vip\": true,\n" +
		"  \"birthday\": \"2024-01-01T00:00:00\",\n" +
		"  \"address\": \"\",\n" +
		"  \"items\": \"\"\n" +
		"}"
	if get(body, "raw") != wantRaw {
		t.Fatalf("raw:\n%s\nwant:\n%s", get(body, "raw"), wantRaw)
	}
	if !strings.Contains(get(add, "response", 0, "body").(string), "\"data\": true,\n") {
		t.Fatalf("add resp body: %v", get(add, "response", 0, "body"))
	}
	if get(add, "response", 0, "originalRequest", "body", "raw") != wantRaw {
		t.Fatal("originalRequest body")
	}

	// absolute url
	abs := get(items[2], "item", 0, "request", "url").(map[string]any)
	if abs["raw"] != "https://api.example.com/v1/x/" || abs["protocol"] != "https" {
		t.Fatalf("abs: %v", abs)
	}
	if hs := abs["host"].([]any); len(hs) != 3 || hs[0] != "api" || hs[2] != "com" {
		t.Fatalf("host: %v", hs)
	}
	if ps := abs["path"].([]any); len(ps) != 2 || ps[1] != "x" {
		t.Fatalf("path: %v", ps)
	}
}

func TestDeterministic(t *testing.T) {
	a, _ := Build(sampleDoc())
	b, _ := Build(sampleDoc())
	if !bytes.Equal(a, b) {
		t.Fatal("output not deterministic")
	}
}

// 对一份最小文档逐字节比对 Java 的输出格式(紧凑、中文不转义、字段顺序)。
func TestExactBytesMinimal(t *testing.T) {
	got, err := Build(&model.Response{
		ApiList: []*model.Controller{{
			Value: "a",
			MethodList: []*model.Method{{
				Value: "m", Methods: []string{"PUT"}, Paths: []string{"/x"},
				ParamType: model.ParamBody,
				Request:   []*model.Model{{}},
			}},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	want := `{"info":{"name":"CSAP API Collection","description":"Generated from CSAP Framework API Documentation","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},` +
		`"item":[{"name":"a","item":[{"name":"m","request":{"method":"PUT","url":{"raw":"{{baseUrl}}/x","path":["x"]},` +
		`"body":{"mode":"raw","raw":"{\n\n}","options":{"raw":{"language":"json"}}}}}]}],` +
		`"variable":[{"key":"baseUrl","value":"http://localhost:8080","description":"API基础URL"}]}`
	if string(got) != want {
		t.Fatalf("got:\n%s\nwant:\n%s", got, want)
	}
}

func TestEmptyAndNil(t *testing.T) {
	if _, err := Build(nil); err == nil {
		t.Fatal("want error for nil doc")
	}
	got, _ := Build(&model.Response{})
	if !strings.Contains(string(got), `"item":[]`) {
		t.Fatalf("empty: %s", got)
	}
}

func TestParamNamesFallback(t *testing.T) {
	m := build(t, &model.Response{ApiList: []*model.Controller{{
		Value: "c",
		MethodList: []*model.Method{{
			Value: "list", ParamNames: []string{"ids", "pageSize", "id", "currentPage", "other"},
			Request: []*model.Model{nil, {Parameters: []*model.Parameter{
				{Name: "other", ParamType: model.ParamBody, Description: "其它"},
			}}},
			Response: []*model.Model{{Parameters: []*model.Parameter{{Name: "code"}}}},
		}},
	}}})
	q := get(m, "item", 0, "item", 0, "request", "url", "query").([]any)
	want := [][3]string{{"ids", "1,2,3", "资源ID数组"}, {"pageSize", "20", "每页显示条数"},
		{"id", "1", "资源ID"}, {"currentPage", "1", "当前页数"}, {"other", "", "其它"}}
	for i, w := range want {
		e := q[i].(map[string]any)
		if e["key"] != w[0] || e["value"] != w[1] || e["description"] != w[2] || e["disabled"] != true {
			t.Fatalf("q[%d]=%v want %v", i, e, w)
		}
	}
	if get(m, "item", 0, "item", 0, "request", "method") != "GET" {
		t.Fatal("default method")
	}
	body := get(m, "item", 0, "item", 0, "response", 0, "body").(string)
	if !strings.Contains(body, `"data": {},`) {
		t.Fatalf("data fallback: %s", body)
	}
}

func TestHelpers(t *testing.T) {
	if s := javaSplit("", "/"); len(s) != 1 || s[0] != "" {
		t.Fatalf("%q", s)
	}
	if s := javaSplit("a//b//", "/"); strings.Join(s, "|") != "a||b" {
		t.Fatalf("%q", s)
	}
	if s := javaSplit("///", "/"); len(s) != 0 {
		t.Fatalf("%q", s)
	}
	u := parseURL("http://localhost:8080", nil)
	if len(u) != 3 || u[1].v != "http" {
		t.Fatalf("%v", u)
	}
	if dataExample(nil) != "{}" || dataExample([]*model.Model{{}}) != "{}" {
		t.Fatal("dataExample empty")
	}
	if paramValueJSON(&model.Parameter{Name: "x", DataType: "Integer[]"}) != "[0]" {
		t.Fatal("base array")
	}
	if paramValueJSON(&model.Parameter{Name: "x", DataType: "Set<Long>", Parameters: []*model.Parameter{{Name: "h", Hidden: true}}}) != "[{\n\n  }]" {
		t.Fatal("array of object")
	}
	if jsonDefault(&model.Parameter{DataType: "Long", Example: "  "}) != "0" ||
		jsonDefault(&model.Parameter{DataType: "boolean", Example: "false"}) != "false" ||
		jsonDefault(&model.Parameter{DataType: "Obj", Example: "x\ty"}) != `"x\ty"` {
		t.Fatal("jsonDefault")
	}
	if buildURL(&model.Method{Paths: []string{"/a"}}, &model.Controller{}) != "/a" ||
		buildURL(&model.Method{Paths: []string{""}}, &model.Controller{Path: []string{"/c/"}}) != "/c/" {
		t.Fatal("buildURL")
	}
	var b strings.Builder
	writeValue(&b, nil)
	if b.String() != "null" {
		t.Fatal("null")
	}
}
