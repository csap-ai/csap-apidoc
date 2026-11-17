package yaml

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

func TestHistoryAndRollback(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "UserController-method-field.yaml", javaSample)
	ctx := context.Background()
	s := Dir(dir)
	tr := true
	newRules := strategy.MethodRules{Response: map[string]strategy.FieldRule{"data.rows.name": {Include: &tr}}}
	if _, err := s.Save(ctx, "com.example.UserController.list", newRules, "alice"); err != nil {
		t.Fatal(err)
	}
	h, err := s.History(ctx, "com.example.UserController.list", 0)
	if err != nil || len(h) != 1 {
		t.Fatalf("history: %+v %v", h, err)
	}
	c := h[0]
	if c.UpdatedBy != "alice" || c.Before == nil || c.After == nil {
		t.Fatalf("change: %+v", c)
	}
	if _, ok := c.Before.Response["data.rows.id"]; !ok {
		t.Fatalf("before should hold original rules: %+v", c.Before)
	}
	// 记录文件以点开头,不被当成规则文件
	if _, err := os.Stat(filepath.Join(dir, HistoryFile)); err != nil {
		t.Fatal(err)
	}
	rules, err := s.Load(ctx)
	if err != nil || len(rules) != 2 {
		t.Fatalf("load: %d %v", len(rules), err)
	}

	// 新实例(模拟重启)也能看到记录并回滚
	s2 := Dir(dir)
	if _, err := s2.Rollback(ctx, c.ID, "bob"); err != nil {
		t.Fatal(err)
	}
	rules, _ = s2.Load(ctx)
	if _, ok := rules["com.example.UserController.list"].Response["data.rows.id"]; !ok {
		t.Fatalf("rollback did not restore: %+v", rules["com.example.UserController.list"])
	}
	all, _ := s2.History(ctx, "", 0)
	if len(all) != 2 || all[0].UpdatedBy != "bob" {
		t.Fatalf("rollback record: %+v", all)
	}

	// 新建接口的修改,回滚后删除
	v, _ := s2.Save(ctx, "com.example.UserController.added", newRules, "x")
	all, _ = s2.History(ctx, "com.example.UserController.added", 1)
	if all[0].Before != nil || all[0].Version != v {
		t.Fatalf("added: %+v", all[0])
	}
	if _, err := s2.Rollback(ctx, all[0].ID, "x"); err != nil {
		t.Fatal(err)
	}
	rules, _ = s2.Load(ctx)
	if _, ok := rules["com.example.UserController.added"]; ok {
		t.Fatal("rollback of a creation should delete")
	}
}

func TestHistoryOptions(t *testing.T) {
	ctx := context.Background()
	dir := t.TempDir()
	mem := New(Options{Dir: dir, History: "-"})
	tr := true
	r := strategy.MethodRules{Response: map[string]strategy.FieldRule{"data": {Include: &tr}}}
	if _, err := mem.Save(ctx, "a.B.m", r, ""); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dir, HistoryFile)); !os.IsNotExist(err) {
		t.Fatal("memory history should not write a file")
	}
	if h, _ := mem.History(ctx, "", 0); len(h) != 1 {
		t.Fatalf("memory history: %+v", h)
	}
	custom := filepath.Join(t.TempDir(), "log", "h.jsonl")
	cs := New(Options{Dir: dir, History: custom})
	_, _ = cs.Save(ctx, "a.B.m", r, "")
	if _, err := os.Stat(custom); err != nil {
		t.Fatal(err)
	}
	ro := FS(os.DirFS(dir), ".")
	if h, err := ro.History(ctx, "", 0); err != nil || len(h) != 0 {
		t.Fatalf("read-only history: %+v %v", h, err)
	}
	if _, err := ro.Rollback(ctx, 1, ""); err == nil {
		t.Fatal("read-only rollback should fail")
	}
}
