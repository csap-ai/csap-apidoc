package devtools

import (
	"errors"
	"net/http"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/fieldlib"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// 字段库接口(1.4.0,见 docs/design/1.4.0-field-library.md 第 5 节)。套用本身是前端把模板内容写进字段,
// 走现有的保存字段规则接口;这里只管模板的查询和项目模板的增删。

// GET /csap/yaml/fieldTemplates:内置 + 项目模板合并后的列表,每项带来源(builtin / project)。
func (d *Devtools) fieldTemplates(_ http.ResponseWriter, r *http.Request) (any, error) {
	project := fieldlib.File{}
	writable := false
	if rs, ok := d.opts.Store.(strategy.RawStore); ok {
		f, _, err := fieldlib.LoadProject(r.Context(), rs)
		if err != nil {
			return nil, err
		}
		project, writable = f, true
	}
	return map[string]any{
		"templates": fieldlib.Merge(fieldlib.Builtin(), project.Templates),
		"writable":  writable,
	}, nil
}

// POST /csap/yaml/fieldTemplates:新增或覆盖一个项目模板,请求体就是模板本身(name、title、description、example、required、validate)。
func (d *Devtools) saveFieldTemplate(_ http.ResponseWriter, r *http.Request) (any, error) {
	rs, ok := d.opts.Store.(strategy.RawStore)
	if !ok {
		return nil, errors.New("当前规则来源不支持保存项目模板")
	}
	var t fieldlib.Template
	if err := decodeBody(r, &t); err != nil {
		return nil, err
	}
	f, _, err := fieldlib.LoadProject(r.Context(), rs)
	if err != nil {
		return nil, err
	}
	if err := f.Upsert(t); err != nil {
		return nil, err
	}
	if _, err := fieldlib.SaveProject(r.Context(), rs, f, d.opts.User(r)); err != nil {
		return nil, err
	}
	return t, nil
}

// DELETE /csap/yaml/fieldTemplates/{name}:删除一个项目模板(内置模板只读,不能删)。
func (d *Devtools) deleteFieldTemplate(r *http.Request, name string) (any, error) {
	rs, ok := d.opts.Store.(strategy.RawStore)
	if !ok {
		return nil, errors.New("当前规则来源不支持保存项目模板")
	}
	f, _, err := fieldlib.LoadProject(r.Context(), rs)
	if err != nil {
		return nil, err
	}
	if !f.Remove(name) {
		for _, b := range fieldlib.Builtin() {
			if b.Name == name {
				return nil, errors.New("内置模板只读,不能删除(可以新建同名项目模板覆盖它)")
			}
		}
		return nil, errors.New("模板不存在: " + name)
	}
	if _, err := fieldlib.SaveProject(r.Context(), rs, f, d.opts.User(r)); err != nil {
		return nil, err
	}
	return true, nil
}
