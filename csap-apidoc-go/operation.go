package apidoc

import (
	"reflect"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// Operation 描述一条路由的文档信息,对应 Java 的 @ApiOperation 加方法签名。
// Go 的 handler 签名都一样,看不出请求和返回用的是哪个结构体,所以要在注册路由时声明一次:
//
//	apidoc.Op("UserController", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{})
//
// 不声明的路由也会出现在文档里,只是没有参数和返回。
type Operation struct {
	group       string
	name        string
	summary     string
	description string
	req         reflect.Type
	body        reflect.Type
	bodyName    string
	resp        reflect.Type
	noWrap      bool
	hidden      bool
	tags        []string
	status      string
	headerHints []model.GlobalHeaderHint
	authHint    *model.AuthHint
}

// Op 新建一条接口声明。group 对应 Java 的 Controller 全名(规则文件的一级键),
// name 对应方法名(二级键)。两者都可以留空,留空时由适配器按 handler 的函数名推断。
func Op(group, name string) *Operation {
	return &Operation{group: group, name: name}
}

// Summary 设置接口标题(Java @ApiOperation.value)。
func (o *Operation) Summary(s string) *Operation { o.summary = s; return o }

// Description 设置接口详细说明。
func (o *Operation) Description(s string) *Operation { o.description = s; return o }

// Req 声明请求结构体。每个字段按标签决定参数位置:in > uri(PATH) > header(HEADER) >
// 路径参数同名(PATH) > form(GET 为 QUERY,其他为 FORM) > json(非 GET 为 BODY);
// 都没有时 GET/DELETE 为 QUERY,其余为 BODY。BODY 字段合并成一个名为 BodyName 的对象参数。
func (o *Operation) Req(v any) *Operation { o.req = typeOf(v); return o }

// Body 声明整个请求体的类型(相当于 Java 的 @RequestBody),参数名默认 "request"。
func (o *Operation) Body(v any) *Operation { o.body = typeOf(v); return o }

// BodyName 设置请求体参数名,即规则路径的第一段(默认 "request",如 request.username)。
func (o *Operation) BodyName(name string) *Operation { o.bodyName = name; return o }

// Resp 声明业务返回类型(不含统一外层包装)。
func (o *Operation) Resp(v any) *Operation { o.resp = typeOf(v); return o }

// NoWrap 表示该接口的返回不套统一外层包装。
func (o *Operation) NoWrap() *Operation { o.noWrap = true; return o }

// Hidden 表示该接口不出现在文档里。
func (o *Operation) Hidden() *Operation { o.hidden = true; return o }

// Tags 设置标签。
func (o *Operation) Tags(tags ...string) *Operation { o.tags = tags; return o }

// Deprecated 标记接口已过时。
func (o *Operation) Deprecated() *Operation { o.status = model.StatusDeprecated; return o }

// HeaderHint 添加一条"在线调试"全局请求头建议(对应 Java @DocGlobalHeader),
// 文档页面调试时自动预填,不影响后端处理。同名时接口 > 分组 > Options 全局。
func (o *Operation) HeaderHint(name, description, example string, required bool) *Operation {
	o.headerHints = append(o.headerHints, model.GlobalHeaderHint{Name: name, Description: description, Example: example, Required: required})
	return o
}

// Auth 设置"在线调试"的认证建议(对应 Java @DocAuth),scheme 为 bearer / basic / oauth2_client / none。
// API Key 用 APIKeyAuth。优先级:接口 > 分组 > Options 全局。
func (o *Operation) Auth(scheme, description string) *Operation {
	o.authHint = NewAuthHint(scheme, description, "", "")
	return o
}

// APIKeyAuth 设置 API Key 认证建议,in 为 header 或 query,name 为参数名。
func (o *Operation) APIKeyAuth(in, name, description string) *Operation {
	o.authHint = NewAuthHint("apikey", description, in, name)
	return o
}

// NewAuthHint 生成认证建议。与 Java 一致:只有 apikey 方案才保留 in 和 name。
func NewAuthHint(scheme, description, in, name string) *model.AuthHint {
	h := &model.AuthHint{Scheme: scheme, Description: description}
	if scheme == "apikey" {
		h.In, h.Name = model.Str(in), model.Str(name)
	}
	return h
}

// Group 返回分组名。
func (o *Operation) GroupName() string { return o.group }

// Name 返回接口名。
func (o *Operation) Name() string { return o.name }

func typeOf(v any) reflect.Type {
	if v == nil {
		return nil
	}
	if t, ok := v.(reflect.Type); ok {
		return t
	}
	return reflect.TypeOf(v)
}

// GroupInfo 描述一个分组,对应 Java 的 Controller(@Api)。
type GroupInfo struct {
	Name        string   // 全名,规则文件的一级键,如 com.example.UserController
	Title       string   // 显示名(@Api.value),默认取 Name 的最后一段
	Description string   //
	Position    int      // 排序,越小越靠前,相同时按 Name 排
	Path        string   // 公共路径前缀(@RequestMapping),用于拆分 apiPath / paths
	Tags        []string //
	Hidden      bool     // 整个分组不出现在文档里
	// HeaderHints、Auth 是分组级的"在线调试"建议(对应 Java 类上的 @DocGlobalHeader / @DocAuth)。
	HeaderHints []model.GlobalHeaderHint
	Auth        *model.AuthHint
}

// Route 是适配器收集到的一条路由。
type Route struct {
	Method  string
	Path    string
	Handler string // handler 函数名,如 github.com/x/app.(*UserController).Get-fm
}
