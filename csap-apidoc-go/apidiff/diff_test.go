package apidiff

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

// 两种语言导出的真实文档:Java 示例项目、Go 示例项目(gin-basic)。
var fixtures = map[string]string{"java": "testdata/java-example.json", "go": "testdata/go-example.json"}

type obj = map[string]any

func load(t *testing.T, file string, mutate func(data obj)) *Doc {
	t.Helper()
	b, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	if mutate != nil {
		var root obj
		if err := json.Unmarshal(b, &root); err != nil {
			t.Fatal(err)
		}
		mutate(root["data"].(obj))
		if b, err = json.Marshal(root); err != nil {
			t.Fatal(err)
		}
	}
	d, err := Parse(b, file)
	if err != nil {
		t.Fatal(err)
	}
	return d
}

func methods(data obj) []obj {
	var out []obj
	for _, c := range data["apiList"].([]any) {
		for _, m := range c.(obj)["methodList"].([]any) {
			out = append(out, m.(obj))
		}
	}
	return out
}

// firstMethod 找第一个既有请求字段又有返回字段的接口。
func firstMethod(t *testing.T, data obj) obj {
	t.Helper()
	for _, m := range methods(data) {
		if len(params(m["request"])) > 0 && len(params(m["response"])) > 0 {
			return m
		}
	}
	t.Fatal("没有合适的接口")
	return nil
}

// params 返回一组 model 下的顶层 parameters。
func params(models any) []obj {
	var out []obj
	list, _ := models.([]any)
	for _, m := range list {
		ps, _ := m.(obj)["parameters"].([]any)
		for _, p := range ps {
			out = append(out, p.(obj))
		}
	}
	return out
}

func find(ch []Change, kind string) *Change {
	for i := range ch {
		if ch[i].Kind == kind {
			return &ch[i]
		}
	}
	return nil
}

func TestSelfDiffIsEmpty(t *testing.T) {
	for name, f := range fixtures {
		t.Run(name, func(t *testing.T) {
			d := load(t, f, nil)
			if len(d.Endpoints) == 0 {
				t.Fatal("没解析到接口")
			}
			if ch := Diff(d, load(t, f, nil)); len(ch) != 0 {
				t.Fatalf("同一份文档不该有差异: %+v", ch)
			}
		})
	}
}

