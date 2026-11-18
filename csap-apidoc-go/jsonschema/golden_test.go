package jsonschema

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// 黄金样本(Java 与 Go 共用,docs/design/1.4.0-mock-schema.md 4.5 节):读同一个目录,不复制,按 JSON 值相等比较。
const goldenDir = "../../docs/design/1.4.0-mock-golden/cases"

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

func TestGoldenSchemas(t *testing.T) {
	files, _ := filepath.Glob(filepath.Join(goldenDir, "*.method.json"))
	if len(files) == 0 {
		t.Skip("没有黄金样本 " + goldenDir + ",跳过")
	}
	if len(files) < 2 {
		t.Fatalf("黄金样本应不少于 2 个,实际 %d", len(files))
	}
	for _, f := range files {
		name := strings.TrimSuffix(filepath.Base(f), ".method.json")
		t.Run(name, func(t *testing.T) {
			raw, _ := os.ReadFile(f)
			var m model.Method
			if err := json.Unmarshal(raw, &m); err != nil {
				t.Fatal(err)
			}
			expRaw, _ := os.ReadFile(strings.TrimSuffix(f, ".method.json") + ".schema.json")
			var want any
			if err := json.Unmarshal(expRaw, &want); err != nil {
				t.Fatal(err)
			}
			got := jsonValue(t, Method(&m))
			if !reflect.DeepEqual(got, want) {
				gb, _ := json.MarshalIndent(got, "", " ")
				wb, _ := json.MarshalIndent(want, "", " ")
				t.Fatalf("与黄金样本不一致\n--- got ---\n%s\n--- want ---\n%s", gb, wb)
			}
		})
	}
}
