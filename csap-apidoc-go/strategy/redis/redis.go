package redis

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"sort"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/rulejson"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// SaveScript 是保存用的 Lua 脚本,与 docs/design/redis-mode.md 第 3 节、Java 版内嵌的是同一段文本
// (save.lua 是 Java 版 csap-apidoc-redis 里同名文件的逐字副本,改动以 Java 为准,TestSaveScriptMatchesJava 会比对)。
//
//go:embed save.lua
var SaveScript string

// Client 是本包需要的最小 Redis 客户端接口。go-redis 的适配器很短,见 docs/REDIS.md。
// 找不到的键 / 字段用 ok=false 表示,不要返回错误。
type Client interface {
	// Eval 执行 Lua 脚本,返回整数结果。
	Eval(ctx context.Context, script string, keys []string, args ...any) (int64, error)
	Get(ctx context.Context, key string) (val string, ok bool, err error)
	HGet(ctx context.Context, key, field string) (val string, ok bool, err error)
	HGetAll(ctx context.Context, key string) (map[string]string, error)
	LRange(ctx context.Context, key string, start, stop int64) ([]string, error)
	// Subscribe 订阅频道,阻塞到 ctx 结束或连接出错;每收到一条消息调用一次 fn。
	// 出错返回后,Watch 会等一会儿重新订阅,轮询兜底不受影响。
	Subscribe(ctx context.Context, channel string, fn func(msg string)) error
}

// Options 是 New 的参数。
type Options struct {
	Client Client
	// KeyPrefix 是键前缀,默认 "csap:apidoc:"。
	KeyPrefix string
	// Service 是服务名,默认 "default"。键里用 {service} 包起来(Redis Cluster 的 hash tag)。
	Service string
	// HistoryLimit 是每个接口保留的修改记录条数。0(未设置)用默认值 200;小于 0 表示不裁剪,保留全部。
	// 与 Java 对齐:Java 脚本对 historyLimit<=0 同样不裁剪(Java 的配置默认值也是 200);
	// Go 里 0 表示"没设置"所以取默认值,想不裁剪请填负数。
	HistoryLimit int
	// Poll 是轮询版本号的间隔,默认 5 秒;小于 0 表示不轮询(只靠订阅)。
	Poll time.Duration
}

// Strategy 把规则存进 Redis,适合多实例部署和在线修改。键约定见 docs/design/redis-mode.md。
// 修改记录只保留最近 HistoryLimit 条;Redis 需要开启 AOF 或 RDB,否则重启会丢规则。
type Strategy struct {
	opts Options
	k    keys

	mu     sync.Mutex
	subs   []chan struct{}
	loaded atomic.Int64
}

type keys struct{ rules, version, changes, channel, historyPrefix string }

const DefaultService = "default"

var (
	_ strategy.Strategy  = (*Strategy)(nil)
	_ strategy.Saver     = (*Strategy)(nil)
	_ strategy.Watcher   = (*Strategy)(nil)
	_ strategy.Historian = (*Strategy)(nil)
)

// New 创建 Redis 策略。
func New(opts Options) *Strategy {
	if opts.KeyPrefix == "" {
		opts.KeyPrefix = "csap:apidoc:"
	}
	if opts.Service == "" {
		opts.Service = DefaultService
	}
	if opts.HistoryLimit == 0 {
		opts.HistoryLimit = 200
	}
	if opts.Poll == 0 {
		opts.Poll = 5 * time.Second
	}
	base := opts.KeyPrefix + "{" + opts.Service + "}:"
	return &Strategy{opts: opts, k: keys{
		rules: base + "rules", version: base + "version", changes: base + "changes",
		channel: base + "changed", historyPrefix: base + "history:",
	}}
}

func (s *Strategy) Name() string { return "redis:" + s.opts.KeyPrefix + "[" + s.opts.Service + "]" }

// Version 返回本服务的版本号(不存在为 0)。
func (s *Strategy) Version(ctx context.Context) (int64, error) {
	v, ok, err := s.opts.Client.Get(ctx, s.k.version)
	if err != nil || !ok {
		return 0, err
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("apidoc/redis: 版本号 %q: %w", v, err)
	}
	return n, nil
}

