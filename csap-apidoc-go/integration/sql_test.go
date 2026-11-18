package integration

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	_ "github.com/go-sql-driver/mysql"
	_ "github.com/jackc/pgx/v5/stdlib"
	_ "modernc.org/sqlite"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	sqlstrategy "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/sql"
)

type dbCase struct {
	name    string
	dialect sqlstrategy.Dialect
	open    func(t *testing.T) *sql.DB
}

func cases(t *testing.T) []dbCase {
	cs := []dbCase{{"sqlite", sqlstrategy.SQLite, func(t *testing.T) *sql.DB {
		db, err := sql.Open("sqlite", filepath.Join(t.TempDir(), "rules.db")+"?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_txlock=immediate")
		if err != nil {
			t.Fatal(err)
		}
		return db
	}}}
	if dsn := os.Getenv("CSAP_TEST_PG_DSN"); dsn != "" {
		cs = append(cs, dbCase{"postgres", sqlstrategy.Postgres, func(t *testing.T) *sql.DB {
			db, err := sql.Open("pgx", dsn)
			if err != nil {
				t.Fatal(err)
			}
			return db
		}})
	}
	if dsn := os.Getenv("CSAP_TEST_MYSQL_DSN"); dsn != "" {
		cs = append(cs, dbCase{"mysql", sqlstrategy.MySQL, func(t *testing.T) *sql.DB {
			db, err := sql.Open("mysql", dsn)
			if err != nil {
				t.Fatal(err)
			}
			return db
		}})
	}
	return cs
}

// prefix 每个测试用独立的表,避免互相影响;测试结束删表。
func prefix(t *testing.T, db *sql.DB) string {
	p := fmt.Sprintf("csap_it_%d_", time.Now().UnixNano()%1_000_000_000)
	t.Cleanup(func() {
		for _, tbl := range []string{"rule", "history", "meta", "rule_v2", "meta_v2"} {
			_, _ = db.Exec("DROP TABLE IF EXISTS " + p + tbl)
		}
		db.Close()
	})
	return p
}

func TestSQLStrategy(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx := context.Background()
			db := c.open(t)
			s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: prefix(t, db)})
			if err := s.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			if err := s.EnsureSchema(ctx); err != nil { // 幂等
				t.Fatal(err)
			}
			if v, err := s.Version(ctx); err != nil || v != 0 {
				t.Fatalf("initial version %d %v", v, err)
			}
			tr := true
			r1 := strategy.MethodRules{Response: map[string]strategy.FieldRule{"data.id": {Include: &tr}}}
			r2 := strategy.MethodRules{Request: map[string]strategy.FieldRule{"id.id": {Required: &tr,
				Validate: []strategy.ValidateRule{{Type: "NotEmpty", Code: "1001", Message: "不能为空", Level: 1}}}}}
			if v, err := s.Save(ctx, "G.m", r1, "alice"); err != nil || v != 1 {
				t.Fatalf("save1 %d %v", v, err)
			}
			if v, err := s.Save(ctx, "G.m", r2, "bob"); err != nil || v != 2 {
				t.Fatalf("save2 %d %v", v, err)
			}
			if _, err := s.Save(ctx, "G.other", r1, "bob"); err != nil {
				t.Fatal(err)
			}
			rules, err := s.Load(ctx)
			if err != nil || len(rules) != 2 {
				t.Fatalf("load %v %d", err, len(rules))
			}
			if got := rules["G.m"].Request["id.id"]; got.Validate[0].Code != "1001" || !*got.Required {
				t.Fatalf("rules: %+v", got)
			}
			hist, err := s.History(ctx, "G.m", 0)
			if err != nil || len(hist) != 2 {
				t.Fatalf("history %v %d", err, len(hist))
			}
			if hist[0].UpdatedBy != "bob" || hist[0].Before == nil || hist[0].After == nil || hist[1].Before != nil {
				t.Fatalf("history content: %+v", hist)
			}
			// 回滚最新一次修改:恢复成 r1
			if _, err := s.Rollback(ctx, hist[0].ID, "admin"); err != nil {
				t.Fatal(err)
			}
			rules, _ = s.Load(ctx)
			if _, ok := rules["G.m"].Response["data.id"]; !ok || len(rules["G.m"].Request) != 0 {
				t.Fatalf("rollback: %+v", rules["G.m"])
			}
			// 回滚第一次修改(修改前没有规则):删除
			if _, err := s.Rollback(ctx, hist[1].ID, "admin"); err != nil {
				t.Fatal(err)
			}
			rules, _ = s.Load(ctx)
			if _, ok := rules["G.m"]; ok {
				t.Fatal("rollback to empty should delete")
			}
			all, _ := s.History(ctx, "", 2)
			if len(all) != 2 || all[0].After != nil || all[0].UpdatedBy != "admin" {
				t.Fatalf("all history: %+v", all)
			}
			if v, _ := s.Version(ctx); v != 5 {
				t.Fatalf("version %d", v)
			}
		})
	}
}

