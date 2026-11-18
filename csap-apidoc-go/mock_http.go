package apidoc

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/jsonschema"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/mock"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// MockOptions 是 Mock 服务的配置(对应 Java csap.apidoc.mock.*),见 docs/design/1.4.0-mock-schema.md。
// 默认关闭:关闭时不注册 /csap/mock/** 和 /csap/mock-status。访问控制沿用文档接口的 Options.DocAuth。
// 启用时会打一行 WARN 日志,提醒不要在生产环境启用。
type MockOptions struct {
	// Enabled 是总开关。
	Enabled bool
	// Seed 是随机种子,默认 "csap"(结果可复现);"random" 表示每次请求用时间生成新种子(不可复现)。
	// 单次请求可用请求头 X-CSAP-Mock-Seed 或查询参数 _csap_mock_seed 覆盖(请求头优先)。
	Seed string
	// ArraySize 是数组默认长度,范围 0~10,默认 2。用 mock.Size(n) 构造。
	ArraySize *int
	// MaxDepth 是对象嵌套深度上限,范围 1~20,默认 6。
	MaxDepth int
	// DelayMs 是每次响应前固定等待的毫秒数(调试 loading 状态用),默认 0。
	DelayMs int
	// DisableHeuristics 为 true 时关闭字段名启发式(email、phone、id、name 等按名字推测取值)。默认开启。
	DisableHeuristics bool
}

// SchemaOptions 是 JSON Schema 导入的配置(对应 Java csap.apidoc.schema.*)。导出始终开启。
type SchemaOptions struct {
	// ImportEnabled 开启 POST /csap/schema/import,默认关闭:这是"写规则"的端点,导入的校验会在运行时生效。
	ImportEnabled bool
	// Store 是 apply=true 时写入规则的存储(通常与 Options.Strategies 里可写的那个是同一个)。为 nil 时只能预览。
	Store strategy.Saver
	// ReadOnly 为 true 时 apply=true 返回明确错误,预览仍可用。
	ReadOnly bool
}

// docAuthOK 做文档接口的访问控制;不通过时已写出 401,返回 false。
func (d *Doc) docAuthOK(w http.ResponseWriter, r *http.Request) bool {
	if d.opts.DocAuth != nil && !d.opts.DocAuth(r) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return false
	}
	return true
}

func (d *Doc) mockRouter() *mock.Router {
	s := d.Snapshot()
	s.mockOnce.Do(func() { s.mockRouter = mock.NewRouter(s.Doc) })
	return s.mockRouter
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", jsonContentType)
	w.WriteHeader(status)
	_, _ = w.Write(bytes.TrimRight(buf.Bytes(), "\n"))
}

// ANY /csap/mock/{原路径}:按文档里的返回结构生成数据。不校验请求,不调用业务代码。
func (d *Doc) serveMock(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, mock.Prefix)
	if path == "" {
		path = "/"
	}
	res := d.mockRouter().Match(r.Method, path)
	switch res.Status {
	case http.StatusNotFound:
		writeJSON(w, http.StatusNotFound, map[string]string{"code": "MOCK_NOT_FOUND",
			"message": fmt.Sprintf("no documented endpoint for %s %s", r.Method, path)})
		return
	case http.StatusMethodNotAllowed:
		w.Header().Set("Allow", strings.Join(res.Allow, ", "))
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"code": "MOCK_METHOD_NOT_ALLOWED",
			"message": fmt.Sprintf("no documented endpoint for %s %s", r.Method, path)})
		return
	}
	o := d.opts.Mock
	seed := firstNonEmpty(r.Header.Get("X-CSAP-Mock-Seed"), r.URL.Query().Get("_csap_mock_seed"), o.Seed, mock.DefaultSeed)
	if seed == "random" {
		seed = strconv.FormatInt(time.Now().UnixNano(), 10)
	}
	if o.DelayMs > 0 {
		select {
		case <-time.After(time.Duration(o.DelayMs) * time.Millisecond):
		case <-r.Context().Done():
			return
		}
	}
	w.Header().Set("X-CSAP-Mock", "1")
	w.Header().Set("X-CSAP-Mock-Operation", res.Method.Key)
	w.Header().Set("X-CSAP-Mock-Seed", seed)
	v, ok := mock.Generate(res.Method, mock.Options{Seed: seed, ArraySize: o.ArraySize, MaxDepth: o.MaxDepth, DisableHeuristics: o.DisableHeuristics})
	if !ok {
		w.WriteHeader(http.StatusOK) // 没有返回模型:200 + 空 body
		return
	}
	writeJSON(w, http.StatusOK, v)
}

