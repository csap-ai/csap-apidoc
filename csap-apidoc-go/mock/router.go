package mock

import (
	"sort"
	"strings"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/jsonschema"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// Prefix 是 Mock 的固定入口前缀。不提供配置项:两种语言的路径必须一致,文档页也靠它识别。
const Prefix = "/csap/mock"

// Router 按文档里的路径把请求匹配到接口(docs/design/1.4.0-mock-schema.md 2.2)。
type Router struct{ eps []*entry }

type entry struct {
	m            *model.Method
	segs         []string // 字面段原样,占位符为 ""
	literal      int
	placeholders int
}

// NewRouter 收集全部可 Mock 的接口。隐藏的接口、不在文档里的接口不 Mock;DEPRECATED 照常。
func NewRouter(doc *model.Response) *Router {
	r := &Router{}
	for _, c := range doc.ApiList {
		if c == nil || c.Hidden {
			continue
		}
		hidden := map[string]bool{}
		for _, h := range c.HiddenMethod {
			hidden[h] = true
		}
		for _, m := range c.MethodList {
			if m == nil || m.Hidden || hidden[m.Name] {
				continue
			}
			e := &entry{m: m}
			full := strings.Trim(jsonschema.FullPath(m), "/")
			if full != "" {
				for _, s := range strings.Split(full, "/") {
					if strings.HasPrefix(s, "{") && strings.HasSuffix(s, "}") {
						e.segs = append(e.segs, "")
						e.placeholders++
					} else {
						e.segs = append(e.segs, s)
						e.literal++
					}
				}
			}
			r.eps = append(r.eps, e)
		}
	}
	return r
}

// Result 是匹配结果。
type Result struct {
	Method *model.Method // 命中的接口
	Status int           // 200 命中;404 路径不存在;405 路径匹配但方法都不对
	Allow  []string      // 405 时允许的方法
}

// Match 匹配请求:path 是去掉 /csap/mock 前缀后的原路径。
// 占位符(含带正则的写法)各匹配一个非空段;方法必须在接口的 methods 里(methods 为空视为任意方法);
// 多个接口都匹配时:字面段数多的优先 → 占位符少的优先 → key 字典序小的优先。
func (r *Router) Match(httpMethod, path string) Result {
	trimmed := strings.Trim(path, "/")
	var segs []string
	if trimmed != "" {
		segs = strings.Split(trimmed, "/")
	}
	var okPath, okMethod []*entry
	allow := map[string]bool{}
	for _, e := range r.eps {
		if !e.matchPath(segs) {
			continue
		}
		okPath = append(okPath, e)
		if len(e.m.Methods) == 0 {
			okMethod = append(okMethod, e)
			continue
		}
		for _, m := range e.m.Methods {
			allow[strings.ToUpper(m)] = true
			if strings.EqualFold(m, httpMethod) {
				okMethod = append(okMethod, e)
				break
			}
		}
	}
	if len(okMethod) > 0 {
		sort.SliceStable(okMethod, func(i, j int) bool {
			a, b := okMethod[i], okMethod[j]
			if a.literal != b.literal {
				return a.literal > b.literal
			}
			if a.placeholders != b.placeholders {
				return a.placeholders < b.placeholders
			}
			return a.m.Key < b.m.Key
		})
		return Result{Method: okMethod[0].m, Status: 200}
	}
	if len(okPath) > 0 {
		out := make([]string, 0, len(allow))
		for m := range allow {
			out = append(out, m)
		}
		sort.Strings(out)
		return Result{Status: 405, Allow: out}
	}
	return Result{Status: 404}
}

func (e *entry) matchPath(segs []string) bool {
	if len(segs) != len(e.segs) {
		return false
	}
	for i, s := range e.segs {
		if s == "" {
			if segs[i] == "" {
				return false
			}
		} else if s != segs[i] {
			return false
		}
	}
	return true
}
