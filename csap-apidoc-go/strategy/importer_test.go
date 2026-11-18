package strategy

import (
	"context"
	"reflect"
	"testing"
)

func TestImport(t *testing.T) {
	ctx := context.Background()
	tr := true
	r := func(p string) MethodRules { return MethodRules{Response: map[string]FieldRule{p: {Include: &tr}}} }
	src := NewMemory(Rules{"G.a": r("a"), "G.b": r("b"), "G.c": r("c"), "G.empty": {}})
	dst := NewMemory(Rules{"G.b": r("b"), "G.c": r("other")})

	// dry-run:只计算
	res, err := Import(ctx, src, dst, ImportOptions{DryRun: true})
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(res.Created, []string{"G.a"}) || !reflect.DeepEqual(res.Unchanged, []string{"G.b"}) ||
		!reflect.DeepEqual(res.Conflicts, []string{"G.c"}) || len(res.Updated) != 0 {
		t.Fatalf("dry-run: %+v", res)
	}
	if got, _ := dst.Load(ctx); len(got) != 2 {
		t.Fatalf("dry-run 不应写入: %v", got)
	}

	// 默认:新增 G.a,G.c 冲突不覆盖
	if res, err = Import(ctx, src, dst, ImportOptions{}); err != nil || len(res.Created) != 1 {
		t.Fatalf("%+v %v", res, err)
	}
	got, _ := dst.Load(ctx)
	if _, ok := got["G.c"].Response["other"]; !ok || len(got) != 3 {
		t.Fatalf("冲突的规则被覆盖了: %v", got)
	}
	// 再导入一次:全部相同或冲突,不写入
	if res, _ = Import(ctx, src, dst, ImportOptions{}); len(res.Created) != 0 || len(res.Unchanged) != 2 || len(res.Conflicts) != 1 {
		t.Fatalf("again: %+v", res)
	}
	// Overwrite
	if res, _ = Import(ctx, src, dst, ImportOptions{Overwrite: true}); !reflect.DeepEqual(res.Updated, []string{"G.c"}) || len(res.Conflicts) != 0 {
		t.Fatalf("overwrite: %+v", res)
	}
	got, _ = dst.Load(ctx)
	if _, ok := got["G.c"].Response["c"]; !ok {
		t.Fatalf("overwrite 没生效: %v", got["G.c"])
	}
}
