package apidoc

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/csap-ai/csap-framework-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-framework-apidoc/csap-apidoc-go/openapi"
	"github.com/csap-ai/csap-framework-apidoc/csap-apidoc-go/ui"
)

const jsonContentType = "application/json;charset=UTF-8"

// Paths 返回文档相关的路由(gin 风格的路径写法,*x 为通配),适配器按这个列表把 Handler 挂上去。
func (d *Doc) Paths() []string {
	ps := []string{
		"/csap/apidoc",
		// parent、method、{name} 都由 Handler 内部分发;用通配是因为 Go 的分组名常是带 / 的包路径
		"/csap/apidoc/*name",
		"/csap/openapi.json",
	}
	if !d.opts.DisableUI {
		ps = append(ps, "/csap-api.html", "/favicon.ico", d.opts.UIPrefix+"/assets/*filepath")
	}
	return ps
}

// Handler 返回处理全部文档路由的 http.Handler,按 URL 路径分发,可以直接挂到 http.ServeMux:
//
//	mux.Handle("/csap/", doc.Handler())
//	mux.Handle("/csap-api.html", doc.Handler())
//	mux.Handle("/csap-ui/", doc.Handler())
func (d *Doc) Handler() http.Handler {
	var uiHandler http.Handler
	if !d.opts.DisableUI {
		uiHandler = ui.Handler(d.opts.UIPrefix)
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		p := r.URL.Path
		isUI := p == "/csap-api.html" || p == "/favicon.ico" || strings.HasPrefix(p, d.opts.UIPrefix+"/")
		if isUI {
			if uiHandler == nil {
				http.NotFound(w, r)
				return
			}
			uiHandler.ServeHTTP(w, r)
			return
		}
		if d.opts.DocAuth != nil && !d.opts.DocAuth(r) {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		switch {
		case p == "/csap/apidoc":
			d.serveDoc(w, r)
		case p == "/csap/apidoc/parent":
			d.serveParent(w, r)
		case p == "/csap/apidoc/method":
			d.serveMethod(w, r)
		case strings.HasPrefix(p, "/csap/apidoc/"):
			d.serveByName(w, r, strings.TrimPrefix(p, "/csap/apidoc/"))
		case p == "/csap/openapi.json":
			d.serveOpenAPI(w, r)
		default:
			http.NotFound(w, r)
		}
	})
}

func writeResult(w http.ResponseWriter, data model.Raw) {
	w.Header().Set("Content-Type", jsonContentType)
	_, _ = w.Write(model.ResultJSON("0", "成功", data))
}

// GET /csap/apidoc[?flush=true]:全量文档。
func (d *Doc) serveDoc(w http.ResponseWriter, r *http.Request) {
	if r.URL.Query().Get("flush") == "true" {
		_ = d.Refresh(r.Context())
	}
	writeResult(w, d.Snapshot().data())
}

// GET /csap/apidoc/{name}[?key=]:不带 key 时返回全量文档(与 Java 一致,不按 name 过滤);
// 带 key 时 apiList 只保留该分组,methodList 只保留该接口。
// 与 Java 不同,这里生成的是副本,不修改快照(Java 版会改坏缓存,见 DESIGN 附录 A)。
func (d *Doc) serveByName(w http.ResponseWriter, r *http.Request, name string) {
	snap := d.Snapshot()
	key := r.URL.Query().Get("key")
	if strings.TrimSpace(key) == "" {
		writeResult(w, snap.data())
		return
	}
	cp := *snap.Doc
	cp.ApiList = []*model.Controller{}
	if c := snap.Controller(name); c != nil {
		cc := *c
		cc.MethodList = []*model.Method{}
		for _, m := range c.MethodList {
			if m.Key == key {
				cc.MethodList = append(cc.MethodList, m)
			}
		}
		cp.ApiList = append(cp.ApiList, &cc)
	}
	data, err := model.MarshalCsap(&cp, 1)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeResult(w, data)
}

