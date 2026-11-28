package apidoc

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"reflect"
	"regexp"
	"strings"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/tag"
)

type builder struct {
	doc   *Doc
	rules strategy.Rules
	types map[string]reflect.Type // 不为 nil 时记录遇到的结构体类型
}

// methodCtx 是生成一个接口时的上下文。
type methodCtx struct {
	key        string
	httpMethod string
	rules      strategy.MethodRules
	keyCount   map[string]int
	paramTypes []string
	wrapData   string       // 生成返回时:外层包装里业务数据字段的 json 名
	wrapResp   reflect.Type // 生成返回时:替换进 wrapData 字段的业务类型

	bodyPrefix string       // 请求体字段规则路径的前缀,如 "request."
	checks     []FieldCheck // 请求校验

	outPaths  []string                  // 返回里要输出的字段路径(json 名),用于编译过滤掩码
	mapPaths  map[string]bool           // 值为 map 的返回字段路径
	docHidden map[*model.Parameter]bool // 规则 hidden=true:文档不显示,但不影响输出
}

func (b *builder) method(info GroupInfo, op *Operation, httpMethod, rawPath, name, key string, mr strategy.MethodRules) (*model.Method, *filter.Node, []FieldCheck) {
	full := docPath(rawPath)
	apiPath, paths := []string{full}, []string{}
	if info.Path != "" {
		base := docPath(info.Path)
		if rest, ok := strings.CutPrefix(full, base); ok && (rest == "" || strings.HasPrefix(rest, "/")) {
			apiPath = []string{base}
			if rest != "" {
				paths = []string{rest}
			}
		}
	}
	title := op.summary
	if title == "" {
		title = name
	}
	// 有效状态:规则存储里的覆盖 > 代码里的声明(不是 DEFAULT 时)> DEFAULT。DEFAULT 的声明视为"没标"。
	status := model.StatusDefault
	switch {
	case strategy.ValidStatus(mr.Status):
		status = mr.Status
	case op.status != "" && op.status != model.StatusDefault:
		status = op.status
	}
	tags := op.tags
	if tags == nil {
		tags = []string{}
	}
	headers := append([]model.MethodHeader(nil), b.doc.opts.MethodHeaders...)
	mc := &methodCtx{key: key, httpMethod: httpMethod, rules: mr, keyCount: map[string]int{},
		mapPaths: map[string]bool{}, docHidden: map[*model.Parameter]bool{}}

	m := &model.Method{
		ApiPath:       apiPath,
		ClassName:     info.Name,
		Description:   op.description,
		Group:         []string{model.Default},
		Key:           key,
		MethodHeaders: headers,
		Methods:       []string{httpMethod},
		Name:          name,
		ParamNames:    []string{},
		ParamType:     model.ParamDefault,
		Paths:         paths,
		Request:       []*model.Model{},
		Response:      []*model.Model{},
		Search:        uniq(op.description, title),
		SimpleName:    simpleName(info.Name),
		Status:        status,
		StatusNote:    mr.StatusNote,
		Tags:          tags,
		Value:         title,
		Version:       []string{model.Default},
	}
	m.GlobalHeaderHints = mergeHeaderHints(b.doc.opts.HeaderHints, info.HeaderHints, op.headerHints)
	m.AuthHint = firstAuth(op.authHint, info.Auth, b.doc.opts.Auth)
	b.request(m, op, rawPath, mc)
	b.response(m, op, mc)
	m.ParamTypes = uniq(mc.paramTypes...)
	if len(mc.paramTypes) == 0 {
		m.ParamTypes = []string{model.ParamDefault}
	}
	var mask *filter.Node
	if mode := b.doc.filterMode(strategy.Key(info.Name, name)); mode != filter.ModeOff && len(mr.Response) > 0 && (op.resp != nil || b.wrapper(op) != nil) {
		// 与 Java 一致:只有返回字段路径真正命中过规则的接口才过滤(ResponseRuleIndex.markResponseRule),
		// 规则路径过期、和返回类型对不上时原样输出。返回值不是结构体(如 map)时无从判断,沿用路径规则。
		if tr := b.collectTypeRules(op, mr); tr.hit || tr.root == nil {
			if mode == filter.ModeCsap {
				mask = tr.csapMask()
			} else {
				out := mc.outPaths
				if w := b.wrapper(op); w != nil && op.resp == nil {
					// 没声明 Resp:业务数据的类型未知,整个放行
					out = append(out, w.DataField)
				}
				mask = filter.Compile(out, mc.mapPaths)
			}
		}
	}
	for _, md := range m.Request {
		md.Parameters = removeHidden(md.Parameters, mc.docHidden)
	}
	for _, md := range m.Response {
		md.Parameters = removeHidden(md.Parameters, mc.docHidden)
	}
	return m, mask, mc.checks
}

