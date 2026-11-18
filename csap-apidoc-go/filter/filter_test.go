package filter

import (
	"testing"
)

func prune(t *testing.T, src string, n *Node) string {
	t.Helper()
	out, err := Prune([]byte(src), n)
	if err != nil {
		t.Fatalf("Prune(%s): %v", src, err)
	}
	return string(out)
}

func TestPruneCompact(t *testing.T) {
	mask := Compile([]string{"code", "data.rows.id", "data.total"}, nil)
	src := `{"code":"0","success":true,"data":{"total":2,"size":20,"rows":[{"id":1,"name":"a"},{"name":"b","id":2}]},"time":1}`
	want := `{"code":"0","data":{"total":2,"rows":[{"id":1},{"id":2}]}}`
	if got := prune(t, src, mask); got != want {
		t.Fatalf("got  %s\nwant %s", got, want)
	}
}

func TestPrunePrettyKeepsFormat(t *testing.T) {
	mask := Compile([]string{"a", "c"}, nil)
	src := "{\n\t\"a\": 1,\n\t\"b\": {\"x\": [1, 2]},\n\t\"c\": \"s\"\n}\n"
	want := "{\n\t\"a\": 1,\n\t\"c\": \"s\"\n}\n"
	if got := prune(t, src, mask); got != want {
		t.Fatalf("got  %q\nwant %q", got, want)
	}
	// 第一个成员被去掉时
	mask = Compile([]string{"c"}, nil)
	want = "{\n\t\"c\": \"s\"\n}\n"
	if got := prune(t, src, mask); got != want {
		t.Fatalf("got  %q\nwant %q", got, want)
	}
	// 全部去掉
	mask = Compile([]string{"zz"}, nil)
	if got := prune(t, src, mask); got != "{}\n" {
		t.Fatalf("got %q", got)
	}
}

func TestPruneAliasesAndWhole(t *testing.T) {
	// 规则写驼峰,JSON 是下划线;没有下级规则的 data.rows 整段输出
	mask := Compile([]string{"data", "data.lastId", "data.rows"}, nil)
	src := `{"data":{"last_id":1,"rows":[{"a":1,"b":{"c":2}}],"extra":3}}`
	want := `{"data":{"last_id":1,"rows":[{"a":1,"b":{"c":2}}]}}`
	if got := prune(t, src, mask); got != want {
		t.Fatalf("got %s", got)
	}
}

func TestPruneMapValues(t *testing.T) {
	mask := Compile([]string{"stats.goals"}, map[string]bool{"stats": true})
	src := `{"stats":{"home":{"goals":1,"shots":5},"away":{"goals":0,"shots":2}},"x":1}`
	want := `{"stats":{"home":{"goals":1},"away":{"goals":0}}}`
	if got := prune(t, src, mask); got != want {
		t.Fatalf("got %s", got)
	}
}

func TestPruneOpenDeny(t *testing.T) {
	n := &Node{Open: true, Deny: map[string]bool{"secret": true}, Fields: map[string]*Node{
		"child": {Fields: map[string]*Node{"keep": nil}},
	}}
	src := `{"a":1,"secret":2,"child":{"keep":1,"drop":2},"z":null}`
	want := `{"a":1,"child":{"keep":1},"z":null}`
	if got := prune(t, src, n); got != want {
		t.Fatalf("got %s", got)
	}
}

func TestPruneEdgeCases(t *testing.T) {
	mask := Compile([]string{"a"}, nil)
	cases := map[string]string{
		`{"a":"x\"},{y","b":1}`:       `{"a":"x\"},{y"}`,
		`{"b":[{"a":1}],"a":[[1,2]]}`: `{"a":[[1,2]]}`,
		`[{"a":1,"b":2},{"b":3}]`:     `[{"a":1},{}]`,
		`{"a":1,"b":2}`:               `{"a":1}`,
		`{}`:                          `{}`,
		`"str"`:                       `"str"`,
		` {"a" : true , "b":false} `:  ` {"a" : true} `,
	}
	for src, want := range cases {
		if got := prune(t, src, mask); got != want {
			t.Errorf("%s: got %s want %s", src, got, want)
		}
	}
	for _, bad := range []string{`{"a":1`, `{"a" 1}`, `{"a":1}x`, `[1,`, `{a:1}`, ``} {
		if _, err := Prune([]byte(bad), mask); err == nil {
			t.Errorf("%q should fail", bad)
		}
	}
	if out, _ := Prune([]byte(`{"x":1}`), nil); string(out) != `{"x":1}` {
		t.Error("nil mask must pass through")
	}
}
