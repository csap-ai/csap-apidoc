package devtools

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

const jsonContentType = "application/json;charset=UTF-8"

// 返回格式与 Java ApidocResult 一致:{code,data,language,message,success,time}。
// 出错时与 Java 全局异常处理一致回 HTTP 200、code 非 "0",前端按 success / code 判断并提示 message。
func writeOK(w http.ResponseWriter, data any) {
	raw, err := model.MarshalCsap(data, 1)
	if err != nil {
		writeError(w, http.StatusOK, "500", err.Error())
		return
	}
	w.Header().Set("Content-Type", jsonContentType)
	_, _ = w.Write(model.ResultJSON("0", "成功", raw))
}

func writeError(w http.ResponseWriter, status int, code, msg string) {
	w.Header().Set("Content-Type", jsonContentType)
	w.WriteHeader(status)
	_, _ = w.Write(model.ResultJSON(code, msg, nil))
}

// badRequest 对应 Spring 缺少 @RequestParam 时的 400。
func badRequest(w http.ResponseWriter, r *http.Request, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusBadRequest)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"timestamp": time.Now().Format("2006-01-02T15:04:05.000-07:00"),
		"status":    400,
		"error":     "Bad Request",
		"message":   msg,
		"path":      r.URL.Path,
	})
}

type apiFunc func(w http.ResponseWriter, r *http.Request) (any, error)

