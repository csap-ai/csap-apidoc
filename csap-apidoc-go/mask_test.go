package apidoc

import (
	"encoding/json"
	"net/http"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

type apiResult struct {
	Code     string `json:"code" doc_force:"true"`
	Message  string `json:"message" doc_force:"true"`
	Data     any    `json:"data" doc_force:"true"`
	Language string `json:"language" doc_force:"true"`
	Time     int64  `json:"time" doc_force:"true"`
	Success  bool   `json:"success"`
}

type team struct {
	ID     int64  `json:"id"`
	Name   string `json:"name"`
	Secret string `json:"secret"`
}

type match struct {
	ID       int64           `json:"id"`
	LeagueID int64           `json:"league_id"`
	Home     team            `json:"home"`
	Away     *team           `json:"away"`
	ByHalf   map[string]team `json:"by_half"`
	Internal string          `json:"internal"`
	Hidden   string          `json:"hidden" doc_hidden:"true"`
}

type matchPage struct {
	Rows  []match `json:"rows"`
	Total int64   `json:"total" doc_force:"true"`
	Size  int     `json:"size"`
}

func render(t *testing.T, mask *filter.Node, v any) string {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	out, err := filter.Prune(b, mask)
	if err != nil {
		t.Fatal(err)
	}
	return string(out)
}

var sample = apiResult{Code: "0", Message: "成功", Success: true, Time: 1, Language: "ZH", Data: matchPage{
	Total: 1, Size: 20,
	Rows: []match{{ID: 7, LeagueID: 3, Internal: "x", Hidden: "h",
		Home:   team{ID: 1, Name: "A", Secret: "s"},
		Away:   &team{ID: 2, Name: "B", Secret: "s"},
		ByHalf: map[string]team{"h1": {ID: 1, Name: "A", Secret: "s"}}}},
}}

// 规则写驼峰(Java 字段名),结构体 json 是下划线
var rules = strategy.Rules{"G.list": {Response: map[string]strategy.FieldRule{
	"data":                  {Include: model.Bool(true)},
	"data.rows":             {Include: model.Bool(true)},
	"data.rows.id":          {Include: model.Bool(true)},
	"data.rows.leagueId":    {Include: model.Bool(true)},
	"data.rows.home":        {Include: model.Bool(true)},
	"data.rows.home.id":     {Include: model.Bool(true), Hidden: model.Bool(true)},
	"data.rows.away":        {Include: model.Bool(true)},
	"data.rows.away.name":   {Include: model.Bool(true)},
	"data.rows.byHalf.name": {Include: model.Bool(true)},
	"data.rows.internal":    {Include: model.Bool(false)},
}}}

func maskFor(t *testing.T, mode filter.Mode) (*Snapshot, *filter.Node) {
	d := New(Options{
		Strategies: []strategy.Strategy{strategy.NewMemory(rules)},
		Wrapper:    &Wrapper{Type: apiResult{}},
		FilterMode: mode,
	})
	d.Register("GET", "/m", Op("G", "list").Resp(matchPage{}), "")
	snap := d.Snapshot()
	ep := snap.Endpoint("GET", "/m")
	if ep == nil || ep.ResponseMask == nil {
		t.Fatal("mask missing")
	}
	return snap, ep.ResponseMask
}

func TestPathModeMask(t *testing.T) {
	snap, mask := maskFor(t, filter.ModePath)
	got := render(t, mask, sample)
	// success 不是 force、也没规则 -> 去掉;total 是 force;size 没规则 -> 去掉;
	// home 只放行 id;away 只放行 name;by_half 是 map,放行每个值的 name(作为上级自动保留)
	want := `{"code":"0","message":"成功","data":{"rows":[{"id":7,"league_id":3,"home":{"id":1},"away":{"name":"B"},"by_half":{"h1":{"name":"A"}}}],"total":1},"language":"ZH","time":1}`
	if got != want {
		t.Fatalf("got  %s\nwant %s", got, want)
	}
	// 文档与输出一致,hidden 规则只影响文档
	m := snap.Method("G", "G.list")
	data := findParam(m.Response[0].Parameters, "data")
	rows := findParam(data.Children.Parameters, "rows")
	home := findParam(rows.Children.Parameters, "home")
	if len(home.Children.Parameters) != 0 {
		t.Fatalf("home.id should be hidden in doc: %+v", home.Children.Parameters)
	}
	if findParam(rows.Children.Parameters, "internal") != nil || findParam(data.Children.Parameters, "size") != nil {
		t.Fatal("non-included fields must not show in doc")
	}
	if findParam(rows.Children.Parameters, "by_half") == nil {
		t.Fatal("ancestor of included field should show")
	}
}

func TestCsapModeMask(t *testing.T) {
	_, mask := maskFor(t, filter.ModeCsap)
	got := render(t, mask, sample)
	// 按类型合并,且与 Java 一样先查黑名单:先遍历 home,name 没放行 -> 进 team 的黑名单;
	// 之后 away.name 放行,加进白名单,但黑名单优先,所以两处都只输出 id。
	// by_half 本身没被 include -> 不输出(Java 不会自动放行上级)。
	want := `{"code":"0","message":"成功","data":{"rows":[{"id":7,"league_id":3,"home":{"id":1},"away":{"id":2}}],"total":1},"language":"ZH","time":1}`
	if got != want {
		t.Fatalf("got  %s\nwant %s", got, want)
	}
}

func TestCsapModeEmptyIncludes(t *testing.T) {
	// 某个类型的字段全部没放行:Java 白名单为空,改为"黑名单之外全部输出",黑名单就是这些字段,结果为 {}。
	d := New(Options{
		FilterMode: filter.ModeCsap,
		Strategies: []strategy.Strategy{strategy.NewMemory(strategy.Rules{"G.t": {Response: map[string]strategy.FieldRule{
			"x": {Include: model.Bool(false)},
		}}})},
	})
	d.Register("GET", "/t", Op("G", "t").Resp(team{}), "")
	mask := d.Snapshot().Endpoint("GET", "/t").ResponseMask
	if got := render(t, mask, map[string]any{"id": 1, "name": "a", "secret": "s", "extra": 1}); got != `{"extra":1}` {
		t.Fatalf("got %s", got)
	}
}

func TestNoRulesNoMask(t *testing.T) {
	d := New(Options{Wrapper: &Wrapper{Type: apiResult{}}})
	d.Register("GET", "/m", Op("G", "list").Resp(matchPage{}), "")
	if d.Snapshot().Endpoint("GET", "/m").ResponseMask != nil {
		t.Fatal("endpoint without response rules must not be filtered")
	}
}

func TestFilterModeOffAndEndpointOverride(t *testing.T) {
	newDoc := func(o Options) *Doc {
		o.Wrapper = &Wrapper{Type: apiResult{}}
		o.Strategies = []strategy.Strategy{strategy.NewMemory(rules)}
		d := New(o)
		d.Register("GET", "/m", Op("G", "list").Resp(matchPage{}), "")
		return d
	}
	if newDoc(Options{FilterMode: filter.ModeOff}).Snapshot().Endpoint("GET", "/m").ResponseMask != nil {
		t.Fatal("global off must not filter")
	}
	d := newDoc(Options{FilterEndpoints: map[string]filter.Mode{"G.list": filter.ModeOff}})
	if d.Snapshot().Endpoint("GET", "/m").ResponseMask != nil {
		t.Fatal("endpoint off must not filter")
	}
	d = newDoc(Options{FilterMode: filter.ModeOff, FilterEndpoints: map[string]filter.Mode{"G.list": filter.ModeCsap}})
	_, csapMask := maskFor(t, filter.ModeCsap)
	ep := d.Snapshot().Endpoint("GET", "/m")
	if ep.ResponseMask == nil || render(t, ep.ResponseMask, sample) != render(t, csapMask, sample) {
		t.Fatal("endpoint override to csap should filter like csap mode")
	}
}

func TestShouldFilterDefault(t *testing.T) {
	d := New(Options{})
	for status, want := range map[int]bool{200: true, 201: true, 204: true, 299: true, 301: false, 400: false, 404: false, 500: false} {
		if got := d.ShouldFilter(nil, status); got != want {
			t.Errorf("status %d: got %v", status, got)
		}
	}
	d = New(Options{ShouldFilter: func(_ *http.Request, status int) bool { return status == 400 }})
	if d.ShouldFilter(nil, 200) || !d.ShouldFilter(nil, 400) {
		t.Fatal("custom ShouldFilter not used")
	}
}
