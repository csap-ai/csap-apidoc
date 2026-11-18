package devtools

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// 规则记录页用的接口,请求/响应与 Java ApidocRuleHistoryController 一致:
// GET /devtools/rules/status、GET /devtools/rules/history、POST /devtools/rules/rollback。
// 只在规则存在数据库(SQL)或 Redis 里时可用;YAML 等来源返回 supported=false 和提示(Go 的 YAML 来源另有 /csap/yaml/history)。

// rollbackBy 是回滚时记录的修改人。
const rollbackBy = "devtools-rollback"

const notSupportedRules = "修改记录与回滚只在数据库模式(strategy/sql)或 Redis 模式(strategy/redis)下可用。" +
	" | Change history and rollback are only available in the database (strategy/sql) or Redis (strategy/redis) mode."

const notSupportedMCP = "Go 版没有 MCP 服务。 | MCP is not available in the Go edition."

// storeType 返回规则存储的类型名(与 Java ApiStrategyType 的名字一致):sql → DATABASE,redis → REDIS,其他按策略名前缀大写。
func (d *Devtools) storeType() (string, bool) {
	s, ok := d.opts.Store.(strategy.Strategy)
	if !ok {
		return "", false
	}
	name := s.Name()
	switch {
	case strings.HasPrefix(name, "sql:"):
		return "DATABASE", true
	case strings.HasPrefix(name, "redis:"):
		return "REDIS", true
	}
	prefix, _, _ := strings.Cut(name, ":")
	return strings.ToUpper(prefix), false
}

// historian 返回支持修改记录的存储;不是数据库 / Redis 时返回 nil。
func (d *Devtools) historian() strategy.Historian {
	if _, ok := d.storeType(); !ok {
		return nil
	}
	h, _ := d.opts.Store.(strategy.Historian)
	return h
}

// hint 是页面显示的后端提示:{zh, en} 对象(按页面语言取),允许换行,前端等宽显示。格式见仓库 docs/design/devtools-hints.md。
type hint struct {
	ZH string `json:"zh"`
	EN string `json:"en"`
}

// rulesHint 是规则来源不支持修改记录时的提示(Go 写法)。
var rulesHint = hint{
	ZH: "修改记录与回滚只在规则存放在数据库或 Redis 时可用。把规则来源换成 strategy/sql 或 strategy/redis:\n\n" +
		"  s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: sqlstrategy.MySQL, Service: \"my-service\"})\n" +
		"  _ = s.EnsureSchema(ctx)\n" +
		"  doc := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{s}})\n\n" +
		"或 redisstrategy.New(redisstrategy.Options{Client: ..., Service: \"my-service\"})。\n" +
		"已有 YAML 规则可以用 strategy.Import 一次性导入。详见 " + docsSite + "/go/",
	EN: "Change history and rollback are only available when rules are stored in a database or Redis. Switch the rule source to strategy/sql or strategy/redis:\n\n" +
		"  s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: sqlstrategy.MySQL, Service: \"my-service\"})\n" +
		"  _ = s.EnsureSchema(ctx)\n" +
		"  doc := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{s}})\n\n" +
		"or redisstrategy.New(redisstrategy.Options{Client: ..., Service: \"my-service\"}).\n" +
		"Existing YAML rules can be imported once with strategy.Import. See " + docsSite + "/go/",
}

// mcpHint 是 MCP 页的提示:Go 版暂不提供 MCP。
var mcpHint = hint{
	ZH: "Go 版暂不提供 MCP 服务。Java 版从 1.3.0 起提供(csap.apidoc.mcp.enabled=true),说明见 " + docsSite + "/guide/mcp",
	EN: "The Go edition does not provide an MCP server yet. The Java edition has had it since 1.3.0 (csap.apidoc.mcp.enabled=true); see " + docsSite + "/guide/mcp",
}

const docsSite = "https://apidoc.csap.ai"

// rulesStatus 对应 GET /devtools/rules/status:{supported, readOnly, paramType},不支持时多一个 hint。
func (d *Devtools) rulesStatus(_ http.ResponseWriter, _ *http.Request) (any, error) {
	t, _ := d.storeType()
	ro := false
	if r, ok := d.opts.Store.(interface{ ReadOnly() bool }); ok {
		ro = r.ReadOnly()
	}
	out := map[string]any{"supported": d.historian() != nil, "readOnly": ro, "paramType": t}
	if d.historian() == nil {
		out["hint"] = rulesHint
	}
	return out, nil
}

