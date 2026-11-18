package redis

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

// Go 内嵌的脚本必须与 Java 版逐字节一致(约定见 docs/design/redis-mode.md)。仓库里没有 Java 那份时跳过。
func TestSaveScriptMatchesJava(t *testing.T) {
	p := filepath.Join("..", "..", "..", "csap-apidoc-strategy", "csap-apidoc-redis", "src", "main", "resources",
		"META-INF", "csap-apidoc", "redis", "save.lua")
	java, err := os.ReadFile(p)
	if os.IsNotExist(err) {
		t.Skipf("没有 Java 版脚本 %s,跳过", p)
	}
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(java, []byte(SaveScript)) {
		t.Fatalf("strategy/redis/save.lua 与 Java 版不一致;以 Java 为准,请逐字复制:\n  cp %s strategy/redis/save.lua", p)
	}
}

func TestHistoryLimitDefault(t *testing.T) {
	for in, want := range map[int]int{0: 200, 5: 5, -1: -1} {
		if got := New(Options{HistoryLimit: in}).opts.HistoryLimit; got != want {
			t.Errorf("HistoryLimit %d -> %d, want %d", in, got, want)
		}
	}
}