// GET /csap/mock-status:Mock 开启时返回 {"enabled":true,"prefix":"/csap/mock","seed":"csap"}(外层沿用文档接口的统一包装)。
func (d *Doc) serveMockStatus(w http.ResponseWriter, r *http.Request) {
	seed := firstNonEmpty(d.opts.Mock.Seed, mock.DefaultSeed)
	raw, _ := model.MarshalCsap(map[string]any{"enabled": true, "prefix": mock.Prefix, "seed": seed}, 1)
	writeResult(w, raw)
}

func firstNonEmpty(ss ...string) string {
	for _, s := range ss {
		if s != "" {
			return s
		}
	}
	return ""
}

// GET /csap/schema.json[?download=true]:全部接口的 JSON Schema 合集(裸 JSON,不套外层包装)。
func (d *Doc) serveSchemaCollection(w http.ResponseWriter, r *http.Request) {
	if r.URL.Query().Get("download") == "true" {
		w.Header().Set("Content-Disposition", `attachment; filename="csap-schema.json"`)
	}
	writeJSON(w, http.StatusOK, jsonschema.Collection(d.Snapshot().Doc))
}

// findMethod 按接口 key 找文档里的接口(隐藏的不算)。
func (d *Doc) findMethod(key string) *model.Method {
	for _, c := range d.Snapshot().Doc.ApiList {
		if c == nil || c.Hidden {
			continue
		}
		hidden := map[string]bool{}
		for _, h := range c.HiddenMethod {
			hidden[h] = true
		}
		for _, m := range c.MethodList {
			if m != nil && m.Key == key && !m.Hidden && !hidden[m.Name] {
				return m
			}
		}
	}
	return nil
}

// GET /csap/schema/method?key=:单个接口的 Schema(裸 JSON);key 不存在 404。
func (d *Doc) serveSchemaMethod(w http.ResponseWriter, r *http.Request) {
	key := r.URL.Query().Get("key")
	m := d.findMethod(key)
	if m == nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"code": "SCHEMA_NOT_FOUND", "message": "no documented endpoint with key " + key})
		return
	}
	writeJSON(w, http.StatusOK, jsonschema.Method(m))
}

func writeResultErr(w http.ResponseWriter, status int, code, msg string) {
	w.Header().Set("Content-Type", jsonContentType)
	w.WriteHeader(status)
	_, _ = w.Write(model.ResultJSON(code, msg, nil))
}

// POST /csap/schema/import:把 Schema 里的说明、必填、示例、校验转成字段规则。默认只预览,apply=true 才写入
// (修改人 schema-import,走规则存储的正常保存路径:版本号、修改记录、多实例同步;受 Schema.ReadOnly 约束)。
func (d *Doc) serveSchemaImport(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Key     string         `json:"key"`
		Kind    string         `json:"kind"`
		Section string         `json:"section"`
		Schema  map[string]any `json:"schema"`
		Apply   bool           `json:"apply"`
	}
	r.Body = http.MaxBytesReader(w, r.Body, 8<<20)
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeResultErr(w, http.StatusBadRequest, "400", "请求体格式错误: "+err.Error())
		return
	}
	m := d.findMethod(req.Key)
	if m == nil {
		writeResultErr(w, http.StatusNotFound, "404", "文档里没有这个接口: "+req.Key)
		return
	}
	if req.Schema == nil {
		writeResultErr(w, http.StatusBadRequest, "400", "schema 必填")
		return
	}
	res, err := jsonschema.Import(m, req.Kind, req.Section, req.Schema)
	if err != nil {
		writeResultErr(w, http.StatusBadRequest, "400", err.Error())
		return
	}
	if req.Apply {
		if d.opts.Schema.ReadOnly {
			writeResultErr(w, http.StatusOK, "1007", "只读模式,不能写入规则(预览仍可用,apply 传 false)")
			return
		}
		if d.opts.Schema.Store == nil {
			writeResultErr(w, http.StatusOK, "500", "没有配置可写的规则存储(Options.Schema.Store),只能预览")
			return
		}
		cur := d.currentRules(r, req.Key)
		merged := jsonschema.Merge(cur, req.Kind, res.Rules)
		if _, err := d.opts.Schema.Store.Save(r.Context(), req.Key, merged, "schema-import"); err != nil {
			writeResultErr(w, http.StatusOK, "500", "保存规则失败: "+err.Error())
			return
		}
		_ = d.Refresh(r.Context())
		res.Applied = true
	}
	raw, err := model.MarshalCsap(res, 1)
	if err != nil {
		writeResultErr(w, http.StatusOK, "500", err.Error())
		return
	}
	writeResult(w, raw)
}

// currentRules 读取存储里该接口现有的规则(作为合并的基础);存储不能读取时退回到合并后的规则。
func (d *Doc) currentRules(r *http.Request, key string) strategy.MethodRules {
	if s, ok := d.opts.Schema.Store.(strategy.Strategy); ok {
		if rules, err := s.Load(r.Context()); err == nil || rules != nil {
			return strategy.Clone(rules[key])
		}
	}
	return strategy.Clone(d.Snapshot().Rules[key])
}
