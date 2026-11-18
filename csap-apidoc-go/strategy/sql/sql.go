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
	// DM 是达梦 DM8。占位符 ?,大文本 CLOB,自增 IDENTITY。驱动由使用方 import(如 gitee.com/chunanyong/dm)。
	DM
	// Kingbase 是人大金仓,按 PostgreSQL 兼容模式处理(占位符 $n,BIGSERIAL)。
	// MySQL 兼容模式的库可用 MySQL 方言试用;Oracle 兼容模式未验证,暂不支持。
	Kingbase
)

// DefaultService 是 Options.Service 为空时使用的服务名,与 Java 版一致。
const DefaultService = "default"

// ErrLegacySchema 表示库里是 v0.1.x 的旧表(没有 service 列)。调用 Migrate 升级,或按 MigrationSQL 手工执行。
var ErrLegacySchema = errors.New("旧版表结构(没有 service 列),请先执行迁移:Strategy.Migrate 或 MigrationSQL")

// Notifier 是可选的跨实例通知(如 Redis Pub/Sub):保存后 Publish,其他实例 Subscribe 收到后立即重新加载,
// 不用等轮询。没有 Notifier 时只靠轮询版本号。用 go-redis 实现的例子见 docs/DESIGN.md。
//
// 这个接口不带服务名,所以保持 v0.1.x 的写法不变;一个库里存多个服务时,收到通知的实例会多做一次版本号检查
// (只查一行,版本号没变就不重新加载),结果依然正确。要避免无谓检查,实现 ServiceNotifier。
type Notifier interface {
	Publish(ctx context.Context, version int64) error
	// Subscribe 阻塞到 ctx 结束,每收到一条通知调用一次 fn。
	Subscribe(ctx context.Context, fn func(version int64)) error
}

// ServiceNotifier 是带服务名的通知。Notifier 同时实现了它时,优先使用它:Publish 带上本实例的服务名,
// 订阅端忽略其他服务的消息。
type ServiceNotifier interface {
	Notifier
	PublishService(ctx context.Context, service string, version int64) error
	SubscribeService(ctx context.Context, fn func(service string, version int64)) error
}

// Options 是 New 的参数。
type Options struct {
	DB      *sql.DB
	Dialect Dialect
	// Service 是服务名,同一个库存多个服务的规则时用来区分,所有读写都按它过滤。
	// 默认 "default",最长 128 个字符。要与 Java 版共用一个库,两边填同一个名字。
	Service string
	// TablePrefix 是表名前缀,默认 "csap_apidoc_"。
	TablePrefix string
	// Poll 是轮询版本号的间隔,默认 5 秒;小于 0 表示不轮询(只靠 Notifier)。
	Poll time.Duration
	// Notifier 可选,见 Notifier。
	Notifier Notifier
}

// Strategy 把字段规则存进数据库,适合多实例部署和在线修改。表结构与 Java 1.4.0 的数据库模式相同(前缀可改):
//
//	csap_apidoc_rule     规则:(service, rule_key) 主键,rules(JSON),version,updated_by,updated_at(毫秒)
//	csap_apidoc_history  修改记录:每次保存一行,service / rule_key / before_rules / after_rules / version / updated_by / updated_at
//	csap_apidoc_meta     每个服务一行的版本号:每次保存加 1,其他实例轮询它判断要不要重新加载
//
// rule_key 在 Go 里是 "分组.接口名",在 Java 里是 "类全名.方法名";规则 JSON 两边一样。
// v0.1.x 的旧表没有 service 列,见 Migrate。
type Strategy struct {
	opts Options
	t    tables

	mu   sync.Mutex
	subs []chan struct{}
	// loaded 是最近一次 Load 时的全局版本号。Watch 拿它做基准,
	// 这样 Load 与 Watch 启动之间发生的修改也不会漏掉。
	loaded atomic.Int64
	metaOK atomic.Bool // meta 行已确认存在
}

type tables struct{ rule, history, meta string }