// Load 加载本服务的全部规则。先读版本号再读规则:两者之间若有修改,记下的版本偏旧,下次检查会再加载一次。
func (s *Strategy) Load(ctx context.Context) (strategy.Rules, error) {
	v, err := s.Version(ctx)
	if err != nil {
		return nil, err
	}
	all, err := s.opts.Client.HGetAll(ctx, s.k.rules)
	if err != nil {
		return nil, err
	}
	out := strategy.Rules{}
	var errs []error
	for key, raw := range all {
		if strategy.ReservedKey(key) {
			continue // @templates 等保留键不是接口规则
		}
		var mr strategy.MethodRules
		if err := json.Unmarshal([]byte(raw), &mr); err != nil {
			errs = append(errs, fmt.Errorf("apidoc/redis: rule %s: %w", key, err))
			continue
		}
		out[key] = mr
	}
	s.loaded.Store(v)
	return out, errors.Join(errs...)
}

// Save 保存一个接口的规则(空规则表示删除),版本号加 1,并写一条修改记录。返回新版本号。
// 规则内容与现有的相同(按 JSON 结构比较)时什么都不做,返回当前版本号。
func (s *Strategy) Save(ctx context.Context, key string, rules strategy.MethodRules, by string) (int64, error) {
	if by == "" {
		by = "unknown"
	}
	after := ""
	if !rules.IsEmpty() {
		b, err := json.Marshal(rules)
		if err != nil {
			return 0, err
		}
		after = string(b)
	}
	// 先按结构比较:别的语言写的 JSON 文本可能不同(键顺序、空白),内容相同就不必加版本号
	cur, ok, err := s.opts.Client.HGet(ctx, s.k.rules, key)
	if err != nil {
		return 0, fmt.Errorf("apidoc/redis: save %s: %w", key, err)
	}
	if sameRules(cur, ok, rules) {
		return s.Version(ctx)
	}
	return s.eval(ctx, key, after, by)
}

var _ strategy.RawStore = (*Strategy)(nil)

// LoadRaw 读取规则 Hash 里一个字段的原始 JSON(用于 @templates 等保留键)。不存在时 ok 为 false。
func (s *Strategy) LoadRaw(ctx context.Context, key string) (string, bool, error) {
	return s.opts.Client.HGet(ctx, s.k.rules, key)
}

// SaveRaw 保存规则 Hash 里一个字段的原始 JSON(用于 @templates 等保留键),raw 为空表示删除。
// 与 Save 走同一段脚本:版本号加 1、写修改记录、发布通知;内容语义相同(按 JSON 比较,忽略空白和键顺序)时什么都不写,返回当前版本号。
func (s *Strategy) SaveRaw(ctx context.Context, key, raw, by string) (int64, error) {
	if by == "" {
		by = "unknown"
	}
	// 与 Java RedisRuleStore.save 一致:调脚本前先按 JSON 语义比较(忽略空白和键顺序)
	cur, ok, err := s.opts.Client.HGet(ctx, s.k.rules, key)
	if err != nil {
		return 0, fmt.Errorf("apidoc/redis: save %s: %w", key, err)
	}
	if rulejson.Same(cur, ok, raw) {
		return s.Version(ctx)
	}
	return s.eval(ctx, key, raw, by)
}

func (s *Strategy) eval(ctx context.Context, key, after, by string) (int64, error) {
	v, err := s.opts.Client.Eval(ctx, SaveScript,
		[]string{s.k.rules, s.k.version, s.k.historyPrefix + key, s.k.changes},
		key, after, by, time.Now().UnixMilli(), s.opts.HistoryLimit, s.k.channel)
	if err != nil {
		return 0, fmt.Errorf("apidoc/redis: save %s: %w", key, err)
	}
	if v < 0 { // 脚本判断没有变化
		return s.Version(ctx)
	}
	s.notifyLocal()
	return v, nil
}

func sameRules(cur string, exists bool, rules strategy.MethodRules) bool {
	if !exists {
		return rules.IsEmpty()
	}
	if rules.IsEmpty() {
		return false
	}
	var mr strategy.MethodRules
	if json.Unmarshal([]byte(cur), &mr) != nil {
		return false
	}
	return reflect.DeepEqual(norm(mr), norm(rules))
}

// norm 去掉空 map / 空切片的差别,便于比较。
func norm(mr strategy.MethodRules) strategy.MethodRules {
	if len(mr.Request) == 0 {
		mr.Request = nil
	}
	if len(mr.Response) == 0 {
		mr.Response = nil
	}
	return mr
}

// record 是 List 里的修改记录(键名固定,见 redis-mode.md 第 2 节)。
type record struct {
	ID        int64   `json:"id"`
	RuleKey   string  `json:"ruleKey"`
	Before    *string `json:"before"`
	After     *string `json:"after"`
	Version   int64   `json:"version"`
	UpdatedBy string  `json:"updatedBy"`
	UpdatedAt int64   `json:"updatedAt"`
}

