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

// csapMask 按 Java 的方式编译返回过滤掩码(filter.ModeCsap)。
//
// 与 Java DefaultAbstractMethod.group / FilterClassParam / SimplePropertyPreFilter 一致:
//   - 沿返回类型链逐个字段遍历,字段 include(doc_force 或规则 include: true)则加入所在类型的白名单,
//     否则在"白名单里还没有它"时加入黑名单;
//   - 只有被放行的字段才继续展开它的类型;
//   - 同一类型出现在多个路径时,规则合并到同一个过滤器;
//   - 输出时先看黑名单,再看白名单:白名单非空只输出白名单,白名单为空则输出黑名单之外的全部。
func (b *builder) csapMask(op *Operation, mr strategy.MethodRules) *filter.Node {
	filters := map[reflect.Type]*classFilter{}
	var root reflect.Type
	w := b.wrapper(op)
	if w != nil {
		root = deref(reflect.TypeOf(w.Type))
	} else {
		root = elemStruct(op.resp)
	}
	if root == nil || root.Kind() != reflect.Struct {
		return nil
	}
	// fieldTypes 记录每个类型每个字段展开后的类型(wrapper 的 data 字段被替换)
	fieldTypes := map[reflect.Type]map[string]reflect.Type{}

	var walk func(t reflect.Type, prefix string, stack []reflect.Type, top bool)
	walk = func(t reflect.Type, prefix string, stack []reflect.Type, top bool) {
		cf := filters[t]
		if cf == nil {
			cf = &classFilter{includes: map[string]bool{}, excludes: map[string]bool{}}
			filters[t] = cf
			fieldTypes[t] = map[string]reflect.Type{}
		}
		for _, f := range flatFields(t) {
			meta := tag.Parse(f)
			if meta.Name == "-" || meta.Hidden {
				continue
			}
			ft := f.Type
			if top && w != nil && meta.Name == w.DataField {
				if op.resp == nil {
					continue
				}
				ft = op.resp
			}
			name := meta.Name
			if _, seen := fieldTypes[t][name]; !seen {
				cf.order = append(cf.order, name)
			}
			fieldTypes[t][name] = ft
			rule, ok := strategy.Lookup(mr.Response, prefix+name)
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
	walk(root, "", []reflect.Type{root}, true)

	nodes := map[reflect.Type]*filter.Node{}
	var node func(t reflect.Type) *filter.Node
	node = func(t reflect.Type) *filter.Node {
		cf := filters[t]
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
				if !n.Open {
					continue
				}
				continue
			}
			if !n.Open && !cf.includes[name] {
				continue
			}
			ft := fieldTypes[t][name]
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
	if n := node(root); n != nil {
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