func (d *Devtools) serveAPI(w http.ResponseWriter, r *http.Request) {
	type ep struct {
		method string
		fn     apiFunc
	}
	routes := map[string]ep{
		"/csap/yaml/scannerApi":              {http.MethodGet, d.scannerAPI},
		"/csap/yaml/getAllMethod":            {http.MethodGet, d.getAllMethod},
		"/csap/yaml/addRequestParam":         {http.MethodPost, d.addRequestParam},
		"/csap/yaml/addResponseParam":        {http.MethodPost, d.addResponseParam},
		"/csap/yaml/getMethodValidateFields": {http.MethodGet, d.getMethodValidateFields},
		"/csap/yaml/validatePatternTypes":    {http.MethodGet, d.validatePatternTypes},
		"/csap/yaml/write":                   {http.MethodPost, d.write},
		"/csap/yaml/writeSelect":             {http.MethodPost, d.writeSelect},
		"/csap/yaml/writeFieldMethod":        {http.MethodPost, d.writeFieldMethod},
		"/csap/yaml/history":                 {http.MethodGet, d.history},
		"/csap/yaml/rollback":                {http.MethodPost, d.rollback},
		"/devtools/getFields":                {http.MethodPost, d.getFields},
		"/devtools/hiddenMethod":             {http.MethodPost, d.hiddenMethod},
	}
	e, ok := routes[r.URL.Path]
	if !ok {
		http.NotFound(w, r)
		return
	}
	if r.Method != e.method {
		w.Header().Set("Allow", e.method)
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if e.method == http.MethodPost {
		// 只接受 JSON:浏览器跨站提交表单(CSRF)发不出 application/json,跨站 fetch 又会被 CORS 预检拦下
		ct, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
		if ct != "application/json" {
			writeError(w, http.StatusUnsupportedMediaType, "415", "请求体必须是 application/json")
			return
		}
		r.Body = http.MaxBytesReader(w, r.Body, d.opts.MaxBodyBytes)
	}
	data, err := e.fn(w, r)
	if err != nil {
		var pe paramError
		if errors.As(err, &pe) {
			badRequest(w, r, pe.Error())
			return
		}
		writeError(w, http.StatusOK, "500", err.Error())
		return
	}
	if data == nil {
		return // 已经自己输出
	}
	writeOK(w, data)
}

// paramError 表示缺少必填的查询参数(Java 回 Spring 的 400)。
type paramError string

func (e paramError) Error() string {
	return "Required request parameter '" + string(e) + "' is not present"
}

func decodeBody(r *http.Request, v any) error {
	b, err := io.ReadAll(r.Body)
	if err != nil {
		return err
	}
	if len(strings.TrimSpace(string(b))) == 0 {
		return nil
	}
	if err := json.Unmarshal(b, v); err != nil {
		return fmt.Errorf("请求体格式错误: %w", err)
	}
	return nil
}

// ---------- 查询 ----------

// GET /csap/yaml/scannerApi?className=:所有分组(Java 的 Controller 列表),methodList 为空。
func (d *Devtools) scannerAPI(_ http.ResponseWriter, r *http.Request) (any, error) {
	name := strings.TrimSpace(r.URL.Query().Get("className"))
	out := []model.Controller{}
	for _, c := range d.doc.Snapshot().Doc.ApiList {
		if name != "" && c.Name != name {
			continue
		}
		cc := *c
		cc.MethodList = []*model.Method{}
		out = append(out, cc)
	}
	return out, nil
}

// GET /csap/yaml/getAllMethod?className=:分组下全部接口,请求参数和返回字段不套规则(完整字段)。
func (d *Devtools) getAllMethod(_ http.ResponseWriter, r *http.Request) (any, error) {
	name := r.URL.Query().Get("className")
	out := []*model.Method{}
	if name == "" {
		return out, nil
	}
	snap := d.doc.Snapshot()
	c := snap.Controller(name)
	if c == nil {
		return out, nil
	}
	seen := map[string]bool{}
	for _, m := range c.MethodList {
		key := strategy.Key(name, m.Name)
		if seen[key] {
			continue
		}
		seen[key] = true
		if fm := snap.FullMethod(key); fm != nil {
			out = append(out, fm)
		}
	}
	return out, nil
}

// fieldRequest 对应 Java GetFieldExcled。
type fieldRequest struct {
	ClassName           string   `json:"className"`
	ExcludeFields       []string `json:"excledFields"`
	AppendName          string   `json:"appendName"`
	ControllerClassName string   `json:"controllerClassName"`
	MethodName          string   `json:"methodName"`
	ParameterIndex      *int     `json:"parameterIndex"`
}

// POST /devtools/getFields:某个类型(或某个接口的第 n 个请求参数、返回值)的全部字段,不套规则。
// 前端用它列出"可选字段"。与 Java 一致:优先按接口取(能拿到泛型的实际类型),取不到再按类名。
func (d *Devtools) getFields(_ http.ResponseWriter, r *http.Request) (any, error) {
	var req fieldRequest
	if err := decodeBody(r, &req); err != nil {
		return nil, err
	}
	snap := d.doc.Snapshot()
	exclude := map[string]bool{}
	for _, f := range req.ExcludeFields {
		exclude[f] = true
	}
	var params []*model.Parameter
	found := false
	if req.ControllerClassName != "" && req.MethodName != "" {
		if m := snap.FullMethod(strategy.Key(req.ControllerClassName, req.MethodName)); m != nil {
			idx := 0
			if req.ParameterIndex != nil {
				idx = *req.ParameterIndex
			}
			switch {
			case idx < 0 && len(m.Response) > 0:
				params, found = m.Response[0].Parameters, true
			case idx >= 0 && idx < len(m.Request):
				params, found = m.Request[idx].Parameters, true
			}
		}
	}
	if !found && req.ClassName != "" {
		params = snap.TypeFields(req.ClassName)
	}
	return toFields(params, "", exclude), nil
}

// GET /csap/yaml/getMethodValidateFields?className=&methodName=&fieldName=:某个请求字段已保存的规则。
func (d *Devtools) getMethodValidateFields(_ http.ResponseWriter, r *http.Request) (any, error) {
	q := r.URL.Query()
	for _, p := range []string{"className", "methodName", "fieldName"} {
		if !q.Has(p) {
			return nil, paramError(p)
		}
	}
	rules, err := d.storeRules(r.Context(), strategy.Key(q.Get("className"), q.Get("methodName")))
	if err != nil {
		return nil, err
	}
	pd := paramData{Validate: []validateItem{}}
	if fr, ok := rules.Request[q.Get("fieldName")]; ok {
		pd = fromFieldRule(fr)
	}
	return pd, nil
}

// patternModel 对应 Java ValidatePatternType.ValidatePatternTypeModel。
type patternModel struct {
	Code    *string `json:"code"`
	Descr   string  `json:"descr"`
	Level   *int    `json:"level"`
	Message *string `json:"message"`
	Pattern *string `json:"pattern"`
	Type    int     `json:"type"`
}

// GET /csap/yaml/validatePatternTypes:内置正则列表(与 Java 同编号;Java 写错的几条已修正,见 validate/patterns.go)。
func (d *Devtools) validatePatternTypes(http.ResponseWriter, *http.Request) (any, error) {
	list := make([]patternModel, 0, len(validate.Patterns))
	one, code, msg := 1, "1001", "数据不能为空"
	for _, p := range validate.Patterns {
		pat := validate.JavaPattern(p.Pattern)
		list = append(list, patternModel{Code: &code, Descr: p.Descr, Level: &one, Message: &msg, Pattern: &pat, Type: p.Type})
	}
	types := []patternModel{
		{Type: 1, Descr: "常规表达式"},
		{Type: 2, Descr: "校验数字的表达式"},
		{Type: 3, Descr: "校验字符的表达式"},
		{Type: 4, Descr: "特殊需求表达式"},
	}
	return map[string]any{"patternList": list, "patternTypeList": types}, nil
}

// POST /devtools/hiddenMethod:Java 版是空实现(直接返回 true),这里保持一致。
func (d *Devtools) hiddenMethod(_ http.ResponseWriter, r *http.Request) (any, error) {
	var m methodModel
	if err := decodeBody(r, &m); err != nil {
		return nil, err
	}
	return true, nil
}

// GET /csap/yaml/history?className=&methodName=&limit=:修改记录(Go 版新增),最新的在前。
// 不传 className/methodName 时返回全部接口的记录;也可以直接传 key=<分组.接口>。
func (d *Devtools) history(_ http.ResponseWriter, r *http.Request) (any, error) {
	h, ok := d.opts.Store.(strategy.Historian)
	if !ok {
		return nil, errors.New("当前规则来源不支持修改记录")
	}
	q := r.URL.Query()
	key := q.Get("key")
	if key == "" && q.Get("className") != "" && q.Get("methodName") != "" {
		key = strategy.Key(q.Get("className"), q.Get("methodName"))
	}
	limit, _ := strconv.Atoi(q.Get("limit"))
	cs, err := h.History(r.Context(), key, limit)
	if err != nil {
		return nil, err
	}
	out := make([]changeView, 0, len(cs))
	for _, c := range cs {
		out = append(out, toChangeView(c))
	}
	return out, nil
}

// changeView 是修改记录的输出格式,时间为毫秒时间戳(与 ApidocResult.time 一致)。
type changeView struct {
	After     *strategy.MethodRules `json:"after"`
	Before    *strategy.MethodRules `json:"before"`
	ID        int64                 `json:"id"`
	Key       string                `json:"key"`
	UpdatedAt int64                 `json:"updatedAt"`
	UpdatedBy string                `json:"updatedBy"`
	Version   int64                 `json:"version"`
}

func toChangeView(c strategy.Change) changeView {
	return changeView{After: c.After, Before: c.Before, ID: c.ID, Key: c.Key, UpdatedAt: c.UpdatedAt.UnixMilli(),
		UpdatedBy: c.UpdatedBy, Version: c.Version}
}

// POST /csap/yaml/rollback {"id":<修改记录 ID>}:把接口恢复成该次修改之前的样子(Go 版新增)。
// 回滚本身也会留一条修改记录。返回新的版本号。
func (d *Devtools) rollback(_ http.ResponseWriter, r *http.Request) (any, error) {
	h, ok := d.opts.Store.(strategy.Historian)
	if !ok {
		return nil, errors.New("当前规则来源不支持回滚")
	}
	var req struct {
		ID int64 `json:"id"`
	}
	if err := decodeBody(r, &req); err != nil {
		return nil, err
	}
	if req.ID <= 0 {
		return nil, errors.New("修改记录 id 未传")
	}
	v, err := h.Rollback(r.Context(), req.ID, d.opts.User(r))
	if err != nil {
		return nil, err
	}
	d.refresh(r.Context())
	return map[string]int64{"version": v}, nil
}

// ---------- 写入 ----------

// POST /csap/yaml/addRequestParam:保存某个接口的请求参数选择(必填、位置、校验规则)。
func (d *Devtools) addRequestParam(_ http.ResponseWriter, r *http.Request) (any, error) {
	return d.addMethodParam(r, true)
}

// POST /csap/yaml/addResponseParam:保存某个接口的返回字段选择(白名单)。
func (d *Devtools) addResponseParam(_ http.ResponseWriter, r *http.Request) (any, error) {
	return d.addMethodParam(r, false)
}

func (d *Devtools) addMethodParam(r *http.Request, request bool) (any, error) {
	var m methodModel
	if err := decodeBody(r, &m); err != nil {
		return nil, err
	}
	if strings.TrimSpace(m.ClassName) == "" {
		return nil, errors.New("类名称不能为空")
	}
	if strings.TrimSpace(m.MethodName) == "" {
		return nil, errors.New("方法名称不能为空")
	}
	key := strategy.Key(m.ClassName, m.MethodName)
	if d.doc.Snapshot().FullMethod(key) == nil {
		return nil, fmt.Errorf("接口不存在: %s", key)
	}
	old, err := d.storeRules(r.Context(), key)
	if err != nil {
		return nil, err
	}
	rules := applyMethodModel(old, m)
	if err := d.save(r, key, rules); err != nil {
		return nil, err
	}
	return true, nil
}

// POST /csap/yaml/writeFieldMethod:直接写入 {分组: {接口: {request, response}}}(Java ParamGroupMethodProperty 格式)。
func (d *Devtools) writeFieldMethod(_ http.ResponseWriter, r *http.Request) (any, error) {
	var in map[string]map[string]methodProperty
	if err := decodeBody(r, &in); err != nil {
		return nil, err
	}
	return d.writeProperties(r, in)
}

// POST /csap/yaml/write:Java StandardProperties,Go 版只处理其中的 methodFieldMap(字段规则);
// controllerMap、methodMap 等 Java 的接口定义 Go 版由代码声明,忽略。
func (d *Devtools) write(_ http.ResponseWriter, r *http.Request) (any, error) {
	var in struct {
		MethodFieldMap map[string]map[string]methodProperty `json:"methodFieldMap"`
	}
	if err := decodeBody(r, &in); err != nil {
		return nil, err
	}
	return d.writeProperties(r, in.MethodFieldMap)
}

func (d *Devtools) writeProperties(r *http.Request, in map[string]map[string]methodProperty) (any, error) {
	groups := make([]string, 0, len(in))
	for g := range in {
		groups = append(groups, g)
	}
	sort.Strings(groups)
	n := 0
	for _, g := range groups {
		names := make([]string, 0, len(in[g]))
		for name := range in[g] {
			names = append(names, name)
		}
		sort.Strings(names)
		for _, name := range names {
			if err := d.saveNoRefresh(r, strategy.Key(g, name), in[g][name].toRules()); err != nil {
				return nil, err
			}
			n++
		}
	}
	if n > 0 {
		d.refresh(r.Context())
	}
	return true, nil
}

// POST /csap/yaml/writeSelect ["分组",...]:把选中分组(空数组表示全部)里还没有规则的接口,
// 按当前代码生成一份完整规则(请求字段全部列出、返回字段全部放行)写入,作为在线编辑的起点。
// 已经有规则的接口不动。对应 Java 按扫描结果生成 YAML。
func (d *Devtools) writeSelect(_ http.ResponseWriter, r *http.Request) (any, error) {
	var classes []string
	if err := decodeBody(r, &classes); err != nil {
		return nil, err
	}
	want := map[string]bool{}
	for _, c := range classes {
		want[c] = true
	}
	snap := d.doc.Snapshot()
	n := 0
	for _, c := range snap.Doc.ApiList {
		if len(want) > 0 && !want[c.Name] {
			continue
		}
		seen := map[string]bool{}
		for _, m := range c.MethodList {
			key := strategy.Key(c.Name, m.Name)
			if seen[key] {
				continue
			}
			seen[key] = true
			old, err := d.storeRules(r.Context(), key)
			if err != nil {
				return nil, err
			}
			if !old.IsEmpty() {
				continue
			}
			rules := initialRules(snap.FullMethod(key))
			if rules.IsEmpty() {
				continue
			}
			if err := d.saveNoRefresh(r, key, rules); err != nil {
				return nil, err
			}
			n++
		}
	}
	if n > 0 {
		d.refresh(r.Context())
	}
	return true, nil
}

// ---------- 规则读写 ----------

// storeRules 读取 Store 里该接口的规则(不含其他来源合并进来的),作为修改的基础。
// Store 不能单独读取时退回到合并后的规则。
func (d *Devtools) storeRules(ctx context.Context, key string) (strategy.MethodRules, error) {
	if s, ok := d.opts.Store.(strategy.Strategy); ok {
		rules, err := s.Load(ctx)
		if err != nil && rules == nil {
			return strategy.MethodRules{}, err
		}
		return strategy.Clone(rules[key]), nil
	}
	return strategy.Clone(d.doc.Snapshot().Rules[key]), nil
}

func (d *Devtools) save(r *http.Request, key string, rules strategy.MethodRules) error {
	if err := d.saveNoRefresh(r, key, rules); err != nil {
		return err
	}
	d.refresh(r.Context())
	return nil
}

func (d *Devtools) saveNoRefresh(r *http.Request, key string, rules strategy.MethodRules) error {
	if _, err := d.opts.Store.Save(r.Context(), key, rules, d.opts.User(r)); err != nil {
		return fmt.Errorf("保存规则失败: %w", err)
	}
	return nil
}

// refresh 立即重新加载规则、生成文档,不等 Watch 的轮询。部分来源加载失败时保留原来的规则(见 Doc.Refresh)。
func (d *Devtools) refresh(ctx context.Context) {
	_ = d.doc.Refresh(context.WithoutCancel(ctx))
}