func (r record) change() (strategy.Change, error) {
	c := strategy.Change{ID: r.ID, Key: r.RuleKey, Version: r.Version, UpdatedBy: r.UpdatedBy, UpdatedAt: time.UnixMilli(r.UpdatedAt)}
	var err error
	if c.Before, err = decodeRules(r.Before); err != nil {
		return c, err
	}
	c.After, err = decodeRules(r.After)
	return c, err
}

func decodeRules(p *string) (*strategy.MethodRules, error) {
	if p == nil {
		return nil, nil
	}
	var mr strategy.MethodRules
	if err := json.Unmarshal([]byte(*p), &mr); err != nil {
		return nil, err
	}
	return &mr, nil
}

func (s *Strategy) records(ctx context.Context, ruleKey string, stop int64) ([]record, error) {
	raws, err := s.opts.Client.LRange(ctx, s.k.historyPrefix+ruleKey, 0, stop)
	if err != nil {
		return nil, err
	}
	out := make([]record, 0, len(raws))
	for _, raw := range raws {
		var r record
		if err := json.Unmarshal([]byte(raw), &r); err != nil {
			return nil, fmt.Errorf("apidoc/redis: 修改记录: %w", err)
		}
		out = append(out, r)
	}
	return out, nil
}

// History 返回修改记录,最新的在前。key 为空返回全部接口的记录;limit<=0 时默认 100。
func (s *Strategy) History(ctx context.Context, key string, limit int) ([]strategy.Change, error) {
	if limit <= 0 {
		limit = 100
	}
	var recs []record
	if key != "" {
		var err error
		if recs, err = s.records(ctx, key, int64(limit-1)); err != nil {
			return nil, err
		}
	} else {
		idx, err := s.opts.Client.HGetAll(ctx, s.k.changes)
		if err != nil {
			return nil, err
		}
		seen := map[string]bool{}
		for _, rk := range idx {
			if seen[rk] || strategy.ReservedKey(rk) {
				continue
			}
			seen[rk] = true
			rs, err := s.records(ctx, rk, int64(limit-1))
			if err != nil {
				return nil, err
			}
			recs = append(recs, rs...)
		}
		sort.Slice(recs, func(i, j int) bool { return recs[i].ID > recs[j].ID })
		if len(recs) > limit {
			recs = recs[:limit]
		}
	}
	out := make([]strategy.Change, 0, len(recs))
	for _, r := range recs {
		c, err := r.change()
		if err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, nil
}

// Rollback 把某条修改记录对应的接口恢复成"修改前"的样子。回滚本身也是一次保存,会留下新的记录。
// 记录不存在或已被裁掉时返回 strategy.ErrNoChange。
func (s *Strategy) Rollback(ctx context.Context, changeID int64, by string) (int64, error) {
	notFound := fmt.Errorf("apidoc/redis: %w: %d", strategy.ErrNoChange, changeID)
	ruleKey, ok, err := s.opts.Client.HGet(ctx, s.k.changes, strconv.FormatInt(changeID, 10))
	if err != nil {
		return 0, err
	}
	if !ok {
		return 0, notFound
	}
	recs, err := s.records(ctx, ruleKey, -1)
	if err != nil {
		return 0, err
	}
	for _, r := range recs {
		if r.ID != changeID {
			continue
		}
		if strategy.ReservedKey(ruleKey) { // 保留键存的不是 MethodRules,原样恢复
			before := ""
			if r.Before != nil {
				before = *r.Before
			}
			return s.SaveRaw(ctx, ruleKey, before, by)
		}
		mr, err := decodeRules(r.Before)
		if err != nil {
			return 0, err
		}
		if mr == nil {
			mr = &strategy.MethodRules{}
		}
		return s.Save(ctx, ruleKey, *mr, by)
	}
	return 0, notFound
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

// Watch 订阅 changed 频道(收到消息立即检查),同时按 Poll 轮询版本号兜底,变化时调用 onChange。阻塞到 ctx 结束。
// 订阅断开会自动重连,期间靠轮询不漏。
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
	go func() {
		for ctx.Err() == nil {
			_ = s.opts.Client.Subscribe(ctx, s.k.channel, func(string) {
				select {
				case c <- struct{}{}:
				default:
				}
			})
			select {
			case <-ctx.Done():
			case <-time.After(time.Second):
				// 重连后补查一次,订阅断开期间的修改不会漏
				select {
				case c <- struct{}{}:
				default:
				}
			}
		}
	}()
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
	check()
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
