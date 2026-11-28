package yaml_test

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
)

type user struct {
	ID    int64  `json:"id"`
	Name  string `json:"name"`
	Phone string `json:"phone"`
}

// TestHotReloadEndToEnd:改 YAML 文件后,不重启,几秒内文档和返回过滤都按新规则生效。
func TestHotReloadEndToEnd(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "UserController-method-field.yaml")
	write := func(s string) {
		if err := os.WriteFile(file, []byte(s), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	write("example.UserController:\n  get:\n    response:\n      id: {include: true}\n")

	d := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{yaml.New(yaml.Options{Dir: dir, Poll: 20 * time.Millisecond})}})
	d.Register("GET", "/users/:id", apidoc.Op("example.UserController", "get").Resp(user{}), "")
	fields := func() int {
		return len(d.Snapshot().Method("example.UserController", "example.UserController.get").Response[0].Parameters)
	}
	if n := fields(); n != 1 {
		t.Fatalf("initial: %d fields", n)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() { _ = d.Watch(ctx) }()

	time.Sleep(30 * time.Millisecond)
	write("example.UserController:\n  get:\n    response:\n      id: {include: true}\n      name: {include: true}\n")
	deadline := time.Now().Add(3 * time.Second)
	for fields() != 2 {
		if time.Now().After(deadline) {
			t.Fatal("YAML change not picked up")
		}
		time.Sleep(10 * time.Millisecond)
	}
	if d.Snapshot().Endpoint("GET", "/users/:id").ResponseMask == nil {
		t.Fatal("mask should exist")
	}
}

type listReq struct {
	Limit int `form:"limit"`
}

// 早期版本写出的 YAML 里,单值参数的请求规则键是纯参数名(limit:),没有 limit.limit。文档说明和校验规则都要生效。
func TestPlainKeyBasicParamFromYAML(t *testing.T) {
	dir := t.TempDir()
	yml := "example.UserController:\n  list:\n    request:\n      limit: {required: false, description: \"返回数量,最大1000\", validate: [{type: Pattern, pattern: \"^([1-9][0-9]{0,2}|1000)$\", code: \"1002\", message: \"最大1000\"}]}\n"
	if err := os.WriteFile(filepath.Join(dir, "UserController-method-field.yaml"), []byte(yml), 0o644); err != nil {
		t.Fatal(err)
	}
	d := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{yaml.New(yaml.Options{Dir: dir})}})
	d.Register("GET", "/users", apidoc.Op("example.UserController", "list").Req(listReq{}), "")
	snap := d.Snapshot()
	p := snap.Method("example.UserController", "example.UserController.list").Request[0].Parameters[0]
	if p.Description != "返回数量,最大1000" || len(p.Validate) != 1 {
		t.Fatalf("plain YAML key not applied: %+v", p)
	}
	ep := snap.Endpoint("GET", "/users")
	if len(ep.Checks) != 1 || len(ep.Checks[0].Rules) != 1 || ep.Checks[0].Rules[0].Code != "1002" {
		t.Fatalf("checks: %+v", ep.Checks)
	}
}
