package sql

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// Dialect 是数据库方言,决定占位符和建表语句。
type Dialect int

const (
	SQLite Dialect = iota
	Postgres
	MySQL
)

// Notifier 是可选的跨实例通知(如 Redis Pub/Sub):保存后 Publish,其他实例 Subscribe 收到后立即重新加载,
// 不用等轮询。没有 Notifier 时只靠轮询版本号。用 go-redis 实现的例子见 docs/DESIGN.md。
type Notifier interface {
	Publish(ctx context.Context, version int64) error
	// Subscribe 阻塞到 ctx 结束,每收到一条通知调用一次 fn。
	Subscribe(ctx context.Context, fn func(version int64)) error
}

// Options 是 New 的参数。
type Options struct {
	DB      *sql.DB
	Dialect Dialect
	// TablePrefix 是表名前缀,默认 "csap_apidoc_"。
	TablePrefix string
	// Poll 是轮询版本号的间隔,默认 5 秒;小于 0 表示不轮询(只靠 Notifier)。
	Poll time.Duration
	// Notifier 可选,见 Notifier。
	Notifier Notifier
}

// Strategy 把字段规则存进数据库,适合多实例部署和在线修改。表结构(前缀可改):
//
//	csap_apidoc_rule     规则:rule_key(分组.接口) 主键,rules(JSON),version,updated_by,updated_at
//	csap_apidoc_history  修改记录:每次保存一行,before_rules / after_rules / version / updated_by / updated_at
//	csap_apidoc_meta     全局版本号:每次保存加 1,其他实例轮询它判断要不要重新加载
//
// 与 Java SQLite 策略的表结构不同(Java 存的是整份文档),不能直接共用一个库。
type Strategy struct {
	opts Options
	t    tables

	mu   sync.Mutex
	subs []chan struct{}
	// loaded 是最近一次 Load 时的全局版本号。Watch 拿它做基准,
	// 这样 Load 与 Watch 启动之间发生的修改也不会漏掉。
	loaded atomic.Int64
}

type tables struct{ rule, history, meta string }

// New 创建 SQL 策略。第一次使用前调用 EnsureSchema 建表(或者自己按 Schema 返回的语句建)。
func New(opts Options) *Strategy {
	if opts.TablePrefix == "" {
		opts.TablePrefix = "csap_apidoc_"
	}
	if opts.Poll == 0 {
		opts.Poll = 5 * time.Second
	}
	p := opts.TablePrefix
	return &Strategy{opts: opts, t: tables{rule: p + "rule", history: p + "history", meta: p + "meta"}}
}

func (s *Strategy) Name() string { return "sql:" + s.opts.TablePrefix }