// mergeHeaderHints 按 全局 < 分组 < 接口 合并请求头建议,同名时后者覆盖,保持首次出现的顺序。
// 与 Java DocHintsCollector 一致,结果总是非 nil(输出 [])。
func mergeHeaderHints(levels ...[]model.GlobalHeaderHint) []model.GlobalHeaderHint {
	out := []model.GlobalHeaderHint{}
	idx := map[string]int{}
	for _, l := range levels {
		for _, h := range l {
			if i, ok := idx[h.Name]; ok {
				out[i] = h
				continue
			}
			idx[h.Name] = len(out)
			out = append(out, h)
		}
	}
	return out
}

func firstAuth(hs ...*model.AuthHint) *model.AuthHint {
	for _, h := range hs {
		if h != nil {
			return h
		}
	}
	return nil
}

func (b *builder) wrapper(op *Operation) *Wrapper {
	if b.doc.opts.Wrapper != nil && !op.noWrap {
		return b.doc.opts.Wrapper
	}
	return nil
}

func removeHidden(ps []*model.Parameter, hidden map[*model.Parameter]bool) []*model.Parameter {
	if len(hidden) == 0 {
		return ps
	}
	out := ps[:0:0]
	for _, p := range ps {
		if hidden[p] {
			continue
		}
		if p.Children != nil {
			p.Children.Parameters = removeHidden(p.Children.Parameters, hidden)
			p.Parameters = p.Children.Parameters
		}
		out = append(out, p)
	}
	if out == nil {
		out = []*model.Parameter{}
	}
	return out
}

// ---------- 请求 ----------

func isBodyMethod(m string) bool {
	return m == "POST" || m == "PUT" || m == "PATCH"
}

type reqField struct {
	sf        reflect.StructField
	meta      tag.Meta
	name      string
	paramType string
}

func (b *builder) request(m *model.Method, op *Operation, rawPath string, mc *methodCtx) {
	bodyName := op.bodyName
	if bodyName == "" {
		bodyName = "request"
	}
	mc.bodyPrefix = bodyName + "."
	if t := deref(op.req); t != nil && t.Kind() == reflect.Struct {
		pp := pathParams(rawPath)
		var bodyFields []reqField
		for _, f := range flatFields(t) {
			rf := classify(f, mc.httpMethod, pp)
			if rf.paramType == model.ParamBody {
				bodyFields = append(bodyFields, rf)
				continue
			}
			if p := b.basicParam(rf, mc); p != nil {
				m.Request = append(m.Request, &model.Model{
					MethodParamName: model.Str(rf.name),
					ModelType:       model.ModelBaseData,
					Name:            rf.sf.Type.String(),
					Parameters:      []*model.Parameter{p},
				})
				m.ParamNames = append(m.ParamNames, rf.name)
			}
		}
		if len(bodyFields) > 0 {
			md := b.objectModel(t, model.ModelObject)
			for _, rf := range bodyFields {
				if p := b.field(rf.sf, rf.meta, bodyName+".", true, mc, []reflect.Type{t}); p != nil {
					md.Parameters = append(md.Parameters, p)
				}
			}
			m.Request = append(m.Request, md)
			m.ParamNames = append(m.ParamNames, bodyName)
		}
	}
	if op.body != nil {
		t := deref(op.body)
		mt := model.ModelObject
		if t.Kind() == reflect.Slice || t.Kind() == reflect.Array {
			mt = model.ModelArray
			t = deref(t.Elem())
		}
		md := b.objectModel(t, mt)
		if t.Kind() == reflect.Struct {
			md.Parameters = b.structParams(t, bodyName+".", true, mc, []reflect.Type{t})
		}
		m.Request = append(m.Request, md)
		m.ParamNames = append(m.ParamNames, bodyName)
	}
}