// TestMultiInstance 两个实例共用一个库:A 保存后,B 在轮询间隔内生效,不用重启。
func TestMultiInstance(t *testing.T) {
	type item struct {
		ID   int64  `json:"id"`
		Name string `json:"name"`
	}
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			db := c.open(t)
			p := prefix(t, db)
			newInstance := func(n sqlstrategy.Notifier, poll time.Duration) (*apidoc.Doc, *sqlstrategy.Strategy) {
				s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: p, Poll: poll, Notifier: n})
				d := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{s}})
				d.Register("GET", "/items", apidoc.Op("G", "list").Resp([]item{}), "")
				go func() { _ = d.Watch(ctx) }()
				return d, s
			}
			// 先建表(实际部署也是启动时建好),再启动各实例的监听
			if err := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: p}).EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			a, sa := newInstance(nil, 50*time.Millisecond)
			b, _ := newInstance(nil, 50*time.Millisecond)
			if b.Snapshot().Endpoint("GET", "/items").ResponseMask != nil {
				t.Fatal("no rules yet")
			}
			time.Sleep(20 * time.Millisecond)
			if _, err := sa.Save(ctx, "G.list", strategy.MethodRules{Response: map[string]strategy.FieldRule{
				"id": {Include: model.Bool(true)}}}, "a"); err != nil {
				t.Fatal(err)
			}
			waitFor(t, "instance B picks up rule", func() bool { return b.Snapshot().Endpoint("GET", "/items").ResponseMask != nil })
			waitFor(t, "instance A picks up rule", func() bool { return a.Snapshot().Endpoint("GET", "/items").ResponseMask != nil })

			// 关掉轮询、只靠通知
			bus := &chanBus{}
			_, sc := newInstance(bus, -1)
			d, _ := newInstance(bus, -1)
			time.Sleep(20 * time.Millisecond)
			if _, err := sc.Save(ctx, "G.list", strategy.MethodRules{}, "c"); err != nil {
				t.Fatal(err)
			}
			waitFor(t, "notifier propagates delete", func() bool { return d.Snapshot().Endpoint("GET", "/items").ResponseMask == nil })
		})
	}
}

func waitFor(t *testing.T, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("timeout: %s", what)
}

// chanBus 是进程内的 Notifier,模拟 Redis Pub/Sub。
type chanBus struct {
	mu   sync.Mutex
	subs []func(int64)
}

func (b *chanBus) Publish(_ context.Context, v int64) error {
	b.mu.Lock()
	subs := append([]func(int64){}, b.subs...)
	b.mu.Unlock()
	for _, fn := range subs {
		fn(v)
	}
	return nil
}

func (b *chanBus) Subscribe(ctx context.Context, fn func(int64)) error {
	b.mu.Lock()
	b.subs = append(b.subs, fn)
	b.mu.Unlock()
	<-ctx.Done()
	return ctx.Err()
}

// TestWatchSeesChangeBeforeStart:Load 之后、Watch 启动之前别的实例的修改也要能发现(曾经的 bug)。
func TestWatchSeesChangeBeforeStart(t *testing.T) {
	for _, c := range cases(t) {
		t.Run(c.name, func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			db := c.open(t)
			p := prefix(t, db)
			s1 := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: p, Poll: time.Hour})
			s2 := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: c.dialect, TablePrefix: p})
			if err := s1.EnsureSchema(ctx); err != nil {
				t.Fatal(err)
			}
			if _, err := s1.Load(ctx); err != nil {
				t.Fatal(err)
			}
			if _, err := s2.Save(ctx, "G.m", strategy.MethodRules{Response: map[string]strategy.FieldRule{"x": {Include: model.Bool(true)}}}, "s2"); err != nil {
				t.Fatal(err)
			}
			changed := make(chan struct{}, 1)
			go func() { _ = s1.Watch(ctx, func() { changed <- struct{}{} }) }()
			select {
			case <-changed:
			case <-time.After(2 * time.Second):
				t.Fatal("change between Load and Watch was missed")
			}
		})
	}
}
