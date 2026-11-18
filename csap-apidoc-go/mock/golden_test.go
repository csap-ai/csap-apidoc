package mock

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// 黄金样本(Java 与 Go 共用,docs/design/1.4.0-mock-schema.md 第 3 节):读同一个目录,不复制,按 JSON 值相等比较。
const goldenDir = "../../docs/design/1.4.0-mock-golden/cases"

type goldenInput struct {
	Seed      string         `json:"seed"`
	ArraySize *int           `json:"arraySize"`
	MaxDepth  int            `json:"maxDepth"`
	Heuristic *bool          `json:"heuristics"` // 缺省 true
	Response  []*model.Model `json:"response"`
}

func jsonValue(t *testing.T, v any) any {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	var out any
	if err := json.Unmarshal(b, &out); err != nil {
		t.Fatal(err)
	}
	return out
}

func TestGoldenCases(t *testing.T) {
	files, _ := filepath.Glob(filepath.Join(goldenDir, "*.input.json"))
	if len(files) == 0 {
		t.Skip("没有黄金样本 " + goldenDir + ",跳过")
	}
	if len(files) < 6 {
		t.Fatalf("黄金样本应不少于 6 个,实际 %d", len(files))
	}
	for _, f := range files {
		name := strings.TrimSuffix(filepath.Base(f), ".input.json")
		t.Run(name, func(t *testing.T) {
			var in goldenInput
			raw, err := os.ReadFile(f)
			if err != nil {
				t.Fatal(err)
			}
			if err := json.Unmarshal(raw, &in); err != nil {
				t.Fatal(err)
			}
			expRaw, err := os.ReadFile(strings.TrimSuffix(f, ".input.json") + ".expected.json")
			if err != nil {
				t.Fatal(err)
			}
			var want any
			if err := json.Unmarshal(expRaw, &want); err != nil {
				t.Fatal(err)
			}
			got, ok := Generate(&model.Method{Response: in.Response}, Options{Seed: in.Seed, ArraySize: in.ArraySize, MaxDepth: in.MaxDepth, DisableHeuristics: in.Heuristic != nil && !*in.Heuristic})
			if !ok {
				t.Fatal("没有返回模型")
			}
			if g := jsonValue(t, got); !reflect.DeepEqual(g, want) {
				gb, _ := json.MarshalIndent(g, "", " ")
				wb, _ := json.MarshalIndent(want, "", " ")
				t.Fatalf("与黄金样本不一致\n--- got ---\n%s\n--- want ---\n%s", gb, wb)
			}
		})
	}
}