// 两种语言的文档各跑一遍同样的变更规则。
func TestBreakingRules(t *testing.T) {
	for name, f := range fixtures {
		t.Run(name, func(t *testing.T) {
			base := load(t, f, nil)
			var id string
			load(t, f, func(data obj) {
				m := firstMethod(t, data)
				id = m["key"].(string)
			})

			cases := []struct {
				name   string
				mutate func(m obj, data obj)
				kind   string
				level  Level
				want   bool // false 表示不该出现这个 kind
			}{
				{"删除接口", func(m, data obj) {
					for _, c := range data["apiList"].([]any) {
						var keep []any
						for _, x := range c.(obj)["methodList"].([]any) {
							if x.(obj)["key"] != m["key"] {
								keep = append(keep, x)
							}
						}
						c.(obj)["methodList"] = keep
					}
				}, "endpoint.removed", Breaking, true},
				{"隐藏接口等同删除", func(m, _ obj) { m["hidden"] = true }, "endpoint.removed", Breaking, true},
				{"删除返回字段", func(m, _ obj) {
					for _, md := range m["response"].([]any) {
						if ps := md.(obj)["parameters"].([]any); len(ps) > 0 {
							md.(obj)["parameters"] = ps[1:]
							return
						}
					}
				}, "field.removed", Breaking, true},
				{"删除请求字段只是警告", func(m, _ obj) {
					for _, md := range m["request"].([]any) {
						if ps := md.(obj)["parameters"].([]any); len(ps) > 0 {
							md.(obj)["parameters"] = ps[1:]
							return
						}
					}
				}, "field.removed", Warning, true},
				{"请求字段可选变必填", func(m, _ obj) { params(m["request"])[0]["required"] = true }, "field.required.added", Breaking, true},
				{"字段类型变化", func(m, _ obj) { params(m["response"])[0]["dataType"] = "SomethingElse" }, "field.type.changed", Breaking, true},
				{"新增必填请求字段", func(m, _ obj) {
					md := m["request"].([]any)[0].(obj)
					md["parameters"] = append(md["parameters"].([]any), obj{"name": "brandNew", "dataType": "String", "required": true})
				}, "field.required.added", Breaking, true},
				{"新增可选请求字段是兼容的", func(m, _ obj) {
					md := m["request"].([]any)[0].(obj)
					md["parameters"] = append(md["parameters"].([]any), obj{"name": "brandNew", "dataType": "String"})
				}, "field.added", Info, true},
				{"新增返回字段是兼容的", func(m, _ obj) {
					md := m["response"].([]any)[0].(obj)
					md["parameters"] = append(md["parameters"].([]any), obj{"name": "brandNew", "dataType": "String"})
				}, "field.added", Info, true},
				{"路径变化", func(m, _ obj) { m["paths"] = []any{"/moved"} }, "endpoint.path.removed", Breaking, true},
				{"方法减少", func(m, _ obj) { m["methods"] = []any{"TRACE"} }, "endpoint.method.removed", Breaking, true},
				{"新增方法是兼容的", func(m, _ obj) { m["methods"] = append(m["methods"].([]any), "TRACE") }, "endpoint.method.added", Info, true},
				{"改名但方法路径不变", func(m, _ obj) { m["key"] = "renamed.Key" }, "endpoint.renamed", Info, true},
				{"废弃", func(m, _ obj) { m["status"] = "DEPRECATED" }, "endpoint.deprecated", Warning, true},
				{"新增必填请求头", func(m, _ obj) {
					m["methodHeaders"] = append(anySlice(m["methodHeaders"]), obj{"key": "X-Tenant", "required": true})
				}, "header.required.added", Breaking, true},
				{"校验变严", func(m, _ obj) {
					params(m["request"])[0]["validate"] = []any{obj{"type": "NotEmpty", "code": "1", "level": 1}}
				}, "field.validate.changed", Warning, true},
			}
			for _, c := range cases {
				t.Run(c.name, func(t *testing.T) {
					nd := load(t, f, func(data obj) {
						for _, m := range methods(data) {
							if m["key"] == id {
								c.mutate(m, data)
								return
							}
						}
					})
					ch := Diff(base, nd)
					got := find(ch, c.kind)
					if got == nil {
						t.Fatalf("没有检测到 %s: %+v", c.kind, ch)
					}
					if got.Level != c.level {
						t.Fatalf("%s 级别 %s,期望 %s", c.kind, got.Level, c.level)
					}
					if c.name == "改名但方法路径不变" {
						if find(ch, "endpoint.removed") != nil || find(ch, "endpoint.added") != nil {
							t.Fatalf("改名不该报删除/新增: %+v", ch)
						}
					}
				})
			}
		})
	}
}

func anySlice(v any) []any { s, _ := v.([]any); return s }

func TestRequiredRelaxedIsInfo(t *testing.T) {
	for name, f := range fixtures {
		t.Run(name, func(t *testing.T) {
			var id string
			old := load(t, f, func(data obj) {
				m := firstMethod(t, data)
				id = m["key"].(string)
				params(m["request"])[0]["required"] = true
			})
			nd := load(t, f, func(data obj) {
				for _, m := range methods(data) {
					if m["key"] == id {
						params(m["request"])[0]["required"] = false
					}
				}
			})
			ch := Diff(old, nd)
			if c := find(ch, "field.required.removed"); c == nil || c.Level != Info {
				t.Fatalf("%+v", ch)
			}
		})
	}
}

