package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const (
	javaDoc = "../../apidiff/testdata/java-example.json"
	goDoc   = "../../apidiff/testdata/go-example.json"
)

func exec(args ...string) (int, string, string) {
	var out, errb bytes.Buffer
	code := run(args, &out, &errb)
	return code, out.String(), errb.String()
}

// 去掉 Go 示例的一个返回字段,写成新文件。
func brokenGo(t *testing.T) string {
	t.Helper()
	b, _ := os.ReadFile(goDoc)
	var root map[string]any
	_ = json.Unmarshal(b, &root)
	m := root["data"].(map[string]any)["apiList"].([]any)[0].(map[string]any)["methodList"].([]any)[0].(map[string]any)
	resp := m["response"].([]any)[0].(map[string]any)
	resp["parameters"] = resp["parameters"].([]any)[1:]
	nb, _ := json.Marshal(root)
	p := filepath.Join(t.TempDir(), "new.json")
	_ = os.WriteFile(p, nb, 0o644)
	return p
}

func TestSnapshotAndDiffFlow(t *testing.T) {
	for name, doc := range map[string]string{"java": javaDoc, "go": goDoc} {
		t.Run(name, func(t *testing.T) {
			snap := filepath.Join(t.TempDir(), "baseline.json")
			if code, _, e := exec("snapshot", "-o", snap, doc); code != 0 {
				t.Fatalf("snapshot %d %s", code, e)
			}
			// 快照对原文档:没有差异,退出码 0
			if code, out, _ := exec(snap, doc); code != 0 || !strings.Contains(out, "没有破坏性变更") {
				t.Fatalf("%d %s", code, out)
			}
			// 显式 diff 子命令
			if code, _, _ := exec("diff", snap, doc); code != 0 {
				t.Fatalf("diff %d", code)
			}
		})
	}
}

func TestExitCodesAndFormats(t *testing.T) {
	bad := brokenGo(t)
	code, out, _ := exec(goDoc, bad)
	if code != 1 || !strings.Contains(out, "field.removed") {
		t.Fatalf("%d %s", code, out)
	}
	if code, _, _ := exec("-fail-on", "never", goDoc, bad); code != 0 {
		t.Fatalf("never: %d", code)
	}
	// 忽略清单放行
	ig := filepath.Join(t.TempDir(), "ignore.txt")
	_ = os.WriteFile(ig, []byte("example.UserController.list field.removed *\n"), 0o644)
	if code, out, _ := exec("-ignore", ig, goDoc, bad); code != 0 || !strings.Contains(out, "已忽略 1") {
		t.Fatalf("ignore: %d %s", code, out)
	}
	// 三种格式
	_, js, _ := exec("-format", "json", goDoc, bad)
	var rep struct {
		Summary struct{ Breaking int } `json:"summary"`
	}
	if err := json.Unmarshal([]byte(js), &rep); err != nil || rep.Summary.Breaking != 1 {
		t.Fatalf("json: %v %s", err, js)
	}
	_, md, _ := exec("-format", "markdown", goDoc, bad)
	if !strings.Contains(md, "## API 变更检查") || !strings.Contains(md, "| 接口 |") {
		t.Fatalf("markdown: %s", md)
	}
	// -o 写文件
	o := filepath.Join(t.TempDir(), "r.md")
	if code, out, _ := exec("-format", "md", "-o", o, goDoc, bad); code != 1 || out != "" {
		t.Fatalf("-o: %d %q", code, out)
	}
	if b, _ := os.ReadFile(o); !strings.Contains(string(b), "API 变更检查") {
		t.Fatal("报告没写进文件")
	}
}

func TestUsageErrors(t *testing.T) {
	for _, args := range [][]string{
		{}, {goDoc}, {"-format", "xml", goDoc, goDoc}, {"-fail-on", "info", goDoc, goDoc},
		{"-min-level", "x", goDoc, goDoc}, {"/no/such/file.json", goDoc}, {"snapshot"}, {"-ignore", "/no/such", goDoc, goDoc},
	} {
		if code, _, _ := exec(args...); code != 2 {
			t.Errorf("%v: 期望退出码 2,实际 %d", args, code)
		}
	}
	if code, out, _ := exec("-h"); code != 0 || !strings.Contains(out, "用法") {
		t.Fatal("-h")
	}
}
