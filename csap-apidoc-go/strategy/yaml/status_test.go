package yaml_test

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
)

// 状态写进 method-field 文件;只有状态的接口不被当空规则删除;@ 开头的分组不是接口规则。
func TestStatusAndReservedGroups(t *testing.T) {
	ctx := context.Background()
	dir := t.TempDir()
	file := filepath.Join(dir, "U-method-field.yaml")
	src := "# 保留注释\n'@templates':\n  x: {request: {a: {required: true}}}\nG:\n  get:\n    response:\n      id: {include: true}\n"
	if err := os.WriteFile(file, []byte(src), 0o644); err != nil {
		t.Fatal(err)
	}
	s := yaml.New(yaml.Options{Dir: dir, Poll: -1, History: "-"})
	rs, err := s.Load(ctx)
	if err != nil || len(rs) != 1 {
		t.Fatalf("@ 开头的分组应跳过: %v %v", rs, err)
	}
	if _, err := s.Save(ctx, "G.old", strategy.MethodRules{Status: "DEPRECATED", StatusNote: "请改用 /v2"}, "u"); err != nil {
		t.Fatal(err)
	}
	b, _ := os.ReadFile(file)
	if !strings.Contains(string(b), "status: DEPRECATED") || !strings.Contains(string(b), "statusNote: 请改用 /v2") || !strings.Contains(string(b), "@templates") {
		t.Fatalf("%s", b)
	}
	rs, _ = s.Load(ctx)
	if r := rs["G.old"]; r.Status != "DEPRECATED" || r.StatusNote != "请改用 /v2" {
		t.Fatalf("%+v", r)
	}
	// 给 G.get 只设状态:字段规则保留
	cur := rs["G.get"]
	cur.Status = "FINISH"
	if _, err := s.Save(ctx, "G.get", cur, "u"); err != nil {
		t.Fatal(err)
	}
	rs, _ = s.Load(ctx)
	if rs["G.get"].Status != "FINISH" || len(rs["G.get"].Response) != 1 {
		t.Fatalf("%+v", rs["G.get"])
	}
	// 清除状态与字段规则才算删除
	if _, err := s.Save(ctx, "G.old", strategy.MethodRules{}, "u"); err != nil {
		t.Fatal(err)
	}
	if rs, _ = s.Load(ctx); len(rs) != 1 {
		t.Fatalf("%v", rs)
	}
}
