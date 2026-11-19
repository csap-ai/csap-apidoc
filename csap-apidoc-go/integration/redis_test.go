package integration

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	redisstrategy "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/redis"
	yamlstrategy "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
)

// redisEnv 返回连接和一个每个测试独立的键前缀;没有设置 CSAP_TEST_REDIS_ADDR 就跳过。
func redisEnv(t *testing.T) (*redis.Client, string) {
	t.Helper()
	addr := os.Getenv("CSAP_TEST_REDIS_ADDR")
	if addr == "" {
		t.Skip("未设置 CSAP_TEST_REDIS_ADDR,跳过 Redis 集成测试")
	}
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	prefix := "csap:it:" + time.Now().Format("150405.000000") + ":"
	t.Cleanup(func() {
		ctx := context.Background()
		iter := rdb.Scan(ctx, 0, prefix+"*", 200).Iterator()
		for iter.Next(ctx) {
			rdb.Del(ctx, iter.Val())
		}
		rdb.Close()
	})
	if err := rdb.Ping(context.Background()).Err(); err != nil {
		t.Fatal(err)
	}
	return rdb, prefix
}

func newRedis(rdb *redis.Client, prefix, service string, limit int, poll time.Duration) *redisstrategy.Strategy {
	return redisstrategy.New(redisstrategy.Options{Client: GoRedis{rdb}, KeyPrefix: prefix, Service: service, HistoryLimit: limit, Poll: poll})
}

func TestRedisSaveDeleteUnchanged(t *testing.T) {
	rdb, prefix := redisEnv(t)
	ctx := context.Background()
	s := newRedis(rdb, prefix, "order-service", 0, -1)
	if v, err := s.Version(ctx); err != nil || v != 0 {
		t.Fatalf("initial %d %v", v, err)
	}
	if v, err := s.Save(ctx, "G.m", strategy.MethodRules{}, "x"); err != nil || v != 0 {
		t.Fatalf("删除不存在的规则不该加版本号: %d %v", v, err)
	}
	if v, err := s.Save(ctx, "G.m", rr("a"), ""); err != nil || v != 1 {
		t.Fatalf("save %d %v", v, err)
	}
	if v, _ := s.Save(ctx, "G.m", rr("a"), "x"); v != 1 {
		t.Fatalf("内容不变不该加版本号: %d", v)
	}
	// 键的形状:前缀 + {服务名}
	base := prefix + "{order-service}:"
	if n, _ := rdb.HLen(ctx, base+"rules").Result(); n != 1 {
		t.Fatalf("rules hash: %d", n)
	}
	if v, _ := rdb.Get(ctx, base+"version").Result(); v != "1" {
		t.Fatalf("version key %q", v)
	}
	// 修改记录 JSON:键名固定,before 为 null,after 是字符串
	raw, err := rdb.LIndex(ctx, base+"history:G.m", 0).Result()
	if err != nil {
		t.Fatal(err)
	}
	var rec map[string]any
	_ = json.Unmarshal([]byte(raw), &rec)
	for _, k := range []string{"id", "ruleKey", "before", "after", "version", "updatedBy", "updatedAt"} {
		if _, ok := rec[k]; !ok {
			t.Fatalf("记录缺少键 %s: %s", k, raw)
		}
	}
	if rec["before"] != nil || rec["updatedBy"] != "unknown" || rec["id"] != float64(1) {
		t.Fatalf("record: %s", raw)
	}
	if _, ok := rec["after"].(string); !ok {
		t.Fatalf("after 应为字符串: %s", raw)
	}
	if id, _ := rdb.HGet(ctx, base+"changes", "1").Result(); id != "G.m" {
		t.Fatalf("changes 索引: %q", id)
	}
	// 别的语言写的 JSON(文本不同、结构相同):不加版本号
	if err := rdb.HSet(ctx, base+"rules", "G.j", ` { "response" : {"x":{"include":true}} } `).Err(); err != nil {
		t.Fatal(err)
	}
	if v, _ := s.Save(ctx, "G.j", rr("x"), "x"); v != 1 {
		t.Fatalf("结构相同不该加版本号: %d", v)
	}
	// 脚本自己也判断:相同文本直接返回 -1
	same, _ := rdb.HGet(ctx, base+"rules", "G.m").Result()
	if n, err := (GoRedis{rdb}).Eval(ctx, redisstrategy.SaveScript, []string{base + "rules", base + "version", base + "history:G.m", base + "changes"},
		"G.m", same, "x", 1, 200, base+"changed"); err != nil || n != -1 {
		t.Fatalf("script same: %d %v", n, err)
	}
	if v, err := s.Save(ctx, "G.m", strategy.MethodRules{}, "x"); err != nil || v != 2 {
		t.Fatalf("delete %d %v", v, err)
	}
	rules, err := s.Load(ctx)
	if err != nil || len(rules) != 1 { // 只剩手工写的 G.j
		t.Fatalf("load %v %v", rules, err)
	}
	// 两个服务互不干扰
	other := newRedis(rdb, prefix, "user-service", 0, -1)
	if rs, _ := other.Load(ctx); len(rs) != 0 {
		t.Fatalf("other service sees %v", rs)
	}
	if v, _ := other.Save(ctx, "G.m", rr("o"), "y"); v != 1 {
		t.Fatalf("other version %d", v)
	}
	if v, _ := s.Version(ctx); v != 2 {
		t.Fatalf("version changed %d", v)
	}
	hs, _ := s.History(ctx, "", 0)
	ho, _ := other.History(ctx, "", 0)
	if len(hs) != 2 || len(ho) != 1 {
		t.Fatalf("history %d %d", len(hs), len(ho))
	}
	if _, err := other.Rollback(ctx, hs[0].ID+5, "z"); !errors.Is(err, strategy.ErrNoChange) {
		t.Fatalf("rollback missing: %v", err)
	}
}