// q 把 ? 占位符换成方言的写法。
func (s *Strategy) q(query string) string {
	if s.opts.Dialect != Postgres {
		return query
	}
	var b strings.Builder
	n := 0
	for _, r := range query {
		if r == '?' {
			n++
			b.WriteByte('$')
			b.WriteString(strconv.Itoa(n))
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

// Schema 返回建表语句。
func (s *Strategy) Schema() []string {
	text, key, idCol := "TEXT", "VARCHAR(512)", ""
	switch s.opts.Dialect {
	case SQLite:
		idCol = "id INTEGER PRIMARY KEY AUTOINCREMENT"
	case Postgres:
		idCol = "id BIGSERIAL PRIMARY KEY"
	case MySQL:
		text = "MEDIUMTEXT"
		idCol = "id BIGINT AUTO_INCREMENT PRIMARY KEY"
	}
	stmts := []string{
		fmt.Sprintf(`CREATE TABLE IF NOT EXISTS %s (
	rule_key %s NOT NULL PRIMARY KEY,
	rules %s NOT NULL,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL DEFAULT '',
	updated_at BIGINT NOT NULL
)`, s.t.rule, key, text),
		fmt.Sprintf(`CREATE TABLE IF NOT EXISTS %s (
	%s,
	rule_key %s NOT NULL,
	before_rules %s,
	after_rules %s,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL DEFAULT '',
	updated_at BIGINT NOT NULL
)`, s.t.history, idCol, key, text, text),
		fmt.Sprintf(`CREATE TABLE IF NOT EXISTS %s (
	id INT NOT NULL PRIMARY KEY,
	version BIGINT NOT NULL
)`, s.t.meta),
	}
	idx := fmt.Sprintf("CREATE INDEX IF NOT EXISTS %shistory_key ON %s (rule_key, id)", s.opts.TablePrefix, s.t.history)
	if s.opts.Dialect == MySQL {
		// MySQL 8 不支持 CREATE INDEX IF NOT EXISTS,建表时直接带上
		stmts[1] = strings.Replace(stmts[1], "\tupdated_at BIGINT NOT NULL\n)", "\tupdated_at BIGINT NOT NULL,\n\tKEY "+s.opts.TablePrefix+"history_key (rule_key, id)\n)", 1)
	} else {
		stmts = append(stmts, idx)
	}
	switch s.opts.Dialect {
	case SQLite:
		stmts = append(stmts, fmt.Sprintf("INSERT OR IGNORE INTO %s (id, version) VALUES (1, 0)", s.t.meta))
	case Postgres:
		stmts = append(stmts, fmt.Sprintf("INSERT INTO %s (id, version) VALUES (1, 0) ON CONFLICT DO NOTHING", s.t.meta))
	case MySQL:
		stmts = append(stmts, fmt.Sprintf("INSERT IGNORE INTO %s (id, version) VALUES (1, 0)", s.t.meta))
	}
	return stmts
}

// EnsureSchema 建表(已存在则跳过)。
func (s *Strategy) EnsureSchema(ctx context.Context) error {
	for _, stmt := range s.Schema() {
		if _, err := s.opts.DB.ExecContext(ctx, stmt); err != nil {
			return fmt.Errorf("apidoc/sql: %w\n%s", err, stmt)
		}
	}
	return nil
}

// Load 加载全部规则。
func (s *Strategy) Load(ctx context.Context) (strategy.Rules, error) {
	// 先读版本号再读规则:两者之间若有修改,记下的版本偏旧,下次检查会再加载一次,不会漏
	v, err := s.Version(ctx)
	if err != nil {
		return nil, err
	}
	rows, err := s.opts.DB.QueryContext(ctx, "SELECT rule_key, rules FROM "+s.t.rule)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := strategy.Rules{}
	var errs []error
	for rows.Next() {
		var key, raw string
		if err := rows.Scan(&key, &raw); err != nil {
			return nil, err
		}
		var mr strategy.MethodRules
		if err := json.Unmarshal([]byte(raw), &mr); err != nil {
			errs = append(errs, fmt.Errorf("apidoc/sql: rule %s: %w", key, err))
			continue
		}
		out[key] = mr
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	s.loaded.Store(v)
	return out, errors.Join(errs...)
}

// Version 返回全局版本号。
func (s *Strategy) Version(ctx context.Context) (int64, error) {
	var v int64
	err := s.opts.DB.QueryRowContext(ctx, s.q("SELECT version FROM "+s.t.meta+" WHERE id = ?"), 1).Scan(&v)
	return v, err
}

// Save 保存一个接口的规则(空规则表示删除),全局版本号加 1,并写一条修改记录。返回新版本号。
func (s *Strategy) Save(ctx context.Context, key string, rules strategy.MethodRules, by string) (int64, error) {
	empty := len(rules.Request) == 0 && len(rules.Response) == 0
	var after []byte
	if !empty {
		var err error
		if after, err = json.Marshal(rules); err != nil {
			return 0, err
		}
	}
	v, err := s.tx(ctx, func(tx *sql.Tx) (int64, error) {
		// 先加版本号:PostgreSQL / MySQL 会锁住 meta 行,并发保存串行化
		if _, err := tx.ExecContext(ctx, s.q("UPDATE "+s.t.meta+" SET version = version + 1 WHERE id = ?"), 1); err != nil {
			return 0, err
		}
		var v int64
		if err := tx.QueryRowContext(ctx, s.q("SELECT version FROM "+s.t.meta+" WHERE id = ?"), 1).Scan(&v); err != nil {
			return 0, err
		}
		var before sql.NullString
		err := tx.QueryRowContext(ctx, s.q("SELECT rules FROM "+s.t.rule+" WHERE rule_key = ?"), key).Scan(&before)
		if err != nil && !errors.Is(err, sql.ErrNoRows) {
			return 0, err
		}
		now := time.Now().UnixMilli()
		switch {
		case empty:
			if _, err := tx.ExecContext(ctx, s.q("DELETE FROM "+s.t.rule+" WHERE rule_key = ?"), key); err != nil {
				return 0, err
			}
		case before.Valid:
			if _, err := tx.ExecContext(ctx, s.q("UPDATE "+s.t.rule+" SET rules = ?, version = ?, updated_by = ?, updated_at = ? WHERE rule_key = ?"),
				string(after), v, by, now, key); err != nil {
				return 0, err
			}
		default:
			if _, err := tx.ExecContext(ctx, s.q("INSERT INTO "+s.t.rule+" (rule_key, rules, version, updated_by, updated_at) VALUES (?, ?, ?, ?, ?)"),
				key, string(after), v, by, now); err != nil {
				return 0, err
			}
		}
		var afterArg any
		if !empty {
			afterArg = string(after)
		}
		var beforeArg any
		if before.Valid {
			beforeArg = before.String
		}
		if _, err := tx.ExecContext(ctx, s.q("INSERT INTO "+s.t.history+" (rule_key, before_rules, after_rules, version, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?)"),
			key, beforeArg, afterArg, v, by, now); err != nil {
			return 0, err
		}
		return v, nil
	})
	if err != nil {
		return 0, fmt.Errorf("apidoc/sql: save %s: %w", key, err)
	}
	s.notifyLocal()
	if s.opts.Notifier != nil {
		_ = s.opts.Notifier.Publish(ctx, v)
	}
	return v, nil
}

func (s *Strategy) tx(ctx context.Context, fn func(*sql.Tx) (int64, error)) (int64, error) {
	tx, err := s.opts.DB.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	v, err := fn(tx)
	if err != nil {
		_ = tx.Rollback()
		return 0, err
	}
	return v, tx.Commit()
}

// Change 是一条修改记录,与其他可写策略共用 strategy.Change。
type Change = strategy.Change

var _ strategy.Historian = (*Strategy)(nil)

// History 返回修改记录,最新的在前。key 为空返回全部;limit<=0 时默认 100。
func (s *Strategy) History(ctx context.Context, key string, limit int) ([]Change, error) {
	if limit <= 0 {
		limit = 100
	}
	query := "SELECT id, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM " + s.t.history
	args := []any{}
	if key != "" {
		query += " WHERE rule_key = ?"
		args = append(args, key)
	}
	query += " ORDER BY id DESC LIMIT " + strconv.Itoa(limit)
	rows, err := s.opts.DB.QueryContext(ctx, s.q(query), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Change{}
	for rows.Next() {
		var c Change
		var before, after sql.NullString
		var at int64
		if err := rows.Scan(&c.ID, &c.Key, &before, &after, &c.Version, &c.UpdatedBy, &at); err != nil {
			return nil, err
		}
		c.UpdatedAt = time.UnixMilli(at)
		if c.Before, err = decode(before); err != nil {
			return nil, err
		}
		if c.After, err = decode(after); err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

// Rollback 把某条修改记录对应的接口恢复成"修改前"的样子。回滚本身也是一次保存,会留下新的记录。
func (s *Strategy) Rollback(ctx context.Context, changeID int64, by string) (int64, error) {
	var key string
	var before sql.NullString
	err := s.opts.DB.QueryRowContext(ctx, s.q("SELECT rule_key, before_rules FROM "+s.t.history+" WHERE id = ?"), changeID).Scan(&key, &before)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, fmt.Errorf("apidoc/sql: %w: %d", strategy.ErrNoChange, changeID)
	}
	if err != nil {
		return 0, fmt.Errorf("apidoc/sql: change %d: %w", changeID, err)
	}
	mr, err := decode(before)
	if err != nil {
		return 0, err
	}
	if mr == nil {
		mr = &strategy.MethodRules{}
	}
	return s.Save(ctx, key, *mr, by)
}

func decode(ns sql.NullString) (*strategy.MethodRules, error) {
	if !ns.Valid {
		return nil, nil
	}
	var mr strategy.MethodRules
	if err := json.Unmarshal([]byte(ns.String), &mr); err != nil {
		return nil, err
	}
	return &mr, nil
}

func (s *Strategy) notifyLocal() {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, c := range s.subs {
		select {
		case c <- struct{}{}:
		default:
		}
	}
}

// Watch 轮询全局版本号(以及 Notifier 通知、本实例的保存),变化时调用 onChange。阻塞到 ctx 结束。
func (s *Strategy) Watch(ctx context.Context, onChange func()) error {
	c := make(chan struct{}, 1)
	s.mu.Lock()
	s.subs = append(s.subs, c)
	s.mu.Unlock()
	defer func() {
		s.mu.Lock()
		for i, x := range s.subs {
			if x == c {
				s.subs = append(s.subs[:i], s.subs[i+1:]...)
				break
			}
		}
		s.mu.Unlock()
	}()
	if s.opts.Notifier != nil {
		go func() {
			_ = s.opts.Notifier.Subscribe(ctx, func(int64) {
				select {
				case c <- struct{}{}:
				default:
				}
			})
		}()
	}
	var tick <-chan time.Time
	if s.opts.Poll > 0 {
		t := time.NewTicker(s.opts.Poll)
		defer t.Stop()
		tick = t.C
	}
	check := func() {
		v, err := s.Version(ctx)
		if err != nil || v == s.loaded.Load() {
			return
		}
		onChange() // 调用方会重新 Load,loaded 随之更新
	}
	check() // 启动时先对一次:Load 之后、Watch 之前的修改
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-c:
			check()
		case <-tick:
			check()
		}
	}
}
