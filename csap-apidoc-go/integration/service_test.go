package integration

import (
	"context"
	"database/sql"
	"errors"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	sqlstrategy "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/sql"
)

// ph 把 ? 占位符换成方言写法(测试里直接执行原始 SQL 用)。
func ph(d sqlstrategy.Dialect, q string) string {
	if d != sqlstrategy.Postgres && d != sqlstrategy.Kingbase {
		return q
	}
	var b strings.Builder
	n := 0
	for _, r := range q {
		if r == '?' {
			n++
			b.WriteString("$" + string(rune('0'+n)))
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

func newSvc(db *sql.DB, c dbCase, prefix, service string) *sqlstrategy.Strategy {
	return sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: prefix, Service: service, Poll: -1})
}

func rr(path string) strategy.MethodRules {
	return strategy.MethodRules{Response: map[string]strategy.FieldRule{path: {Include: model.Bool(true)}}}
}

// 两个服务共用一个库、一套表:规则、版本号、修改记录、回滚互不干扰。
func TestServiceIsolation(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx := context.Background()
			db := c.open(t)
			p := prefix(t, db)
			a, b := newSvc(db, c, p, "order"), newSvc(db, c, p, "user")
			if err := a.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			if err := b.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			// 同一个规则键,两个服务各存各的
			if _, err := a.Save(ctx, "G.m", rr("a1"), "alice"); err != nil {
				t.Fatal(err)
			}
			if _, err := a.Save(ctx, "G.m", rr("a2"), "alice"); err != nil {
				t.Fatal(err)
			}
			if v, err := b.Save(ctx, "G.m", rr("b1"), ""); err != nil || v != 1 {
				t.Fatalf("b save: %d %v", v, err)
			}
			if v, _ := a.Version(ctx); v != 2 {
				t.Fatalf("a version %d", v)
			}
			if v, _ := b.Version(ctx); v != 1 {
				t.Fatalf("b version %d", v)
			}
			ra, _ := a.Load(ctx)
			rb, _ := b.Load(ctx)
			if _, ok := ra["G.m"].Response["a2"]; !ok || len(ra) != 1 || len(ra["G.m"].Response) != 1 {
				t.Fatalf("a rules: %+v", ra)
			}
			if _, ok := rb["G.m"].Response["b1"]; !ok || len(rb) != 1 {
				t.Fatalf("b rules: %+v", rb)
			}
			ha, _ := a.History(ctx, "", 0)
			hb, _ := b.History(ctx, "", 0)
			if len(ha) != 2 || len(hb) != 1 {
				t.Fatalf("history a=%d b=%d", len(ha), len(hb))
			}
			if hb[0].UpdatedBy != "unknown" {
				t.Fatalf("empty updated_by should be unknown, got %q", hb[0].UpdatedBy)
			}
			// 不能回滚别的服务的记录
			if _, err := a.Rollback(ctx, hb[0].ID, "x"); !errors.Is(err, strategy.ErrNoChange) {
				t.Fatalf("cross-service rollback: %v", err)
			}
			// a 回滚、删除都不影响 b
			if _, err := a.Rollback(ctx, ha[0].ID, "admin"); err != nil {
				t.Fatal(err)
			}
			if _, err := a.Save(ctx, "G.m", strategy.MethodRules{}, "alice"); err != nil {
				t.Fatal(err)
			}
			ra, _ = a.Load(ctx)
			rb, _ = b.Load(ctx)
			if len(ra) != 0 || len(rb) != 1 {
				t.Fatalf("after delete: a=%d b=%d", len(ra), len(rb))
			}
			if v, _ := b.Version(ctx); v != 1 {
				t.Fatalf("b version changed: %d", v)
			}
			// 默认服务名
			d := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: p, Poll: -1})
			if _, err := d.Save(ctx, "G.m", rr("d"), "x"); err != nil {
				t.Fatal(err)
			}
			var n int
			if err := db.QueryRow(ph(c.dialect, "SELECT COUNT(*) FROM "+p+"rule WHERE service = ?"), sqlstrategy.DefaultService).Scan(&n); err != nil || n != 1 {
				t.Fatalf("default service rows %d %v", n, err)
			}
		})
	}
}