// classify 决定请求字段的位置和参数名。
func classify(f reflect.StructField, httpMethod string, pathParams map[string]bool) reqField {
	meta := tag.Parse(f)
	rf := reqField{sf: f, meta: meta, name: meta.Name}
	switch {
	case meta.In != "":
		rf.paramType = meta.In
		if meta.In == "FORM_DATA" || meta.In == "FORMDATA" {
			rf.paramType = model.ParamFormData
		}
		switch {
		case meta.In == model.ParamPath && meta.URI != "":
			rf.name = meta.URI
		case meta.In == model.ParamHeader && meta.Header != "":
			rf.name = meta.Header
		case (meta.In == model.ParamQuery || meta.In == model.ParamForm) && meta.Form != "":
			rf.name = meta.Form
		}
	case meta.URI != "":
		rf.paramType, rf.name = model.ParamPath, meta.URI
	case meta.Header != "":
		rf.paramType, rf.name = model.ParamHeader, meta.Header
	case pathParams[meta.Name]:
		rf.paramType = model.ParamPath
	case meta.Form != "" && !(meta.HasJSON && isBodyMethod(httpMethod)):
		rf.name = meta.Form
		if isBodyMethod(httpMethod) {
			rf.paramType = model.ParamForm
		} else {
			rf.paramType = model.ParamQuery
		}
	case isBodyMethod(httpMethod):
		rf.paramType = model.ParamBody
	default:
		rf.paramType = model.ParamQuery
	}
	return rf
}

// basicParam 生成路径、查询、请求头这类单值参数(对应 Java 的 @RequestParam 基本类型参数)。
// 规则路径为 "<名>.<名>",与 Java 一致;查不到时回退纯 "<名>"(早期版本写出的规则文件用这种写法)。
// keyName 始终是 "<名>.<名>",新写入的规则也用它。
func (b *builder) basicParam(rf reqField, mc *methodCtx) *model.Parameter {
	if rf.meta.Name == "-" || rf.meta.Hidden {
		return nil
	}
	keyName := rf.name + "." + rf.name
	rule, hasRule := strategy.Lookup(mc.rules.Request, keyName)
	if !hasRule {
		rule, hasRule = strategy.Lookup(mc.rules.Request, rf.name)
	}
	p := &model.Parameter{
		DataType:     dataType(rf.sf.Type, rf.meta),
		DefaultValue: rf.meta.DefaultValue,
		Description:  rf.meta.Description,
		Example:      rf.meta.Example,
		Group:        []string{model.Default},
		KeyName:      keyName,
		LongDataType: model.Str(rf.sf.Type.String()),
		Name:         rf.name,
		ParamType:    rf.paramType,
		Position:     rf.meta.Position,
		Required:     rf.meta.Required || rf.paramType == model.ParamPath,
		Value:        rf.meta.Value,
		Version:      []string{model.Default},
	}
	applyRequestRule(p, rule, hasRule)
	mc.addCheck(p, keyName, nil, rule.Validate)
	if hasRule && rule.Hidden != nil && *rule.Hidden {
		return nil // 文档里隐藏,校验照常
	}
	p.Key = mc.paramKey("req", keyName)
	mc.paramTypes = append(mc.paramTypes, p.ParamType)
	return p
}

// addCheck 记录一个需要校验的请求字段。bodyPath 为 nil 表示单值参数(按 Name 从对应位置取值)。
func (mc *methodCtx) addCheck(p *model.Parameter, keyName string, bodyPath []string, rules []strategy.ValidateRule) {
	if !p.Required && len(rules) == 0 && !typedParam(p.DataType) {
		return
	}
	in := p.ParamType
	if in == model.ParamBody && bodyPath == nil {
		bodyPath = []string{p.Name}
	}
	if in != model.ParamBody {
		bodyPath = nil
	}
	remark := p.Value
	if remark == "" {
		remark = p.Name
	}
	mc.checks = append(mc.checks, FieldCheck{
		In: in, Name: p.Name, Path: bodyPath, Field: keyName, FieldName: p.Name, Remark: remark,
		Required: p.Required, DataType: p.DataType, Rules: rules,
	})
}

func (mc *methodCtx) addCheckNoType(p *model.Parameter, keyName string, bodyPath []string, rules []strategy.ValidateRule) {
	if !p.Required && len(rules) == 0 {
		return
	}
	n := len(mc.checks)
	mc.addCheck(p, keyName, bodyPath, rules)
	if len(mc.checks) > n {
		mc.checks[n].DataType = ""
	}
}

func typedParam(dataType string) bool {
	switch dataType {
	case "Integer", "Long", "Double", "Float", "Boolean":
		return true
	}
	return false
}

