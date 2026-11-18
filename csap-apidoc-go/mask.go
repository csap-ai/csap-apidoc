package apidoc

import (
	"reflect"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/tag"
)

// classFilter 对应 Java 的 FilterClassParam:某个类型的白名单和黑名单。
type classFilter struct {
	includes map[string]bool
	excludes map[string]bool
	order    []string // 字段遍历顺序,用于确定性地生成子掩码
}

// typeRules 是按 Java 的方式(DefaultAbstractMethod.group)遍历返回类型得到的结果:
// 每个类型的白名单 / 黑名单,以及是否有字段命中过返回规则。
type typeRules struct {
	root       reflect.Type
	filters    map[reflect.Type]*classFilter
	fieldTypes map[reflect.Type]map[string]reflect.Type // 每个类型每个字段展开后的类型(wrapper 的 data 字段被替换)
	// hit 表示遍历中至少有一个字段路径命中了返回规则(不论 include 真假)。
	// 对应 Java 的 ResponseRuleIndex.markResponseRule:没有命中任何规则的接口(例如规则路径过期、
	// 和返回类型对不上)不过滤,原样输出。
	hit bool
}

// collectTypeRules 沿返回类型链逐个字段遍历,与 Java DefaultAbstractMethod.group / FilterClassParam 一致:
//   - 字段 include(doc_force 或规则 include: true)则加入所在类型的白名单,
//     否则在"白名单里还没有它"时加入黑名单;
//   - 只有被放行的字段才继续展开它的类型(所以只有这些路径上的规则算"命中");
//   - 同一类型出现在多个路径时,规则合并到同一个过滤器。
//
// 有统一外层包装、但接口没声明 Resp 时,业务数据字段的类型未知,按原样放行(不展开、不过滤)。
func (b *builder) collectTypeRules(op *Operation, mr strategy.MethodRules) *typeRules {
	tr := &typeRules{filters: map[reflect.Type]*classFilter{}, fieldTypes: map[reflect.Type]map[string]reflect.Type{}}
	w := b.wrapper(op)
	if w != nil {
		tr.root = deref(reflect.TypeOf(w.Type))
	} else {
		tr.root = elemStruct(op.resp)
	}
	if tr.root == nil || tr.root.Kind() != reflect.Struct {
		tr.root = nil
		return tr
	}

	var walk func(t reflect.Type, prefix string, stack []reflect.Type, top bool)
	walk = func(t reflect.Type, prefix string, stack []reflect.Type, top bool) {
		cf := tr.filters[t]
		if cf == nil {
			cf = &classFilter{includes: map[string]bool{}, excludes: map[string]bool{}}
			tr.filters[t] = cf
			tr.fieldTypes[t] = map[string]reflect.Type{}
		}
		for _, f := range flatFields(t) {
			meta := tag.Parse(f)
			if meta.Name == "-" || meta.Hidden {
				continue
			}
			ft := f.Type
			name := meta.Name
			rule, ok := strategy.Lookup(mr.Response, prefix+name)
			if ok {
				tr.hit = true
			}
			unknownData := top && w != nil && name == w.DataField && op.resp == nil
			if top && w != nil && name == w.DataField && op.resp != nil {
				ft = op.resp
			}
			if _, seen := tr.fieldTypes[t][name]; !seen {
				cf.order = append(cf.order, name)
			}
			if unknownData {
				// 类型未知:放行整个值(fieldTypes 记 nil,不生成子掩码)
				tr.fieldTypes[t][name] = nil
				cf.includes[name] = true
				delete(cf.excludes, name)
				continue
			}
			tr.fieldTypes[t][name] = ft
			include := meta.Force || (ok && rule.Include != nil && *rule.Include)
			if !include {
				if !cf.includes[name] {
					cf.excludes[name] = true
				}
				continue
			}
			cf.includes[name] = true
			if elem := elemStruct(ft); elem != nil && !inStack(stack, elem) {
				walk(elem, prefix+name+".", append(stack, elem), false)
			}
		}
	}
	walk(tr.root, "", []reflect.Type{tr.root}, true)
	return tr
}

// csapMask 按 Java 的方式编译返回过滤掩码(filter.ModeCsap)。
//
// 与 Java SimplePropertyPreFilter 一致:输出时先看黑名单,再看白名单:
// 白名单非空只输出白名单,白名单为空则输出黑名单之外的全部。
func (tr *typeRules) csapMask() *filter.Node {
	if tr.root == nil {
		return nil
	}
	nodes := map[reflect.Type]*filter.Node{}
	var node func(t reflect.Type) *filter.Node
	node = func(t reflect.Type) *filter.Node {
		cf := tr.filters[t]
		if cf == nil {
			return nil // 没有挂过滤器的类型原样输出
		}
		if n, ok := nodes[t]; ok {
			return n
		}
		n := &filter.Node{Fields: map[string]*filter.Node{}}
		nodes[t] = n
		if len(cf.includes) == 0 {
			n.Open = true
			n.Deny = cf.excludes
		}
		for _, name := range cf.order {
			if cf.excludes[name] {
				continue
			}
			if !n.Open && !cf.includes[name] {
				continue
			}
			ft := tr.fieldTypes[t][name]
			var child *filter.Node
			if elem := elemStruct(ft); elem != nil {
				child = node(elem)
				if child != nil && deref(ft).Kind() == reflect.Map {
					child = &filter.Node{MapValues: child}
				}
			}
			n.Fields[name] = child
		}
		// Java 先查黑名单:同一个名字既在白名单又在黑名单时不输出
		for name := range cf.excludes {
			if !n.Open {
				delete(n.Fields, name)
			}
		}
		return n
	}
	if n := node(tr.root); n != nil {
		return n.Seal()
	}
	return nil
}

// elemStruct 返回值类型里需要逐字段处理的结构体:T、*T、[]T、[]*T、map[K]T。
func elemStruct(t reflect.Type) reflect.Type {
	if t == nil {
		return nil
	}
	elem, _ := childType(t)
	return elem
}
