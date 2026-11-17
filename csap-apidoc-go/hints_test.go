package apidoc

import (
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

func TestHeaderAndAuthHints(t *testing.T) {
	d := New(Options{
		HeaderHints: []model.GlobalHeaderHint{{Name: "X-Tenant-Id", Example: "1"}, {Name: "X-Trace-Id"}},
		Auth:        NewAuthHint("basic", "全局", "header", "ignored"),
	})
	d.Group(GroupInfo{Name: "g.A", HeaderHints: []model.GlobalHeaderHint{{Name: "X-Tenant-Id", Example: "2", Required: true}}})
	d.Group(GroupInfo{Name: "g.B", Auth: NewAuthHint("bearer", "分组", "", "")})
	d.Register("GET", "/a", Op("g.A", "a").HeaderHint("X-Trace-Id", "链路", "abc", false).APIKeyAuth("query", "token", "接口"), "")
	d.Register("GET", "/b", Op("g.B", "b"), "")
	d.Register("GET", "/c", Op("g.C", "c"), "")
	snap := d.Snapshot()

	a := snap.Method("g.A", "g.A.a")
	if len(a.GlobalHeaderHints) != 2 || a.GlobalHeaderHints[0].Example != "2" || !a.GlobalHeaderHints[0].Required ||
		a.GlobalHeaderHints[1].Example != "abc" {
		t.Fatalf("header hints: %+v", a.GlobalHeaderHints)
	}
	if a.AuthHint.Scheme != "apikey" || *a.AuthHint.In != "query" || *a.AuthHint.Name != "token" {
		t.Fatalf("auth: %+v", a.AuthHint)
	}
	if b := snap.Method("g.B", "g.B.b"); b.AuthHint.Scheme != "bearer" || b.AuthHint.In != nil {
		t.Fatalf("group auth: %+v", b.AuthHint)
	}
	c := snap.Method("g.C", "g.C.c")
	if c.AuthHint.Scheme != "basic" || c.AuthHint.Name != nil || len(c.GlobalHeaderHints) != 2 {
		t.Fatalf("global: %+v %+v", c.AuthHint, c.GlobalHeaderHints)
	}

	// 没有任何建议时:globalHeaderHints 输出 [],authHint 不输出(与 Java 一致)
	e := New(Options{})
	e.Register("GET", "/x", Op("g.X", "x"), "")
	out, err := model.MarshalCsap(e.Snapshot().Doc, 1)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(out), `"globalHeaderHints":[]`) || strings.Contains(string(out), "authHint") {
		t.Fatalf("encoding: %s", out)
	}
}
