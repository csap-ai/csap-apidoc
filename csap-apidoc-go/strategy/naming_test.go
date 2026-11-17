package strategy

import (
	"context"
	"testing"
	"time"
)

func TestNaming(t *testing.T) {
	under := map[string]string{"leagueId": "league_id", "userID": "user_id", "HTTPServer": "http_server", "a1B": "a1_b", "id": "id", "": ""}
	for in, want := range under {
		if got := ToUnderline(in); got != want {
			t.Errorf("ToUnderline(%q)=%q want %q", in, got, want)
		}
	}
	camel := map[string]string{"league_id": "leagueId", "last_updated_at": "lastUpdatedAt", "_x": "x", "id": "id"}
	for in, want := range camel {
		if got := ToCamel(in); got != want {
			t.Errorf("ToCamel(%q)=%q want %q", in, got, want)
		}
	}
	if UnderlinePath("data.rows.leagueId") != "data.rows.league_id" || CamelPath("data.last_id") != "data.lastId" {
		t.Error("path conversion")
	}
}

func TestLookup(t *testing.T) {
	m := map[string]FieldRule{"data.rows.leagueId": {Description: "camel"}, "data.home_team": {Description: "under"}}
	for path, want := range map[string]string{
		"data.rows.leagueId": "camel", "data.rows.league_id": "camel", "data.homeTeam": "under", "data.home_team": "under",
	} {
		r, ok := Lookup(m, path)
		if !ok || r.Description != want {
			t.Errorf("Lookup(%s) = %v %v", path, r, ok)
		}
	}
	if _, ok := Lookup(m, "nope"); ok {
		t.Error("nope")
	}
	if _, ok := Lookup(nil, "x"); ok {
		t.Error("nil map")
	}
}

func TestMemory(t *testing.T) {
	m := NewMemory(nil)
	if m.Name() != "memory" {
		t.Fatal(m.Name())
	}
	ctx, cancel := context.WithCancel(context.Background())
	got := make(chan struct{}, 1)
	done := make(chan error)
	go func() { done <- m.Watch(ctx, func() { got <- struct{}{} }) }()
	time.Sleep(10 * time.Millisecond)
	v, _ := m.Save(ctx, "G.m", MethodRules{}, "me")
	if v != 1 {
		t.Fatalf("version %d", v)
	}
	select {
	case <-got:
	case <-time.After(time.Second):
		t.Fatal("watch not notified")
	}
	r, _ := m.Load(ctx)
	if _, ok := r["G.m"]; !ok {
		t.Fatal("saved rule missing")
	}
	cancel()
	if err := <-done; err == nil {
		t.Fatal("watch should return ctx error")
	}
}
