package apidoc

import (
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// 有效状态:规则存储的覆盖 > 代码里的声明(不是 DEFAULT)> DEFAULT;不认识的值按没有处理。
func TestEffectiveStatus(t *testing.T) {
	type respT struct {
		ID int64 `json:"id"`
	}
	cases := []struct {
		name   string
		rules  strategy.MethodRules
		deprec bool
		want   string
		note   string
	}{
		{"都没有", strategy.MethodRules{}, false, model.StatusDefault, ""},
		{"只有代码声明", strategy.MethodRules{}, true, model.StatusDeprecated, ""},
		{"规则覆盖代码", strategy.MethodRules{Status: "FINISH"}, true, model.StatusFinish, ""},
		{"规则明确写 DEFAULT 覆盖代码声明", strategy.MethodRules{Status: "DEFAULT"}, true, model.StatusDefault, ""},
		{"带备注", strategy.MethodRules{Status: "DEPRECATED", StatusNote: "请改用 /v2"}, false, model.StatusDeprecated, "请改用 /v2"},
		{"不认识的值按没有", strategy.MethodRules{Status: "WHATEVER"}, true, model.StatusDeprecated, ""},
		{"小写不认识", strategy.MethodRules{Status: "finish"}, false, model.StatusDefault, ""},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			d := New(Options{Strategies: []strategy.Strategy{strategy.NewMemory(strategy.Rules{"G.m": c.rules})}})
			op := Op("G", "m").Resp(respT{})
			if c.deprec {
				op = op.Deprecated()
			}
			d.Register("GET", "/m", op, "")
			m := d.Snapshot().Method("G", "G.m")
			if m.Status != c.want || m.StatusNote != c.note {
				t.Fatalf("status=%q note=%q, want %q %q", m.Status, m.StatusNote, c.want, c.note)
			}
		})
	}
}

// 只有状态、没有字段规则的接口不是空规则:Memory 保存后保留,而不是当成删除。
func TestStatusOnlyRuleIsNotEmpty(t *testing.T) {
	if (strategy.MethodRules{Status: "DEPRECATED"}).IsEmpty() || (strategy.MethodRules{StatusNote: "n"}).IsEmpty() {
		t.Fatal("只有状态的规则不该是空规则")
	}
	if !(strategy.MethodRules{Status: "WHATEVER"}).IsEmpty() || !(strategy.MethodRules{}).IsEmpty() {
		t.Fatal("没有内容的规则应是空规则")
	}
	mem := strategy.NewMemory(nil)
	if _, err := mem.Save(nil, "G.m", strategy.MethodRules{Status: "FINISH"}, "u"); err != nil { //nolint:staticcheck
		t.Fatal(err)
	}
	rs, _ := mem.Load(nil) //nolint:staticcheck
	if rs["G.m"].Status != "FINISH" {
		t.Fatalf("%+v", rs)
	}
	// Clone / Merge 带上状态
	if c := strategy.Clone(strategy.MethodRules{Status: "FINISH", StatusNote: "n"}); c.Status != "FINISH" || c.StatusNote != "n" {
		t.Fatalf("clone: %+v", c)
	}
	merged := strategy.Merge(strategy.Rules{"G.m": {Status: "FINISH"}}, strategy.Rules{"G.m": {Status: "DEPRECATED", StatusNote: "x"}}, strategy.Rules{"G.m": {Status: "bogus"}})
	if merged["G.m"].Status != "DEPRECATED" || merged["G.m"].StatusNote != "x" {
		t.Fatalf("merge: %+v", merged["G.m"])
	}
}