// New 创建 SQL 策略。第一次使用前调用 EnsureSchema 建表(或者自己按 Schema 返回的语句建)。
func New(opts Options) *Strategy {
	if opts.TablePrefix == "" {
		opts.TablePrefix = "csap_apidoc_"
	}
	if opts.Service == "" {
		opts.Service = DefaultService
	}
	if opts.Poll == 0 {
		opts.Poll = 5 * time.Second
	}
	p := opts.TablePrefix
	return &Strategy{opts: opts, t: tables{rule: p + "rule", history: p + "history", meta: p + "meta"}}
}

func (s *Strategy) Name() string { return "sql:" + s.opts.TablePrefix + "[" + s.opts.Service + "]" }

func (s *Strategy) checkService() error {
	if n := len([]rune(s.opts.Service)); n > 128 {
		return fmt.Errorf("apidoc/sql: Service 最长 128 个字符,当前 %d", n)
	}
	return nil
}

// q 把 ? 占位符换成方言的写法。
func (s *Strategy) q(query string) string {
	if d := s.opts.Dialect; d != Postgres && d != Kingbase {
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

// tableDDL 是一张表的建表语句(含它的索引)。
type tableDDL struct {
	table string
	stmts []string
}

func (s *Strategy) ddl() []tableDDL {
	text, idCol, opts := "TEXT", "id INTEGER PRIMARY KEY AUTOINCREMENT", ""
	switch s.opts.Dialect {
	case Postgres, Kingbase:
		idCol = "id BIGSERIAL PRIMARY KEY"
	case MySQL:
		text, idCol, opts = "LONGTEXT", "id BIGINT AUTO_INCREMENT PRIMARY KEY", " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
	case DM:
		text, idCol = "CLOB", "id BIGINT IDENTITY(1, 1) PRIMARY KEY"
	}
	return []tableDDL{
		{s.t.rule, []string{fmt.Sprintf(`CREATE TABLE %s (
	service VARCHAR(128) NOT NULL,
	rule_key VARCHAR(512) NOT NULL,
	rules %s NOT NULL,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL,
	updated_at BIGINT NOT NULL,
	PRIMARY KEY (service, rule_key)
)%s`, s.t.rule, text, opts)}},
		{s.t.history, []string{fmt.Sprintf(`CREATE TABLE %s (
	%s,
	service VARCHAR(128) NOT NULL,
	rule_key VARCHAR(512) NOT NULL,
	before_rules %s,
	after_rules %s,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL,
	updated_at BIGINT NOT NULL
)%s`, s.t.history, idCol, text, text, opts),
			fmt.Sprintf("CREATE INDEX %shistory_key ON %s (service, rule_key, id)", s.opts.TablePrefix, s.t.history)}},
		{s.t.meta, []string{fmt.Sprintf(`CREATE TABLE %s (
	service VARCHAR(128) NOT NULL PRIMARY KEY,
	version BIGINT NOT NULL
)%s`, s.t.meta, opts)}},
	}
}

// Schema 返回建表语句(全部三张表,不带 IF NOT EXISTS;表已存在时请用 EnsureSchema)。
func (s *Strategy) Schema() []string {
	var out []string
	for _, d := range s.ddl() {
		out = append(out, d.stmts...)
	}
	return out
}

type tableState int

const (
	tableMissing tableState = iota
	tableLegacy
	tableCurrent
)

// state 探测一张表:能查 service 列是新结构,只能查旧列是 v0.1.x 的旧结构,都不行当作不存在。
func (s *Strategy) state(ctx context.Context, table, legacyCol string) (tableState, error) {
	if err := ctx.Err(); err != nil {
		return 0, err
	}
	if s.probe(ctx, table, "service") {
		return tableCurrent, nil
	}
	if s.probe(ctx, table, legacyCol) {
		return tableLegacy, nil
	}
	return tableMissing, ctx.Err()
}

func (s *Strategy) probe(ctx context.Context, table, col string) bool {
	rows, err := s.opts.DB.QueryContext(ctx, "SELECT "+col+" FROM "+table+" WHERE 1 = 0")
	if err != nil {
		return false
	}
	_ = rows.Close()
	return true
}

func (s *Strategy) legacyCols() map[string]string {
	return map[string]string{s.t.rule: "rule_key", s.t.history: "rule_key", s.t.meta: "id"}
}

// EnsureSchema 建表(表已存在则跳过,并补上本服务的 meta 行)。发现 v0.1.x 的旧表时返回 ErrLegacySchema,不会改动它。
func (s *Strategy) EnsureSchema(ctx context.Context) error {
	if err := s.checkService(); err != nil {
		return err
	}
	legacy := s.legacyCols()
	for _, d := range s.ddl() {
		st, err := s.state(ctx, d.table, legacy[d.table])
		if err != nil {
			return err
		}
		switch st {
		case tableCurrent:
			continue
		case tableLegacy:
			return fmt.Errorf("apidoc/sql: 表 %s: %w", d.table, ErrLegacySchema)
		}
		for _, stmt := range d.stmts {
			if _, err := s.opts.DB.ExecContext(ctx, stmt); err != nil {
				// 多个实例同时启动:别的实例刚建好,就不算错
				if st2, _ := s.state(ctx, d.table, legacy[d.table]); st2 == tableCurrent {
					break
				}
				return fmt.Errorf("apidoc/sql: %w\n%s", err, stmt)
			}
		}
	}
	return s.ensureMeta(ctx)
}

// ensureMeta 保证本服务在 meta 表里有一行(Java 或别的实例可能还没写过)。
func (s *Strategy) ensureMeta(ctx context.Context) error {
	if s.metaOK.Load() {
		return nil
	}
	read := func() error {
		var v int64
		return s.opts.DB.QueryRowContext(ctx, s.q("SELECT version FROM "+s.t.meta+" WHERE service = ?"), s.opts.Service).Scan(&v)
	}
	err := read()
	if errors.Is(err, sql.ErrNoRows) {
		if _, ierr := s.opts.DB.ExecContext(ctx, s.q("INSERT INTO "+s.t.meta+" (service, version) VALUES (?, 0)"), s.opts.Service); ierr != nil {
			if err = read(); err != nil { // 并发插入:别人插成功了就行
				return ierr
			}
		}
		err = nil
	}
	if err != nil {
		return err
	}
	s.metaOK.Store(true)
	return nil
}

func quote(v string) string { return "'" + strings.ReplaceAll(v, "'", "''") + "'" }

// migrationGroups 返回 v0.1.x → v0.2.0 的迁移语句,按表分组。旧数据全部归到 Options.Service。
// 做法:rule / meta 整表重建(换主键),history 加列并重建索引。重建统一用"建新表、拷贝、删旧表、改名",各方言通用。
func (s *Strategy) migrationGroups() []tableDDL {
	svc := quote(s.opts.Service)
	byTable := map[string]tableDDL{}
	for _, d := range s.ddl() {
		byTable[d.table] = d
	}
	ruleNew, metaNew := s.t.rule+"_v2", s.t.meta+"_v2"
	rebuild := func(table string, create []string, copyStmt string) []string {
		tmp := table + "_v2"
		stmts := make([]string, 0, 4)
		for _, c := range create {
			stmts = append(stmts, strings.Replace(c, "CREATE TABLE "+table+" ", "CREATE TABLE "+tmp+" ", 1))
		}
		return append(stmts, copyStmt, "DROP TABLE "+table, "ALTER TABLE "+tmp+" RENAME TO "+table)
	}
	ruleCopy := fmt.Sprintf("INSERT INTO %s (service, rule_key, rules, version, updated_by, updated_at) "+
		"SELECT %s, rule_key, rules, version, CASE WHEN updated_by IS NULL OR updated_by = '' THEN 'unknown' ELSE updated_by END, updated_at FROM %s",
		ruleNew, svc, s.t.rule)
	metaCopy := fmt.Sprintf("INSERT INTO %s (service, version) SELECT %s, version FROM %s WHERE id = 1", metaNew, svc, s.t.meta)
	dropIdx := "DROP INDEX " + s.opts.TablePrefix + "history_key"
	if s.opts.Dialect == MySQL {
		dropIdx += " ON " + s.t.history
	}
	return []tableDDL{
		{s.t.rule, rebuild(s.t.rule, byTable[s.t.rule].stmts, ruleCopy)},
		{s.t.history, []string{
			dropIdx,
			fmt.Sprintf("ALTER TABLE %s ADD COLUMN service VARCHAR(128) NOT NULL DEFAULT %s", s.t.history, svc),
			byTable[s.t.history].stmts[1],
		}},
		{s.t.meta, rebuild(s.t.meta, byTable[s.t.meta].stmts, metaCopy)},
	}
}

// MigrationSQL 返回 v0.1.x → v0.2.0 的迁移语句,旧数据归到 Options.Service,可以交给 DBA 手工执行。
// 语句假设三张表都是旧结构;已经升级过的表不要再执行。Migrate 会逐表检查后再执行。
func (s *Strategy) MigrationSQL() []string {
	var out []string
	for _, g := range s.migrationGroups() {
		out = append(out, g.stmts...)
	}
	return out
}

// Migrate 把 v0.1.x 的旧表升级成当前结构:加 service 列,旧数据全部归到 Options.Service,
// 主键改为 (service, rule_key),meta 由单行(id=1)改为每个服务一行。已经是新结构的表会跳过,可以重复执行;
// 最后调用 EnsureSchema 补齐缺的表。
//
// SQLite、PostgreSQL、金仓的 DDL 在事务里,失败整体回滚;MySQL 与达梦的 DDL 会隐式提交,中途失败要按报错手工处理,
// 建议先备份。迁移前请停掉所有旧版本实例。
func (s *Strategy) Migrate(ctx context.Context) error {
	if err := s.checkService(); err != nil {
		return err
	}
	legacy := s.legacyCols()
	transactional := s.opts.Dialect != MySQL && s.opts.Dialect != DM
	for _, g := range s.migrationGroups() {
		st, err := s.state(ctx, g.table, legacy[g.table])
		if err != nil {
			return err
		}
		if st != tableLegacy {
			continue
		}
		if err := s.runStmts(ctx, g.stmts, transactional); err != nil {
			return fmt.Errorf("apidoc/sql: 迁移表 %s 失败: %w", g.table, err)
		}
	}
	return s.EnsureSchema(ctx)
}

func (s *Strategy) runStmts(ctx context.Context, stmts []string, transactional bool) error {
	if !transactional {
		for _, stmt := range stmts {
			if _, err := s.opts.DB.ExecContext(ctx, stmt); err != nil {
				return fmt.Errorf("%w\n%s", err, stmt)
			}
		}
		return nil
	}
	tx, err := s.opts.DB.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	for _, stmt := range stmts {
		if _, err := tx.ExecContext(ctx, stmt); err != nil {
			_ = tx.Rollback()
			return fmt.Errorf("%w\n%s", err, stmt)
		}
	}
	return tx.Commit()
}

// Load 加载全部规则。
func (s *Strategy) Load(ctx context.Context) (strategy.Rules, error) {
	// 先读版本号再读规则:两者之间若有修改,记下的版本偏旧,下次检查会再加载一次,不会漏
	v, err := s.Version(ctx)
	if err != nil {
		return nil, err
	}
	rows, err := s.opts.DB.QueryContext(ctx, s.q("SELECT rule_key, rules FROM "+s.t.rule+" WHERE service = ?"), s.opts.Service)
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
		if strategy.ReservedKey(key) {
			continue // @templates 等保留键不是接口规则
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

// Version 返回本服务的版本号(还没有保存过时为 0)。
func (s *Strategy) Version(ctx context.Context) (int64, error) {
	var v int64
	err := s.opts.DB.QueryRowContext(ctx, s.q("SELECT version FROM "+s.t.meta+" WHERE service = ?"), s.opts.Service).Scan(&v)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return v, err
}

// Save 保存一个接口的规则(空规则表示删除),全局版本号加 1,并写一条修改记录。返回新版本号。
func (s *Strategy) Save(ctx context.Context, key string, rules strategy.MethodRules, by string) (int64, error) {
	if err := s.checkService(); err != nil {
		return 0, err
	}
	if err := s.ensureMeta(ctx); err != nil {
		return 0, fmt.Errorf("apidoc/sql: save %s: %w", key, err)
	}
	if by == "" {
		by = "unknown" // 达梦 / Oracle 兼容模式会把空串当成 NULL
	}
	empty := rules.IsEmpty()
	var after []byte
	if !empty {
		var err error
		if after, err = json.Marshal(rules); err != nil {
			return 0, err
		}
	}
	v, err := s.tx(ctx, func(tx *sql.Tx) (int64, error) {
		// 先加版本号:PostgreSQL / MySQL 会锁住 meta 行,并发保存串行化
		if _, err := tx.ExecContext(ctx, s.q("UPDATE "+s.t.meta+" SET version = version + 1 WHERE service = ?"), s.opts.Service); err != nil {
			return 0, err
		}
		var v int64
		if err := tx.QueryRowContext(ctx, s.q("SELECT version FROM "+s.t.meta+" WHERE service = ?"), s.opts.Service).Scan(&v); err != nil {
			return 0, err
		}
		var before sql.NullString
		err := tx.QueryRowContext(ctx, s.q("SELECT rules FROM "+s.t.rule+" WHERE service = ? AND rule_key = ?"), s.opts.Service, key).Scan(&before)
		if err != nil && !errors.Is(err, sql.ErrNoRows) {
			return 0, err
		}
		now := time.Now().UnixMilli()
		switch {
		case empty:
			if _, err := tx.ExecContext(ctx, s.q("DELETE FROM "+s.t.rule+" WHERE service = ? AND rule_key = ?"), s.opts.Service, key); err != nil {
				return 0, err
			}
		case before.Valid:
			if _, err := tx.ExecContext(ctx, s.q("UPDATE "+s.t.rule+" SET rules = ?, version = ?, updated_by = ?, updated_at = ? WHERE service = ? AND rule_key = ?"),
				string(after), v, by, now, s.opts.Service, key); err != nil {
				return 0, err
			}
		default:
			if _, err := tx.ExecContext(ctx, s.q("INSERT INTO "+s.t.rule+" (service, rule_key, rules, version, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?)"),
				s.opts.Service, key, string(after), v, by, now); err != nil {
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
		if _, err := tx.ExecContext(ctx, s.q("INSERT INTO "+s.t.history+" (service, rule_key, before_rules, after_rules, version, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"),
			s.opts.Service, key, beforeArg, afterArg, v, by, now); err != nil {
			return 0, err
		}
		return v, nil
	})
	if err != nil {
		return 0, fmt.Errorf("apidoc/sql: save %s: %w", key, err)
	}
	s.notifyLocal()
	if s.opts.Notifier != nil {
		if sn, ok := s.opts.Notifier.(ServiceNotifier); ok {
			_ = sn.PublishService(ctx, s.opts.Service, v)
		} else {
			_ = s.opts.Notifier.Publish(ctx, v)
		}
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
	query := "SELECT id, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM " + s.t.history + " WHERE service = ?"
	args := []any{s.opts.Service}
	if key != "" {
		query += " AND rule_key = ?"
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
	err := s.opts.DB.QueryRowContext(ctx, s.q("SELECT rule_key, before_rules FROM "+s.t.history+" WHERE id = ? AND service = ?"), changeID, s.opts.Service).Scan(&key, &before)
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
		wake := func() {
			select {
			case c <- struct{}{}:
			default:
			}
		}
		go func() {
			if sn, ok := s.opts.Notifier.(ServiceNotifier); ok {
				_ = sn.SubscribeService(ctx, func(service string, _ int64) {
					if service == s.opts.Service {
						wake()
					}
				})
				return
			}
			_ = s.opts.Notifier.Subscribe(ctx, func(int64) { wake() })
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