func TestRedisHistoryTrimAndRollback(t *testing.T) {
	rdb, prefix := redisEnv(t)
	ctx := context.Background()
	s := newRedis(rdb, prefix, "default", 3, -1)
	names := []string{"a", "b", "c", "d", "e"}
	for _, n := range names {
		if _, err := s.Save(ctx, "G.m", rr(n), "u"); err != nil {
			t.Fatal(err)
		}
	}
	base := prefix + "{default}:"
	if n, _ := rdb.LLen(ctx, base+"history:G.m").Result(); n != 3 {
		t.Fatalf("history 长度 %d", n)
	}
	if n, _ := rdb.HLen(ctx, base+"changes").Result(); n != 3 {
		t.Fatalf("changes 索引应同步裁剪,实际 %d", n)
	}
	hist, err := s.History(ctx, "G.m", 0)
	if err != nil || len(hist) != 3 || hist[0].ID != 5 || hist[2].ID != 3 {
		t.Fatalf("history %v %+v", err, hist)
	}
	if _, err := s.Rollback(ctx, 2, "admin"); !errors.Is(err, strategy.ErrNoChange) {
		t.Fatalf("被裁掉的记录: %v", err)
	}
	// 回滚 id=4(修改前是 c):规则恢复成 c,版本号 6,留新记录
	if v, err := s.Rollback(ctx, 4, "admin"); err != nil || v != 6 {
		t.Fatalf("rollback %d %v", v, err)
	}
	rules, _ := s.Load(ctx)
	if _, ok := rules["G.m"].Response["c"]; !ok {
		t.Fatalf("rollback: %+v", rules["G.m"])
	}
	all, _ := s.History(ctx, "", 2)
	if len(all) != 2 || all[0].ID != 6 || all[0].UpdatedBy != "admin" || all[1].ID != 5 {
		t.Fatalf("all history %+v", all)
	}
	// 回滚"新增"那次:修改前没有规则 → 删除
	s2 := newRedis(rdb, prefix, "svc2", 0, -1)
	_, _ = s2.Save(ctx, "G.n", rr("x"), "u")
	if _, err := s2.Rollback(ctx, 1, "admin"); err != nil {
		t.Fatal(err)
	}
	if rs, _ := s2.Load(ctx); len(rs) != 0 {
		t.Fatalf("rollback to empty should delete: %v", rs)
	}
}