func applyRequestRule(p *model.Parameter, rule strategy.FieldRule, ok bool) {
	if !ok {
		return
	}
	if rule.Description != "" {
		p.Description = rule.Description
	}
	if rule.Required != nil {
		p.Required = *rule.Required
	}
	// DEFAULT 表示"未指定"(devtools 新加字段时前端会填接口的 paramType,默认就是 DEFAULT),不覆盖推断出的位置
	if pt := strings.ToUpper(rule.ParamType); pt != "" && pt != model.ParamDefault {
		p.ParamType = pt
	}
	if rule.Example != "" {
		p.Example = rule.Example
	}
	if len(rule.Validate) > 0 {
		p.Validate = make([]model.ValidateAttribute, 0, len(rule.Validate))
		for _, v := range rule.Validate {
			p.Validate = append(p.Validate, model.ValidateAttribute{
				Code: v.Code, Descr: v.Descr, Level: v.Level, Message: v.Message, Pattern: v.Pattern, Type: v.Type,
			})
		}
	}
}

// ---------- 返回 ----------

func (b *builder) response(m *model.Method, op *Operation, mc *methodCtx) {
	resp := op.resp
	w := b.wrapper(op)
	if w == nil {
		if resp == nil {
			return
		}
		t := deref(resp)
		mt := model.ModelObject
		if t.Kind() == reflect.Slice || t.Kind() == reflect.Array {
			mt = model.ModelArray
			t = deref(t.Elem())
		}
		md := b.objectModel(t, mt)
		if isBase(t) {
			md.ModelType = model.ModelBaseData
			md.Description, md.Value = nil, nil
		} else if t.Kind() == reflect.Struct {
			md.Parameters = b.structParams(t, "", false, mc, []reflect.Type{t})
		}
		m.Response = append(m.Response, md)
		return
	}
	wt := deref(reflect.TypeOf(w.Type))
	md := b.objectModel(wt, model.ModelTObject)
	if w.Title != "" {
		md.Value = model.Str(w.Title)
	}
	if w.Description != "" {
		md.Description = model.Str(w.Description)
	}
	mc.wrapData = w.DataField
	mc.wrapResp = resp
	md.Parameters = b.structParams(wt, "", false, mc, []reflect.Type{wt})
	mc.wrapData = ""
	m.Response = append(m.Response, md)
}

// ---------- 字段遍历 ----------

func (b *builder) structParams(t reflect.Type, prefix string, request bool, mc *methodCtx, stack []reflect.Type) []*model.Parameter {
	if b.types != nil && t.Name() != "" {
		b.types[fullName(t)] = t
	}
	out := []*model.Parameter{}
	for _, f := range flatFields(t) {
		meta := tag.Parse(f)
		if p := b.field(f, meta, prefix, request, mc, stack); p != nil {
			out = append(out, p)
		}
	}
	return out
}