// Java 文档和 Go 文档互相比较也能跑(接口完全不同,全是删除和新增),不报错。
func TestCrossLanguage(t *testing.T) {
	j, g := load(t, fixtures["java"], nil), load(t, fixtures["go"], nil)
	r := NewReport(j, g, nil)
	if r.Summary.Breaking != len(j.Endpoints) || r.Summary.Info != len(g.Endpoints) {
		t.Fatalf("%+v", r.Summary)
	}
	if !r.Fails(Breaking) || r.Fails("") {
		t.Fatal("Fails 判断不对")
	}
}

func mini(enums string) *Doc {
	d, err := Parse([]byte(`{"data":{"apiList":[],"docEnumList":`+enums+`}}`), "mini")
	if err != nil {
		panic(err)
	}
	return d
}

func TestEnums(t *testing.T) {
	old := mini(`[{"name":"Status","enumList":[{"code":"A"},{"code":"B"}]},{"name":"Gone","enumList":[{"code":"X"}]}]`)
	nd := mini(`[{"name":"Status","enumList":[{"code":"B"},{"code":"C"}]},{"name":"Fresh","enumList":[{"code":"Y"}]}]`)
	ch := Diff(old, nd)
	if c := find(ch, "enum.value.removed"); c == nil || c.Level != Breaking || c.Old != "A" {
		t.Fatalf("%+v", ch)
	}
	if c := find(ch, "enum.removed"); c == nil || c.Level != Breaking || c.Field != "Gone" {
		t.Fatalf("%+v", ch)
	}
	if c := find(ch, "enum.value.added"); c == nil || c.Level != Info {
		t.Fatalf("%+v", ch)
	}
	if c := find(ch, "enum.added"); c == nil || c.Level != Info {
		t.Fatalf("%+v", ch)
	}
}

func TestIgnore(t *testing.T) {
	rules, err := ParseIgnore(strings.NewReader(`
# 注释
example.UserController.list          # 整个接口
*  field.removed  data.legacy*
ai.csap.*.Product*  endpoint.removed
`))
	if err != nil || len(rules) != 3 {
		t.Fatalf("%v %+v", err, rules)
	}
	ch := []Change{
		{Kind: "endpoint.removed", Endpoint: "example.UserController.list"},
		{Kind: "field.removed", Endpoint: "x.Y.z", Field: "data.legacyId"},
		{Kind: "field.removed", Endpoint: "x.Y.z", Field: "data.id"},
		{Kind: "endpoint.removed", Endpoint: "ai.csap.example.controller.ProductController.getProduct"},
		{Kind: "endpoint.added", Endpoint: "ai.csap.example.controller.ProductController.getProduct"},
		{Kind: "enum.removed", Field: "Status"},
	}
	if n := ApplyIgnore(ch, rules); n != 3 {
		t.Fatalf("ignored %d: %+v", n, ch)
	}
	want := []bool{true, true, false, true, false, false}
	for i, c := range ch {
		if c.Ignored != want[i] {
			t.Errorf("#%d %+v ignored=%v", i, c, c.Ignored)
		}
	}
	if _, err := ParseIgnore(strings.NewReader("a b c d")); err == nil {
		t.Fatal("四段应报错")
	}
	// 被忽略的不计入失败
	old := load(t, fixtures["go"], nil)
	nd := load(t, fixtures["go"], func(data obj) { data["apiList"] = []any{} })
	r := NewReport(old, nd, []IgnoreRule{{Endpoint: "*", Kind: "endpoint.removed", Field: "*"}})
	if r.Fails(Breaking) || r.Summary.Ignored != len(old.Endpoints) || r.Summary.Breaking != 0 {
		t.Fatalf("%+v", r.Summary)
	}
}