// ruleChangeView 对应 Java RuleChange:before / after 是规则 JSON 文本(没有时为 null),时间为毫秒时间戳。
type ruleChangeView struct {
	ID        int64   `json:"id"`
	RuleKey   string  `json:"ruleKey"`
	Before    *string `json:"before"`
	After     *string `json:"after"`
	Version   int64   `json:"version"`
	UpdatedBy string  `json:"updatedBy"`
	UpdatedAt int64   `json:"updatedAt"`
}

func rulesText(m *strategy.MethodRules) *string {
	if m == nil {
		return nil
	}
	b, _ := json.Marshal(m)
	s := string(b)
	return &s
}

// rulesHistory 对应 GET /devtools/rules/history?ruleKey=&limit=:最新的在前;ruleKey 为空返回全部;limit 默认 100,范围 1~1000。
func (d *Devtools) rulesHistory(_ http.ResponseWriter, r *http.Request) (any, error) {
	h := d.historian()
	if h == nil {
		return nil, apiError{"400", notSupportedRules}
	}
	limit := 100
	if v := r.URL.Query().Get("limit"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			limit = n
		}
	}
	if limit < 1 {
		limit = 1
	}
	if limit > 1000 {
		limit = 1000
	}
	cs, err := h.History(r.Context(), r.URL.Query().Get("ruleKey"), limit)
	if err != nil {
		return nil, err
	}
	out := make([]ruleChangeView, 0, len(cs))
	for _, c := range cs {
		out = append(out, ruleChangeView{ID: c.ID, RuleKey: c.Key, Before: rulesText(c.Before), After: rulesText(c.After),
			Version: c.Version, UpdatedBy: c.UpdatedBy, UpdatedAt: c.UpdatedAt.UnixMilli()})
	}
	return out, nil
}

// rulesRollback 对应 POST /devtools/rules/rollback {"changeId": 12}:回滚到该条修改记录之前的内容(回滚本身也留记录,修改人 devtools-rollback)。
// 只读时返回 code 1007;记录不存在返回 code 400。
func (d *Devtools) rulesRollback(_ http.ResponseWriter, r *http.Request) (any, error) {
	h := d.historian()
	if h == nil {
		return nil, apiError{"400", notSupportedRules}
	}
	var body map[string]any
	if err := decodeBody(r, &body); err != nil {
		return nil, err
	}
	var id int64
	switch v := body["changeId"].(type) {
	case float64:
		id = int64(v)
	case string:
		n, err := strconv.ParseInt(v, 10, 64)
		if err != nil {
			return nil, apiError{"400", "缺少 changeId(修改记录 id)。 | changeId is required."}
		}
		id = n
	default:
		return nil, apiError{"400", "缺少 changeId(修改记录 id)。 | changeId is required."}
	}
	if ro, ok := d.opts.Store.(interface{ ReadOnly() bool }); ok && ro.ReadOnly() {
		return nil, apiError{"1007", "规则存储是只读的,不能回滚。 | The rule storage is read-only; rollback is not allowed."}
	}
	v, err := h.Rollback(r.Context(), id, rollbackBy)
	switch {
	case errors.Is(err, strategy.ErrNoChange):
		return nil, apiError{"400", "修改记录不存在。 | Change record not found."}
	case errors.Is(err, strategy.ErrReadOnly):
		return nil, apiError{"1007", "规则存储是只读的,不能回滚。 | The rule storage is read-only; rollback is not allowed."}
	case err != nil:
		return nil, err
	}
	d.refresh(r.Context())
	return map[string]any{"rolledBack": true, "version": v}, nil
}

// mcpStatus 对应 GET /devtools/mcp/status:Go 版没有 MCP,返回 enabled=false 和说明,MCP 页显示"未启用"而不是报错。
func (d *Devtools) mcpStatus(_ http.ResponseWriter, _ *http.Request) (any, error) {
	// persistHint、tokenHint、securityNotes 在 Go 不适用(没有 MCP),不返回
	return map[string]any{"enabled": false, "supported": false, "message": notSupportedMCP, "hint": mcpHint}, nil
}

// mcpUnsupported 是 MCP 页其他接口(tools、audit、auth)的统一回复。
func (d *Devtools) mcpUnsupported(http.ResponseWriter, *http.Request) (any, error) {
	return nil, apiError{"400", notSupportedMCP}
}

// apiError 是带业务码的错误,按 Java ApidocResult.error 输出:HTTP 200,code 非 "0"。
type apiError struct{ code, msg string }

func (e apiError) Error() string { return e.msg }