// field 生成一个结构体字段的文档节点。返回 nil 表示该字段既不在文档里、也不输出。
//
// 显示规则:接口配置了任意返回规则后,返回字段只保留 include: true 的、doc_force 的,
// 以及有下级被保留的上级字段;请求规则同理(规则里出现即保留)。没有规则的接口保留全部字段。
// doc_hidden 的字段不显示也不输出(与 Java hidden 一致);规则 hidden: true 只在文档里隐藏。
func (b *builder) field(f reflect.StructField, meta tag.Meta, prefix string, request bool, mc *methodCtx, stack []reflect.Type) *model.Parameter {
	if meta.Name == "-" || meta.Hidden {
		return nil
	}
	ft := f.Type
	// 统一外层包装的 data 字段替换成业务返回类型
	if !request && prefix == "" && mc.wrapData != "" && meta.Name == mc.wrapData {
		if mc.wrapResp == nil {
			return nil
		}
		ft = mc.wrapResp
	}
	keyName := prefix + meta.Name
	rules := mc.rules.Response
	if request {
		rules = mc.rules.Request
	}
	rule, hasRule := strategy.Lookup(rules, keyName)
	selected := len(rules) == 0 || meta.Force
	if !selected {
		if request {
			selected = hasRule
		} else {
			selected = hasRule && rule.Include != nil && *rule.Include
		}
	}

	paramType := model.ParamQuery // Java @ApiModelProperty.paramType 的默认值
	if request {
		paramType = model.ParamBody
	}
	p := &model.Parameter{
		DataType:     dataType(ft, meta),
		DefaultValue: meta.DefaultValue,
		Description:  meta.Description,
		Example:      meta.Example,
		ExtendDescr:  []map[string]string{},
		Group:        []string{model.Default},
		KeyName:      keyName,
		ModelType:    model.Str(modelType(ft)),
		Name:         meta.Name,
		ParamType:    paramType,
		Position:     meta.Position,
		Required:     meta.Required,
		Value:        meta.Value,
		Version:      []string{model.Default},
	}

	// 子节点
	elem, isList := childType(ft)
	if elem != nil && !inStack(stack, elem) {
		mt := model.ModelObject
		if isList {
			mt = model.ModelArray
		}
		child := b.objectModel(elem, mt)
		child.Parameters = b.structParams(elem, keyName+".", request, mc, append(stack, elem))
		if selected || len(child.Parameters) > 0 {
			p.Children = child
			p.Parameters = child.Parameters
		}
	}
	if !selected && p.Children == nil {
		return nil
	}
	if !request {
		if selected {
			mc.outPaths = append(mc.outPaths, keyName)
		}
		if p.Children != nil && deref(ft).Kind() == reflect.Map {
			mc.mapPaths[keyName] = true
		}
	}

	if request {
		applyRequestRule(p, rule, hasRule)
		mc.paramTypes = append(mc.paramTypes, p.ParamType)
		if selected {
			var bodyPath []string
			if rest, ok := strings.CutPrefix(keyName, mc.bodyPrefix); ok && mc.bodyPrefix != "" {
				bodyPath = strings.Split(rest, ".")
			}
			if p.DataType != "" && !isBase(ft) {
				// 对象、列表字段只做必填/规则校验,不做类型校验
				mc.addCheckNoType(p, keyName, bodyPath, rule.Validate)
			} else {
				mc.addCheck(p, keyName, bodyPath, rule.Validate)
			}
		}
	} else if hasRule {
		if rule.Description != "" {
			p.Description = rule.Description
		}
		if rule.Required != nil {
			p.Required = *rule.Required
		}
		if rule.Example != "" {
			p.Example = rule.Example
		}
		if rule.Mock != nil {
			p.Mock = rule.Mock // 只用于 Mock 响应,见 mock 包
		}
	}
	if hasRule && rule.Hidden != nil && *rule.Hidden && !meta.Force {
		mc.docHidden[p] = true
	}
	side := "resp"
	if request {
		side = "req"
	}
	p.Key = mc.paramKey(side, keyName)
	return p
}

func (mc *methodCtx) paramKey(side, keyName string) string {
	k := side + "|" + keyName
	mc.keyCount[k]++
	h := sha1.Sum([]byte(mc.key + "|" + k + "|" + string(rune('0'+mc.keyCount[k]))))
	return hex.EncodeToString(h[:12])
}

func inStack(stack []reflect.Type, t reflect.Type) bool {
	for _, s := range stack {
		if s == t {
			return true
		}
	}
	return false
}

// flatFields 返回可导出字段,匿名嵌入且没写 json 名的结构体展开(与 encoding/json 一致)。
func flatFields(t reflect.Type) []reflect.StructField {
	var out []reflect.StructField
	for i := 0; i < t.NumField(); i++ {
		f := t.Field(i)
		if f.Anonymous {
			ft := deref(f.Type)
			name := strings.Split(f.Tag.Get("json"), ",")[0]
			if ft.Kind() == reflect.Struct && name == "" {
				out = append(out, flatFields(ft)...)
				continue
			}
		}
		if !f.IsExported() {
			continue
		}
		out = append(out, f)
	}
	return out
}

func (b *builder) objectModel(t reflect.Type, mt string) *model.Model {
	return &model.Model{
		Description: model.Str(""),
		Group:       []string{model.Default},
		ModelType:   mt,
		Name:        fullName(t),
		Parameters:  []*model.Parameter{},
		Value:       model.Str(b.modelName(t)),
		Version:     []string{model.Default},
	}
}

var modelNamerType = reflect.TypeOf((*ModelNamer)(nil)).Elem()

// modelName 返回结构体在文档里的显示名:Options.TypeNames > 类型的 ApidocName() > displayName。
// 只影响模型的显示名(Java @ApiModel.value),字段的 dataType 仍按类型名生成。
func (b *builder) modelName(t reflect.Type) string {
	t = deref(t)
	if b != nil && b.doc != nil {
		if n, ok := b.doc.opts.TypeNames[t]; ok && n != "" {
			return n
		}
	}
	if t.Kind() == reflect.Struct && reflect.PointerTo(t).Implements(modelNamerType) {
		if n := callModelNamer(t); n != "" {
			return n
		}
	}
	return displayName(t)
}

