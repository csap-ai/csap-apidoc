package sql

import (
	"database/sql"
	"strings"
	"testing"
)

func TestDefaults(t *testing.T) {
	s := New(Options{})
	if s.opts.Service != DefaultService || s.opts.TablePrefix != "csap_apidoc_" {
		t.Fatalf("%+v", s.opts)
	}
	if err := New(Options{Service: strings.Repeat("a", 129)}).checkService(); err == nil {
		t.Fatal("service > 128 should fail")
	}
}

func TestPlaceholders(t *testing.T) {
	for d, want := range map[Dialect]string{
		SQLite: "a = ? AND b = ?", MySQL: "a = ? AND b = ?", DM: "a = ? AND b = ?",
		Postgres: "a = $1 AND b = $2", Kingbase: "a = $1 AND b = $2",
	} {
		if got := New(Options{Dialect: d}).q("a = ? AND b = ?"); got != want {
			t.Errorf("dialect %d: %s", d, got)
		}
	}
}

// 各方言的类型约定与 Java 一致。
func TestSchemaTypes(t *testing.T) {
	cases := []struct {
		d          Dialect
		text, auto string
	}{
		{SQLite, "rules TEXT NOT NULL", "id INTEGER PRIMARY KEY AUTOINCREMENT"},
		{Postgres, "rules TEXT NOT NULL", "id BIGSERIAL PRIMARY KEY"},
		{Kingbase, "rules TEXT NOT NULL", "id BIGSERIAL PRIMARY KEY"},
		{MySQL, "rules LONGTEXT NOT NULL", "id BIGINT AUTO_INCREMENT PRIMARY KEY"},
		{DM, "rules CLOB NOT NULL", "id BIGINT IDENTITY(1, 1) PRIMARY KEY"},
	}
	for _, c := range cases {
		all := strings.Join(New(Options{Dialect: c.d}).Schema(), "\n")
		for _, want := range []string{c.text, c.auto, "PRIMARY KEY (service, rule_key)", "service VARCHAR(128) NOT NULL PRIMARY KEY",
			"history_key ON csap_apidoc_history (service, rule_key, id)"} {
			if !strings.Contains(all, want) {
				t.Errorf("dialect %d 缺少 %q\n%s", c.d, want, all)
			}
		}
		if strings.Contains(all, "DEFAULT ''") || strings.Contains(all, "IF NOT EXISTS") {
			t.Errorf("dialect %d: 不应出现 DEFAULT '' / IF NOT EXISTS", c.d)
		}
		if (c.d == MySQL) != strings.Contains(all, "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4") {
			t.Errorf("dialect %d: ENGINE 选项只属于 MySQL", c.d)
		}
	}
}

func TestMigrationSQL(t *testing.T) {
	all := strings.Join(New(Options{Service: "it's", TablePrefix: "t_"}).MigrationSQL(), ";\n")
	for _, want := range []string{"'it''s'", "CREATE TABLE t_rule_v2 (", "ALTER TABLE t_rule_v2 RENAME TO t_rule",
		"ADD COLUMN service", "DROP INDEX t_history_key", "CREATE TABLE t_meta_v2 ("} {
		if !strings.Contains(all, want) {
			t.Errorf("缺少 %q\n%s", want, all)
		}
	}
	if !strings.Contains(strings.Join(New(Options{Dialect: MySQL}).MigrationSQL(), "\n"), "DROP INDEX csap_apidoc_history_key ON csap_apidoc_history") {
		t.Error("MySQL 删索引要带 ON 表名")
	}
}

func TestSameContent(t *testing.T) {
	valid := func(s string) sql.NullString { return sql.NullString{String: s, Valid: true} }
	cases := []struct {
		name   string
		before sql.NullString
		after  string
		want   bool
	}{
		{"完全相同", valid(`{"a":1}`), `{"a":1}`, true},
		{"键顺序和空白不同", valid(`{"b":[1, 2],"a":{"x":true}}`), "{ \"a\": {\"x\": true},\n \"b\": [1,2] }", true},
		{"内容不同", valid(`{"a":1}`), `{"a":2}`, false},
		{"数组顺序不同", valid(`[1,2]`), `[2,1]`, false},
		{"不存在 + 新增", sql.NullString{}, `{"a":1}`, false},
		{"不存在 + 删除", sql.NullString{}, ``, true},
		{"存在 + 删除", valid(`{"a":1}`), ``, false},
		{"库里不是 JSON", valid(`oops`), `{"a":1}`, false},
	}
	for _, c := range cases {
		if got := sameContent(c.before, c.after); got != c.want {
			t.Errorf("%s: got %v want %v", c.name, got, c.want)
		}
	}
}