func TestRedisConcurrentSave(t *testing.T) {
	rdb, prefix := redisEnv(t)
	ctx := context.Background()
	s := newRedis(rdb, prefix, "default", 0, -1)
	const n = 30
	var wg sync.WaitGroup
	versions := make(chan int64, n)
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			// 一半写同一个键、一半写不同的键;内容都不同,所以每次都有效
			key := "G.shared"
			if i%2 == 1 {
				key = "G.k" + string(rune('a'+i))
			}
			v, err := s.Save(ctx, key, rr("f"+string(rune('a'+i))), "u")
			if err != nil {
				t.Error(err)
			}
			versions <- v
		}(i)
	}
	wg.Wait()
	close(versions)
	seen := map[int64]bool{}
	for v := range versions {
		if seen[v] || v < 1 || v > n {
			t.Fatalf("版本号重复或越界: %d", v)
		}
		seen[v] = true
	}
	if v, _ := s.Version(ctx); v != n {
		t.Fatalf("final version %d", v)
	}
	if h, _ := s.History(ctx, "G.shared", 0); len(h) != n/2 {
		t.Fatalf("shared history %d", len(h))
	}
}

// noSub 的 Subscribe 永远收不到消息,用来验证只靠轮询也能同步。
type noSub struct{ GoRedis }

func (noSub) Subscribe(ctx context.Context, _ string, _ func(string)) error {
	<-ctx.Done()
	return ctx.Err()
}

func TestRedisMultiInstance(t *testing.T) {
	rdb, prefix := redisEnv(t)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	type item struct {
		ID int64 `json:"id"`
	}
	start := func(c redisstrategy.Client, poll time.Duration) (*apidoc.Doc, *redisstrategy.Strategy) {
		s := redisstrategy.New(redisstrategy.Options{Client: c, KeyPrefix: prefix, Service: "svc", Poll: poll})
		d := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{s}})
		d.Register("GET", "/items", apidoc.Op("G", "list").Resp([]item{}), "")
		go func() { _ = d.Watch(ctx) }()
		return d, s
	}
	masked := func(d *apidoc.Doc) bool { return d.Snapshot().Endpoint("GET", "/items").ResponseMask != nil }

	// 只靠订阅(关掉轮询)
	subA, sa := start(GoRedis{rdb}, -1)
	subB, _ := start(GoRedis{rdb}, -1)
	// 只靠轮询(订阅收不到任何消息)
	pollC, _ := start(noSub{GoRedis{rdb}}, 50*time.Millisecond)
	time.Sleep(300 * time.Millisecond) // 等订阅建立
	if _, err := sa.Save(ctx, "G.list", rr("id"), "a"); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "subscriber A", func() bool { return masked(subA) })
	waitFor(t, "subscriber B", func() bool { return masked(subB) })
	waitFor(t, "poll-only C", func() bool { return masked(pollC) })
	if _, err := sa.Save(ctx, "G.list", strategy.MethodRules{}, "a"); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "delete propagates (subscribe)", func() bool { return !masked(subB) })
	waitFor(t, "delete propagates (poll)", func() bool { return !masked(pollC) })
}

// 导入:YAML → SQL / Redis;默认不覆盖、dry-run 不写入、修改人 migrate。
const importYAML = `com.example.UserController:
  login:
    request:
      request.username:
        required: true
        description: 用户名
    response:
      data.id:
        include: true
  logout:
    response:
      data.ok:
        include: true
`