// callModelNamer 在零值上调用 ApidocName;方法 panic 时当作没有实现。
func callModelNamer(t reflect.Type) (name string) {
	defer func() {
		if recover() != nil {
			name = ""
		}
	}()
	return reflect.New(t).Interface().(ModelNamer).ApidocName()
}

// ---------- 类型映射 ----------

var (
	timeType   = reflect.TypeOf(time.Time{})
	numberType = reflect.TypeOf(json.Number(""))
	rawType    = reflect.TypeOf(json.RawMessage(nil))
	bytesType  = reflect.TypeOf([]byte(nil))
)

func deref(t reflect.Type) reflect.Type {
	for t != nil && t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	return t
}

func isBase(t reflect.Type) bool {
	t = deref(t)
	switch t {
	case timeType, numberType, rawType, bytesType:
		return true
	}
	switch t.Kind() {
	case reflect.Bool, reflect.String,
		reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
		reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64,
		reflect.Float32, reflect.Float64:
		return true
	}
	return false
}

// childType 返回需要展开子字段的结构体类型,以及它是否在列表里。
func childType(t reflect.Type) (reflect.Type, bool) {
	t = deref(t)
	if isBase(t) {
		return nil, false
	}
	switch t.Kind() {
	case reflect.Struct:
		return t, false
	case reflect.Slice, reflect.Array:
		e := deref(t.Elem())
		if e.Kind() == reflect.Struct && !isBase(e) {
			return e, true
		}
	case reflect.Map:
		e := deref(t.Elem())
		if e.Kind() == reflect.Struct && !isBase(e) {
			return e, false
		}
	}
	return nil, false
}

func modelType(t reflect.Type) string {
	t = deref(t)
	if isBase(t) {
		return model.ModelBaseData
	}
	switch t.Kind() {
	case reflect.Slice, reflect.Array:
		return model.ModelArray
	}
	return model.ModelObject
}

// dataType 返回字段类型的显示名,沿用 Java 的写法(String、Integer、Long、List<X>…),
// 这样文档页面和依赖 dataType 的下游(如 MCP 工具生成)不用改。
func dataType(t reflect.Type, meta tag.Meta) string {
	if meta.DataType != "" {
		return meta.DataType
	}
	return javaName(t)
}

func javaName(t reflect.Type) string {
	t = deref(t)
	switch t {
	case timeType:
		return "Date"
	case numberType:
		return "Number"
	case rawType:
		return "Object"
	case bytesType:
		return "String"
	}
	switch t.Kind() {
	case reflect.Bool:
		return "Boolean"
	case reflect.String:
		return "String"
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32,
		reflect.Uint8, reflect.Uint16, reflect.Uint32:
		return "Integer"
	case reflect.Int64, reflect.Uint, reflect.Uint64:
		return "Long"
	case reflect.Float32:
		return "Float"
	case reflect.Float64:
		return "Double"
	case reflect.Slice, reflect.Array:
		return "List<" + javaName(t.Elem()) + ">"
	case reflect.Map:
		return "Map<" + javaName(t.Key()) + ", " + javaName(t.Elem()) + ">"
	case reflect.Interface:
		return "Object"
	case reflect.Struct:
		return displayName(t)
	}
	return t.String()
}

var qualified = regexp.MustCompile(`[\w./-]*\.`)

// displayName 返回类型的短名,泛型参数去掉包路径并改成尖括号:
// Page[github.com/x/app.Item] -> Page<Item>。
func displayName(t reflect.Type) string {
	t = deref(t)
	n := t.Name()
	if n == "" {
		return javaName(t)
	}
	i := strings.Index(n, "[")
	if i < 0 {
		return n
	}
	args := qualified.ReplaceAllString(n[i+1:len(n)-1], "")
	args = strings.ReplaceAll(args, ",", ", ")
	args = strings.NewReplacer("[", "<", "]", ">").Replace(args)
	return n[:i] + "<" + args + ">"
}

// fullName 返回类型全名(包路径 + 类型名,不含泛型参数),对应 Java 的类全名。
func fullName(t reflect.Type) string {
	t = deref(t)
	n := t.Name()
	if i := strings.Index(n, "["); i >= 0 {
		n = n[:i]
	}
	if n == "" {
		return javaName(t)
	}
	if t.PkgPath() == "" {
		return n
	}
	return t.PkgPath() + "." + n
}