// legacyDDL 是 v0.1.x 的旧表结构(逐字对应当时的 Schema)。
func legacyDDL(d sqlstrategy.Dialect, p string) []string {
	text, idCol := "TEXT", "id INTEGER PRIMARY KEY AUTOINCREMENT"
	switch d {
	case sqlstrategy.Postgres:
		idCol = "id BIGSERIAL PRIMARY KEY"
	case sqlstrategy.MySQL:
		text, idCol = "MEDIUMTEXT", "id BIGINT AUTO_INCREMENT PRIMARY KEY"
	}
	hist := "CREATE TABLE " + p + "history (\n" + idCol + ",\nrule_key VARCHAR(512) NOT NULL,\nbefore_rules " + text + ",\nafter_rules " + text +
		",\nversion BIGINT NOT NULL,\nupdated_by VARCHAR(128) NOT NULL DEFAULT '',\nupdated_at BIGINT NOT NULL"
	out := []string{
		"CREATE TABLE " + p + "rule (rule_key VARCHAR(512) NOT NULL PRIMARY KEY, rules " + text +
			" NOT NULL, version BIGINT NOT NULL, updated_by VARCHAR(128) NOT NULL DEFAULT '', updated_at BIGINT NOT NULL)",
		"CREATE TABLE " + p + "meta (id INT NOT NULL PRIMARY KEY, version BIGINT NOT NULL)",
	}
	if d == sqlstrategy.MySQL {
		out = append(out, hist+",\nKEY "+p+"history_key (rule_key, id)\n)")
	} else {
		out = append(out, hist+"\n)", "CREATE INDEX "+p+"history_key ON "+p+"history (rule_key, id)")
	}
	return out
}

const oldRules = `{"response":{"data.id":{"include":true}}}`

func seedLegacy(t *testing.T, db *sql.DB, c dbCase, p string) {
	t.Helper()
	stmts := append(legacyDDL(c.dialect, p),
		"INSERT INTO "+p+"rule (rule_key, rules, version, updated_by, updated_at) VALUES ('G.a', '"+oldRules+"', 4, 'alice', 1700000000000)",
		"INSERT INTO "+p+"rule (rule_key, rules, version, updated_by, updated_at) VALUES ('G.b', '"+oldRules+"', 5, '', 1700000001000)",
		"INSERT INTO "+p+"history (rule_key, before_rules, after_rules, version, updated_by, updated_at) VALUES ('G.a', NULL, '"+oldRules+"', 4, 'alice', 1700000000000)",
		"INSERT INTO "+p+"history (rule_key, before_rules, after_rules, version, updated_by, updated_at) VALUES ('G.b', NULL, '"+oldRules+"', 5, '', 1700000001000)",
		"INSERT INTO "+p+"meta (id, version) VALUES (1, 5)",
	)
	for _, s := range stmts {
		if _, err := db.Exec(s); err != nil {
			t.Fatalf("%v\n%s", err, s)
		}
	}
}

func checkMigrated(t *testing.T, s *sqlstrategy.Strategy) {
	t.Helper()
	ctx := context.Background()
	rules, err := s.Load(ctx)
	if err != nil || len(rules) != 2 {
		t.Fatalf("load %v %d", err, len(rules))
	}
	if v, err := s.Version(ctx); err != nil || v != 5 {
		t.Fatalf("version %d %v", v, err)
	}
	h, err := s.History(ctx, "", 0)
	if err != nil || len(h) != 2 || h[0].Key != "G.b" || h[0].UpdatedBy != "" && h[0].UpdatedBy != "unknown" {
		t.Fatalf("history %v %+v", err, h)
	}
	// 迁移后能继续保存、回滚,版本号接着旧的往下加
	if v, err := s.Save(ctx, "G.a", rr("x"), "bob"); err != nil || v != 6 {
		t.Fatalf("save after migrate %d %v", v, err)
	}
	if _, err := s.Rollback(ctx, h[1].ID, "bob"); err != nil { // 回滚旧记录:G.a 恢复成"没有规则"
		t.Fatal(err)
	}
}

func TestMigrateLegacy(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx := context.Background()
			db := c.open(t)
			p := prefix(t, db)
			seedLegacy(t, db, c, p)
			s := newSvc(db, c, p, "order")
			if err := s.EnsureSchema(ctx); !errors.Is(err, sqlstrategy.ErrLegacySchema) {
				t.Fatalf("want ErrLegacySchema, got %v", err)
			}
			if err := s.Migrate(ctx); err != nil {
				t.Fatal(err)
			}
			if err := s.Migrate(ctx); err != nil { // 幂等
				t.Fatal(err)
			}
			if err := s.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			// 旧数据全部归到 order;别的服务看不到
			other, _ := newSvc(db, c, p, "user").Load(ctx)
			if len(other) != 0 {
				t.Fatalf("other service sees %d rules", len(other))
			}
			var by string
			if err := db.QueryRow(ph(c.dialect, "SELECT updated_by FROM "+p+"rule WHERE service = ? AND rule_key = ?"), "order", "G.b").Scan(&by); err != nil || by != "unknown" {
				t.Fatalf("updated_by %q %v", by, err)
			}
			checkMigrated(t, s)
		})
	}
}

