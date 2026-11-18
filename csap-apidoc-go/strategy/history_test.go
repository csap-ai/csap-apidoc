package strategy

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func yes() *bool { b := true; return &b }

func TestJournalFilePersistsAndSkipsBrokenLines(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sub", "h.jsonl")
	j := NewJournal(path)
	r := MethodRules{Response: map[string]FieldRule{"data.id": {Include: yes()}}}
	for i := 0; i < 3; i++ {
		c, err := j.Append(Change{Key: "G.m", After: RulesPtr(r), UpdatedBy: "u"})
		if err != nil || c.ID != int64(i+1) {
			t.Fatalf("append %d: %+v %v", i, c, err)
		}
	}
	if _, err := j.Append(Change{Key: "G.n"}); err != nil {
		t.Fatal(err)
	}
	// 模拟写了半行
	f, _ := os.OpenFile(path, os.O_APPEND|os.O_WRONLY, 0)
	_, _ = f.WriteString(`{"id":9,"key":`)
	f.Close()

	j2 := NewJournal(path)
	all, err := j2.List("", 0)
	if err != nil || len(all) != 4 || all[0].Key != "G.n" || all[3].ID != 1 {
		t.Fatalf("list: %+v %v", all, err)
	}
	only, _ := j2.List("G.m", 2)
	if len(only) != 2 || only[0].ID != 3 {
		t.Fatalf("filtered: %+v", only)
	}
	c, err := j2.Get(2)
	if err != nil || c.After == nil || *c.After.Response["data.id"].Include != true {
		t.Fatalf("get: %+v %v", c, err)
	}
	if _, err := j2.Get(42); !errors.Is(err, ErrNoChange) {
		t.Fatalf("missing: %v", err)
	}
	c5, _ := j2.Append(Change{Key: "G.m"})
	if c5.ID != 5 {
		t.Fatalf("next id after reload: %d", c5.ID)
	}
}

func TestMemoryHistoryAndRollback(t *testing.T) {
	ctx := context.Background()
	m := NewMemory(nil)
	r1 := MethodRules{Response: map[string]FieldRule{"data.id": {Include: yes()}}}
	r2 := MethodRules{Response: map[string]FieldRule{"data.name": {Include: yes()}}}
	_, _ = m.Save(ctx, "G.m", r1, "a")
	_, _ = m.Save(ctx, "G.m", r2, "b")
	h, _ := m.History(ctx, "G.m", 0)
	if len(h) != 2 || h[0].UpdatedBy != "b" || h[0].Before == nil || h[1].Before != nil {
		t.Fatalf("history: %+v", h)
	}
	// 回滚第二次修改 -> 回到 r1
	if _, err := m.Rollback(ctx, h[0].ID, "c"); err != nil {
		t.Fatal(err)
	}
	rules, _ := m.Load(ctx)
	if _, ok := rules["G.m"].Response["data.id"]; !ok {
		t.Fatalf("rollback: %+v", rules["G.m"])
	}
	// 回滚第一次修改 -> 删除
	if _, err := m.Rollback(ctx, h[1].ID, "c"); err != nil {
		t.Fatal(err)
	}
	rules, _ = m.Load(ctx)
	if _, ok := rules["G.m"]; ok {
		t.Fatal("rollback to nothing should delete")
	}
	h, _ = m.History(ctx, "", 0)
	if len(h) != 4 || h[0].After != nil || h[0].UpdatedBy != "c" {
		t.Fatalf("rollback records: %+v", h[0])
	}
	if _, err := m.Rollback(ctx, 99, "c"); !errors.Is(err, ErrNoChange) {
		t.Fatalf("missing change: %v", err)
	}
}

func TestClone(t *testing.T) {
	r := MethodRules{Request: map[string]FieldRule{"a.b": {Validate: []ValidateRule{{Type: "NotNull"}}}}}
	c := Clone(r)
	c.Request["a.b"].Validate[0].Type = "x"
	if r.Request["a.b"].Validate[0].Type != "NotNull" {
		t.Fatal("clone shares validate slice")
	}
	if RulesPtr(MethodRules{}) != nil {
		t.Fatal("empty rules should be nil")
	}
}