func TestImportYAMLToSQLAndRedis(t *testing.T) {
	ctx := context.Background()
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "UserController-method-field.yaml"), []byte(importYAML), 0o644); err != nil {
		t.Fatal(err)
	}
	src := yamlstrategy.New(yamlstrategy.Options{Dir: dir, Poll: -1, History: "-"})
	type dst struct {
		name string
		s    interface {
			strategy.Store
			strategy.Historian
		}
	}
	var dsts []dst
	for _, c := range cases(t) {
		db := c.open(t)
		s := newSvc(db, c, prefix(t, db), "imp")
		if err := s.EnsureSchema(ctx); err != nil {
			t.Fatal(err)
		}
		dsts = append(dsts, dst{c.name, s})
	}
	if os.Getenv("CSAP_TEST_REDIS_ADDR") != "" {
		rdb, p := redisEnv(t)
		dsts = append(dsts, dst{"redis", newRedis(rdb, p, "imp", 0, -1)})
	}
	for _, d := range dsts {
		t.Run(d.name, func(t *testing.T) {
			res, err := strategy.Import(ctx, src, d.s, strategy.ImportOptions{DryRun: true})
			if err != nil || len(res.Created) != 2 {
				t.Fatalf("dry-run %+v %v", res, err)
			}
			if rs, _ := d.s.Load(ctx); len(rs) != 0 {
				t.Fatal("dry-run 写入了数据")
			}
			if res, err = strategy.Import(ctx, src, d.s, strategy.ImportOptions{}); err != nil || len(res.Created) != 2 {
				t.Fatalf("import %+v %v", res, err)
			}
			h, _ := d.s.History(ctx, "", 0)
			if len(h) != 2 || h[0].UpdatedBy != "migrate" {
				t.Fatalf("history %+v", h)
			}
			// 再导入一次:全部相同,不产生新记录
			if res, _ = strategy.Import(ctx, src, d.s, strategy.ImportOptions{}); len(res.Unchanged) != 2 {
				t.Fatalf("again %+v", res)
			}
			// 目标改过的规则:默认冲突、不覆盖;Overwrite 覆盖
			key := "com.example.UserController.logout"
			if _, err := d.s.Save(ctx, key, rr("changed"), "dev"); err != nil {
				t.Fatal(err)
			}
			if res, _ = strategy.Import(ctx, src, d.s, strategy.ImportOptions{}); len(res.Conflicts) != 1 || res.Conflicts[0] != key {
				t.Fatalf("conflict %+v", res)
			}
			if rs, _ := d.s.Load(ctx); rs[key].Response["changed"].Include == nil {
				t.Fatal("冲突的规则被覆盖了")
			}
			if res, _ = strategy.Import(ctx, src, d.s, strategy.ImportOptions{Overwrite: true}); len(res.Updated) != 1 {
				t.Fatalf("overwrite %+v", res)
			}
			if rs, _ := d.s.Load(ctx); rs[key].Response["data.ok"].Include == nil {
				t.Fatal("overwrite 没生效")
			}
		})
	}
}

// HistoryLimit<0:不裁剪,记录和 changes 索引都保留全部(与 Java 的 historyLimit<=0 一致,不会出现索引被删、记录还在)。
func TestRedisHistoryUnlimited(t *testing.T) {
	rdb, prefix := redisEnv(t)
	ctx := context.Background()
	s := newRedis(rdb, prefix, "default", -1, -1)
	for _, n := range []string{"a", "b", "c", "d", "e"} {
		if _, err := s.Save(ctx, "G.m", rr(n), "u"); err != nil {
			t.Fatal(err)
		}
	}
	base := prefix + "{default}:"
	if n, _ := rdb.LLen(ctx, base+"history:G.m").Result(); n != 5 {
		t.Fatalf("history 长度 %d", n)
	}
	if n, _ := rdb.HLen(ctx, base+"changes").Result(); n != 5 {
		t.Fatalf("changes 索引 %d", n)
	}
	if h, err := s.History(ctx, "G.m", 0); err != nil || len(h) != 5 {
		t.Fatalf("history %v %d", err, len(h))
	}
	if _, err := s.Rollback(ctx, 1, "admin"); err != nil {
		t.Fatalf("最早的记录仍可回滚: %v", err)
	}
}

// SaveRaw 与 Java RedisRuleStore.save 一致:内容语义相同(空白、键顺序不同)时不写,返回当前版本号。
func TestRedisSaveRawSemanticUnchanged(t *testing.T) {
	rdb, prefix := redisEnv(t)
	ctx := context.Background()
	s := newRedis(rdb, prefix, "order-service", 0, -1)
	if v, err := s.SaveRaw(ctx, "G.raw", `{"response":{"a":{"include":true},"b":{"include":false}}}`, "x"); err != nil || v != 1 {
		t.Fatalf("首次 %d %v", v, err)
	}
	if v, err := s.SaveRaw(ctx, "G.raw", ` { "response" : { "b" : {"include":false}, "a" : {"include": true} } } `, "x"); err != nil || v != 1 {
		t.Fatalf("键序/空白不同应视为相同: %d %v", v, err)
	}
	if v, err := s.SaveRaw(ctx, "G.raw", `{"response":{"a":{"include":false}}}`, "x"); err != nil || v != 2 {
		t.Fatalf("真改动: %d %v", v, err)
	}
	if v, err := s.SaveRaw(ctx, "G.none", "", "x"); err != nil || v != 2 {
		t.Fatalf("删除不存在: %d %v", v, err)
	}
	recs, err := s.History(ctx, "", 100)
	if err != nil || len(recs) != 2 {
		t.Fatalf("记录应 2 条: %d %v", len(recs), err)
	}
}