// MigrationSQL 供手工执行:执行后与 Migrate 的结果一致。
func TestMigrationSQLManual(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx := context.Background()
			db := c.open(t)
			p := prefix(t, db)
			seedLegacy(t, db, c, p)
			s := newSvc(db, c, p, "it's")
			for _, stmt := range s.MigrationSQL() {
				if _, err := db.Exec(stmt); err != nil {
					t.Fatalf("%v\n%s", err, stmt)
				}
			}
			if err := s.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			checkMigrated(t, s)
		})
	}
}

// svcBus 是带服务名的进程内通知。
type svcBus struct {
	chanBus
	mu2  sync.Mutex
	subs []func(string, int64)
}

func (b *svcBus) PublishService(_ context.Context, service string, v int64) error {
	b.mu2.Lock()
	subs := append([]func(string, int64){}, b.subs...)
	b.mu2.Unlock()
	for _, fn := range subs {
		fn(service, v)
	}
	return nil
}

func (b *svcBus) SubscribeService(ctx context.Context, fn func(string, int64)) error {
	b.mu2.Lock()
	b.subs = append(b.subs, fn)
	b.mu2.Unlock()
	<-ctx.Done()
	return ctx.Err()
}

// ServiceNotifier:只有同一个服务的实例被唤醒。
func TestServiceNotifier(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			db := c.open(t)
			p := prefix(t, db)
			bus := &svcBus{}
			mk := func(service string) (*sqlstrategy.Strategy, chan struct{}) {
				s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: p, Service: service, Poll: -1, Notifier: bus})
				ch := make(chan struct{}, 4)
				go func() { _ = s.Watch(ctx, func() { ch <- struct{}{} }) }()
				return s, ch
			}
			a, _ := mk("a")
			if err := a.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			_, chA2 := mk("a")
			_, chB := mk("b")
			time.Sleep(100 * time.Millisecond)
			if _, err := a.Save(ctx, "G.m", rr("x"), "u"); err != nil {
				t.Fatal(err)
			}
			select {
			case <-chA2:
			case <-time.After(2 * time.Second):
				t.Fatal("同服务的另一个实例没收到通知")
			}
			select {
			case <-chB:
				t.Fatal("别的服务不该被唤醒")
			case <-time.After(300 * time.Millisecond):
			}
		})
	}
}

// 状态随规则一起存取;只有状态的规则不会被当空规则删除;@ 开头的保留键加载时跳过。SQL 与 Redis 各跑一遍。
func TestStatusAndReservedKeySQLAndRedis(t *testing.T) {
	ctx := context.Background()
	type store interface {
		strategy.Store
		strategy.Historian
	}
	var stores []struct {
		name  string
		s     store
		raw   func(key, json string) error // 绕过 Save 直接写保留键
		clean func()
	}
	for _, c := range cases(t) {
		c := c
		db := c.open(t)
		p := prefix(t, db)
		s := newSvc(db, c, p, "st")
		if err := s.EnsureSchema(ctx); err != nil {
			t.Fatal(err)
		}
		stores = append(stores, struct {
			name  string
			s     store
			raw   func(key, json string) error
			clean func()
		}{c.name, s, func(key, js string) error {
			_, err := db.Exec(ph(c.dialect, "INSERT INTO "+p+"rule (service, rule_key, rules, version, updated_by, updated_at) VALUES (?, ?, ?, 1, 'x', 1)"), "st", key, js)
			return err
		}, nil})
	}
	if os.Getenv("CSAP_TEST_REDIS_ADDR") != "" {
		rdb, p := redisEnv(t)
		stores = append(stores, struct {
			name  string
			s     store
			raw   func(key, json string) error
			clean func()
		}{"redis", newRedis(rdb, p, "st", 0, -1), func(key, js string) error {
			return rdb.HSet(ctx, p+"{st}:rules", key, js).Err()
		}, nil})
	}
	for _, st := range stores {
		t.Run(st.name, func(t *testing.T) {
			if _, err := st.s.Save(ctx, "G.old", strategy.MethodRules{Status: "DEPRECATED", StatusNote: "请改用 /v2"}, "u"); err != nil {
				t.Fatal(err)
			}
			if err := st.raw("@templates", `{"version":1,"templates":[]}`); err != nil {
				t.Fatal(err)
			}
			rs, err := st.s.Load(ctx)
			if err != nil || len(rs) != 1 || rs["G.old"].Status != "DEPRECATED" || rs["G.old"].StatusNote != "请改用 /v2" {
				t.Fatalf("%v %+v", err, rs)
			}
			// 历史里能回滚状态的修改
			h, _ := st.s.History(ctx, "G.old", 0)
			if len(h) != 1 || h[0].After == nil || h[0].After.Status != "DEPRECATED" {
				t.Fatalf("%+v", h)
			}
			if _, err := st.s.Rollback(ctx, h[0].ID, "u"); err != nil {
				t.Fatal(err)
			}
			if rs, _ = st.s.Load(ctx); len(rs) != 0 {
				t.Fatalf("回滚到没有规则应删除: %v", rs)
			}
		})
	}
}
