package integration

import (
	"context"
	"encoding/json"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

func keys(m map[string]any) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	return out
}

func subset(t *testing.T, what string, got map[string]any, allowed ...string) {
	t.Helper()
	ok := map[string]bool{}
	for _, a := range allowed {
		ok[a] = true
	}
	for k := range got {
		if !ok[k] {
			t.Fatalf("%s 含约定之外的键 %q(约定: %v)", what, k, allowed)
		}
	}
}

// TestJavaInterop 断言 Go 写入的行与 Java 1.4.0 数据库模式的约定一致,并能读取 Java 风格写入的行。
// 约定(docs/design/database-mode.md):
//   - rule(service, rule_key, rules, version, updated_by, updated_at),时间为毫秒;
//   - history(id, service, rule_key, before_rules, after_rules, version, updated_by, updated_at);
//   - meta(service, version);
//   - rules 为 {"request":{字段键:{...}},"response":{...}},validate 项为 {type,pattern,code,message,descr,level}。
func TestJavaInterop(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx := context.Background()
			db := c.open(t)
			p := prefix(t, db)
			s := newSvc(db, c, p, "order-service")
			if err := s.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			rules := strategy.MethodRules{
				Request: map[string]strategy.FieldRule{"req.username": {Required: model.Bool(true), ParamType: "body", Description: "用户名",
					Validate: []strategy.ValidateRule{{Type: "NotEmpty", Pattern: "", Code: "1001", Message: "不能为空", Descr: "d", Level: 1}}}},
				Response: map[string]strategy.FieldRule{"data.id": {Include: model.Bool(true)}},
			}
			if _, err := s.Save(ctx, "UserGroup.login", rules, "alice"); err != nil {
				t.Fatal(err)
			}

			// Java 按约定的列名读取
			var service, key, raw, by string
			var ver, at int64
			err := db.QueryRow(ph(c.dialect, "SELECT service, rule_key, rules, version, updated_by, updated_at FROM "+p+"rule WHERE service = ? AND rule_key = ?"),
				"order-service", "UserGroup.login").Scan(&service, &key, &raw, &ver, &by, &at)
			if err != nil {
				t.Fatal(err)
			}
			if ver != 1 || by != "alice" || at < 1_000_000_000_000 {
				t.Fatalf("row: ver=%d by=%q at=%d(应为毫秒)", ver, by, at)
			}
			var doc map[string]any
			if err := json.Unmarshal([]byte(raw), &doc); err != nil {
				t.Fatal(err)
			}
			subset(t, "rules", doc, "request", "response")
			req := doc["request"].(map[string]any)["req.username"].(map[string]any)
			subset(t, "字段规则", req, "description", "required", "include", "hidden", "paramType", "example", "validate")
			if req["required"] != true || req["paramType"] != "body" {
				t.Fatalf("field: %v", req)
			}
			v0 := req["validate"].([]any)[0].(map[string]any)
			if len(v0) != 6 {
				t.Fatalf("validate 项应恰有 type/pattern/code/message/descr/level,实际 %v", keys(v0))
			}
			subset(t, "validate", v0, "type", "pattern", "code", "message", "descr", "level")
			if _, ok := v0["level"].(float64); !ok {
				t.Fatalf("level 应为数字: %T", v0["level"])
			}
			var hv, hat int64
			var hservice, hkey, hby string
			var hid int64
			var before, after *string
			if err := db.QueryRow(ph(c.dialect, "SELECT id, service, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM "+p+"history WHERE service = ?"),
				"order-service").Scan(&hid, &hservice, &hkey, &before, &after, &hv, &hby, &hat); err != nil {
				t.Fatal(err)
			}
			if before != nil || after == nil || *after != raw || hv != 1 || hat != at {
				t.Fatalf("history row: before=%v after=%v", before, after)
			}
			var mv int64
			if err := db.QueryRow(ph(c.dialect, "SELECT version FROM "+p+"meta WHERE service = ?"), "order-service").Scan(&mv); err != nil || mv != 1 {
				t.Fatalf("meta %d %v", mv, err)
			}

			// Java 风格写入的行(规则键为 类全名.方法名,JSON 带 null 和多余字段)能被 Go 读取
			javaJSON := `{"request":{"form.name":{"required":true,"include":null,"paramType":"","description":"名称",` +
				`"validate":[{"type":"Pattern","pattern":"^[a-z]+$","code":"2001","message":"格式不对","descr":"","level":2}]}},"response":null}`
			for _, st := range []string{
				"INSERT INTO " + p + "rule (service, rule_key, rules, version, updated_by, updated_at) VALUES ('order-service', 'com.example.UserController.save', '" + javaJSON + "', 2, 'java', 1700000000000)",
				"UPDATE " + p + "meta SET version = 2 WHERE service = 'order-service'",
			} {
				if _, err := db.Exec(st); err != nil {
					t.Fatal(err)
				}
			}
			loaded, err := s.Load(ctx)
			if err != nil {
				t.Fatal(err)
			}
			got := loaded["com.example.UserController.save"].Request["form.name"]
			if got.Required == nil || !*got.Required || got.Description != "名称" || len(got.Validate) != 1 ||
				got.Validate[0].Type != "Pattern" || got.Validate[0].Code != "2001" || got.Validate[0].Level != 2 {
				t.Fatalf("java row: %+v", got)
			}
			if v, _ := s.Version(ctx); v != 2 {
				t.Fatalf("version %d", v)
			}
			// Java 写的行再由 Go 保存时,版本号接着加
			if v, err := s.Save(ctx, "com.example.UserController.save", rr("data"), "go"); err != nil || v != 3 {
				t.Fatalf("save %d %v", v, err)
			}
		})
	}
}

// TestSchemaColumns 检查 SQLite 实际建出的列与约定逐一对应(类型约定另见 strategy/sql 的单元测试)。
func TestSchemaColumns(t *testing.T) {
	cs := cases(t)
	db := cs[0].open(t) // SQLite
	p := prefix(t, db)
	s := newSvc(db, cs[0], p, "x")
	if err := s.EnsureSchema(context.Background()); err != nil {
		t.Fatal(err)
	}
	want := map[string]string{
		"rule":    "service,rule_key,rules,version,updated_by,updated_at",
		"history": "id,service,rule_key,before_rules,after_rules,version,updated_by,updated_at",
		"meta":    "service,version",
	}
	for tbl, cols := range want {
		rows, err := db.Query("SELECT name FROM pragma_table_info('" + p + tbl + "')")
		if err != nil {
			t.Fatal(err)
		}
		var got []string
		for rows.Next() {
			var n string
			_ = rows.Scan(&n)
			got = append(got, n)
		}
		rows.Close()
		if strings.Join(got, ",") != cols {
			t.Fatalf("%s 列: got %v want %s", tbl, got, cols)
		}
	}
}