// GET /csap/apidoc/parent:树形菜单。
func (d *Doc) serveParent(w http.ResponseWriter, _ *http.Request) {
	snap := d.Snapshot()
	doc := snap.Doc
	resp := model.ParentResponse{
		ApiInfo:            doc.ApiInfo,
		ApiList:            make([]model.ParentModel, 0, len(doc.ApiList)),
		EnumList:           doc.EnumList,
		GlobalApiList:      doc.GlobalApiList,
		GlobalRequestParam: doc.GlobalRequestParam,
		Groups:             doc.Groups,
		Resources:          doc.Resources,
		Versions:           doc.Versions,
	}
	for _, c := range doc.ApiList {
		pm := model.ParentModel{Title: c.Value, Key: c.Name, Children: make([]model.MethodModel, 0, len(c.MethodList))}
		for _, m := range c.MethodList {
			pm.Children = append(pm.Children, model.MethodModel{
				IsLeaf: true,
				Key:    m.Key,
				Method: firstOr(m.Methods, "GET"),
				Path:   methodPath(m),
				Title:  m.Value,
				Type:   c.Protocols,
			})
		}
		resp.ApiList = append(resp.ApiList, pm)
	}
	data, err := model.MarshalCsap(resp, 1)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeResult(w, data)
}

// GET /csap/apidoc/method?name=&key=:单个接口详情。
// 与 Java 一致:缺参数回 400(Spring 格式的错误 JSON);找不到接口回 200 空 body。
func (d *Doc) serveMethod(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	for _, p := range []string{"name", "key"} {
		if !q.Has(p) {
			springBadRequest(w, r)
			return
		}
	}
	m := d.Snapshot().Method(q.Get("name"), q.Get("key"))
	if m == nil {
		w.WriteHeader(http.StatusOK)
		return
	}
	resp := model.MethodResponse{
		Description: m.Description,
		Headers:     make([]model.HeaderModel, 0, len(m.MethodHeaders)),
		Method:      firstOr(m.Methods, "GET"),
		Methods:     m.Methods,
		ParamType:   m.ParamType,
		Patch:       methodPath(m),
		Request:     flatten(m.Request),
		Response:    flatten(m.Response),
		Tags:        m.Tags,
		Title:       m.Value,
	}
	for _, h := range m.MethodHeaders {
		resp.Headers = append(resp.Headers, model.HeaderModel{
			Description: h.Description, Example: h.Example, Key: h.Key, Required: h.Required, Value: h.Value,
		})
	}
	data, err := model.MarshalCsap(resp, 1)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeResult(w, data)
}

func (d *Doc) serveOpenAPI(w http.ResponseWriter, r *http.Request) {
	spec := openapi.Build(d.Snapshot().Doc)
	w.Header().Set("Content-Type", "application/json")
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	_ = enc.Encode(spec)
}

func springBadRequest(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusBadRequest)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"timestamp": time.Now().Format("2006-01-02T15:04:05.000-07:00"),
		"status":    400,
		"error":     "Bad Request",
		"path":      r.URL.Path,
	})
}

func firstOr(ss []string, def string) string {
	if len(ss) > 0 {
		return ss[0]
	}
	return def
}

// methodPath 与 Java formatPath(apiPath[0]) + formatPath(paths[0]) 一致。
func methodPath(m *model.Method) string {
	return formatPath(firstOr(m.ApiPath, "")) + formatPath(firstOr(m.Paths, ""))
}

func formatPath(p string) string {
	if p == "" || strings.HasPrefix(p, "/") {
		return p
	}
	return "/" + p
}

// flatten 对应 Java CsapDocController.setChildParam:把各个参数模型的字段摊平成一棵树。
func flatten(models []*model.Model) []*model.RequestModel {
	out := []*model.RequestModel{}
	for _, md := range models {
		out = append(out, params(md.Parameters)...)
	}
	return out
}

func params(ps []*model.Parameter) []*model.RequestModel {
	out := []*model.RequestModel{}
	for _, p := range ps {
		pt := p.ParamType
		if pt == "" {
			pt = model.ParamDefault
		}
		out = append(out, &model.RequestModel{
			Children:     params(p.Parameters),
			DataType:     p.DataType,
			DefaultValue: p.DefaultValue,
			Description:  p.Description,
			Example:      p.Example,
			ExtendDescr:  p.ExtendDescr,
			Key:          p.Key,
			ModelType:    p.ModelType,
			Name:         p.Name,
			ParamType:    pt,
			Required:     p.Required,
			Validate:     p.Validate,
			Value:        p.Value,
		})
	}
	return out
}