func TestSnapshotRoundTripAndStable(t *testing.T) {
	for name, f := range fixtures {
		t.Run(name, func(t *testing.T) {
			d := load(t, f, nil)
			at := time.Date(2026, 1, 2, 3, 4, 5, 0, time.UTC)
			b1, err := d.Snapshot(at)
			if err != nil {
				t.Fatal(err)
			}
			d2, err := Parse(b1, "snap")
			if err != nil {
				t.Fatal(err)
			}
			if ch := Diff(d, d2); len(ch) != 0 {
				t.Fatalf("快照读回来应与原文档一致: %+v", ch)
			}
			b2, _ := d2.Snapshot(at)
			if string(b1) != string(b2) {
				t.Fatal("快照内容不稳定")
			}
			if strings.Contains(string(b1), `"key"`) || !strings.Contains(string(b1), SnapshotFormat) {
				t.Fatal("快照里不该有随机 key")
			}
		})
	}
	if _, err := Parse([]byte(`{"format":"other/9"}`), "x"); err == nil {
		t.Fatal("未知快照格式应报错")
	}
	if _, err := Parse([]byte(`{"foo":1}`), "x"); err == nil {
		t.Fatal("不像 apidoc 的 JSON 应报错")
	}
	if _, err := Parse([]byte(`not json`), "x"); err == nil {
		t.Fatal("非 JSON 应报错")
	}
}

func TestParseBareData(t *testing.T) {
	b, _ := os.ReadFile(fixtures["go"])
	var root obj
	_ = json.Unmarshal(b, &root)
	bare, _ := json.Marshal(root["data"])
	d, err := Parse(bare, "bare")
	if err != nil || len(d.Endpoints) != len(load(t, fixtures["go"], nil).Endpoints) {
		t.Fatalf("%v", err)
	}
}

func TestLoadURL(t *testing.T) {
	b, _ := os.ReadFile(fixtures["go"])
	var gotAuth, gotPath string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth, gotPath = r.Header.Get("Authorization"), r.URL.Path
		if r.URL.Path == "/csap/apidoc" {
			_, _ = w.Write(b)
			return
		}
		http.NotFound(w, r)
	}))
	defer srv.Close()
	d, err := Load(context.Background(), srv.URL, []string{"Authorization: Bearer t0k"})
	if err != nil || gotAuth != "Bearer t0k" || gotPath != "/csap/apidoc" || len(d.Endpoints) == 0 {
		t.Fatalf("%v %q %q", err, gotAuth, gotPath)
	}
	if strings.Contains(d.Source, "t0k") {
		t.Fatal("来源里不该带令牌")
	}
	if _, err := Load(context.Background(), srv.URL+"/nope?token=secret", nil); err == nil || strings.Contains(err.Error(), "secret") {
		t.Fatalf("404 应报错且不泄露查询串: %v", err)
	}
	if _, err := Load(context.Background(), srv.URL, []string{"bad header"}); err == nil {
		t.Fatal("坏请求头应报错")
	}
}

func TestReportFormats(t *testing.T) {
	old := load(t, fixtures["go"], nil)
	nd := load(t, fixtures["go"], func(data obj) {
		m := firstMethod(t, data)
		params(m["response"])[0]["dataType"] = "Other"
		m["key"] = "a|b"
	})
	r := NewReport(old, nd, nil)
	txt, md := r.Text(Info), r.Markdown(Info)
	js, err := r.JSON(Warning)
	if err != nil {
		t.Fatal(err)
	}
	for _, s := range []string{"破坏性", "field.type.changed"} {
		if !strings.Contains(txt, s) || !strings.Contains(md, s) {
			t.Errorf("缺少 %q", s)
		}
	}
	if !strings.Contains(md, "a\\|b") {
		t.Error("Markdown 表格里的 | 应转义")
	}
	var back Report
	if err := json.Unmarshal(js, &back); err != nil || back.Summary.Breaking == 0 {
		t.Fatalf("%v", err)
	}
	for _, c := range back.Changes {
		if c.Level == Info {
			t.Error("min-level=warning 不该列出 info")
		}
	}
}
