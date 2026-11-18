package model

// 本文件的结构体与 Java 版 ai.csap.apidoc.model / ai.csap.apidoc.response 下的类一一对应,
// JSON 字段名与 Java fastjson 输出一致。约定:
//   - 指针、nil 切片在 Java 里是 null,输出时省略(见 csapjson.go);
//   - Java 里默认 new 出来的空集合,这里要显式给空切片,输出为 [];
//   - Java 的 int / boolean 原始类型这里用值类型,总会输出。

// ParamType 对应 Java ai.csap.apidoc.annotation.ParamType。
const (
	ParamPath     = "PATH"
	ParamQuery    = "QUERY"
	ParamBody     = "BODY"
	ParamFormData = "FORM_DATA"
	ParamHeader   = "HEADER"
	ParamForm     = "FORM"
	ParamDefault  = "DEFAULT"
)

// ModelType 对应 Java ai.csap.apidoc.type.ModelType。
const (
	ModelObject   = "OBJECT"
	ModelArray    = "ARRAY"
	ModelTObject  = "T_OBJECT"
	ModelBaseData = "BASE_DATA"
)

// ApiStatus 对应 Java ApiStatus。
const (
	StatusDefault    = "DEFAULT"
	StatusFinish     = "FINISH"
	StatusDeprecated = "DEPRECATED"
)

// Default 是 Java 里 group / version 的默认值 ["default"]。
const Default = "default"

// Result 对应 Java ApidocResult,文档接口的外层包装。
type Result struct {
	Code     string `json:"code"`
	Data     any    `json:"data"`
	Language string `json:"language"`
	Message  string `json:"message"`
	Success  bool   `json:"success"`
	Time     int64  `json:"time"`
}

// Contact 对应 CsapApiInfo.Contact。
type Contact struct {
	Email string `json:"email"`
	Name  string `json:"name"`
	URL   string `json:"url"`
}

// ApiInfo 对应 Java CsapApiInfo。
type ApiInfo struct {
	AuthorizationType string   `json:"authorizationType"`
	Contact           *Contact `json:"contact"`
	Description       string   `json:"description"`
	License           string   `json:"license"`
	LicenseURL        string   `json:"licenseUrl"`
	ServiceURL        string   `json:"serviceUrl"`
	Title             string   `json:"title"`
	Version           string   `json:"version"`
}

// DefaultApiInfo 返回与 Java 默认配置相同的 apiInfo。
func DefaultApiInfo() *ApiInfo {
	return &ApiInfo{
		AuthorizationType: "OAUTH_2",
		Contact:           &Contact{},
		Description:       "Api Documentation",
		License:           "Apache 2.0",
		LicenseURL:        "http://www.apache.org/licenses/LICENSE-2.0",
		ServiceURL:        "urn:tos",
		Title:             "Api Documentation",
		Version:           "1.0",
	}
}

// Resource 对应 Java CsapDocResource。
type Resource struct {
	Name    string `json:"name"`
	URL     string `json:"url"`
	Version string `json:"version"`
}

// EnumCode 对应 CsapDocEnum.CsapDocEnumCode。
type EnumCode struct {
	Code  string `json:"code"`
	Descr string `json:"descr"`
	Name  string `json:"name"`
}

// Enum 对应 Java CsapDocEnum。
type Enum struct {
	EnumList []EnumCode `json:"enumList"`
	Name     string     `json:"name"`
	Value    string     `json:"value"`
}

// Response 对应 Java CsapDocResponse,即 /csap/apidoc 的 data。
type Response struct {
	ApiInfo            *ApiInfo                    `json:"apiInfo"`
	ApiList            []*Controller               `json:"apiList"`
	DocEnumList        []Enum                      `json:"docEnumList"`
	EnumList           map[string][]map[string]any `json:"enumList"`
	GlobalApiList      []*Controller               `json:"globalApiList"`
	GlobalRequestParam []*Model                    `json:"globalRequestParam"`
	Groups             []string                    `json:"groups"`
	Resources          []Resource                  `json:"resources"`
	Versions           []string                    `json:"versions"`
}

// Controller 对应 Java CsapDocModelController。Go 里没有 Controller,对应一个"分组"。
type Controller struct {
	Description  string    `json:"description"`
	Group        []string  `json:"group"`
	Hidden       bool      `json:"hidden"`
	HiddenMethod []string  `json:"hiddenMethod"`
	MethodList   []*Method `json:"methodList"`
	Name         string    `json:"name"`
	Path         []string  `json:"path"`
	Position     int       `json:"position"`
	Protocols    string    `json:"protocols"`
	Search       []string  `json:"search"`
	ShowMethod   []string  `json:"showMethod"`
	SimpleName   string    `json:"simpleName"`
	Status       string    `json:"status"`
	Tags         []string  `json:"tags"`
	Value        string    `json:"value"`
	Version      []string  `json:"version"`
}

// Method 对应 Java CsapDocMethod。
type Method struct {
	ApiPath []string `json:"apiPath"`
	// AuthHint 是文档页面"在线调试"的认证建议(Java 1.1.0 新增),nil 时不输出。
	AuthHint    *AuthHint `json:"authHint"`
	ClassName   string    `json:"className"`
	Description string    `json:"description"`
	// GlobalHeaderHints 是"在线调试"的全局请求头建议(Java 1.1.0 新增),没有时输出 []。
	GlobalHeaderHints []GlobalHeaderHint `json:"globalHeaderHints"`
	Group             []string           `json:"group"`
	Hidden            bool               `json:"hidden"`
	Key               string             `json:"key"`
	MethodHeaders     []MethodHeader     `json:"methodHeaders"`
	Methods           []string           `json:"methods"`
	Name              string             `json:"name"`
	ParamNames        []string           `json:"paramNames"`
	ParamType         string             `json:"paramType"`
	ParamTypes        []string           `json:"paramTypes"`
	Paths             []string           `json:"paths"`
	Request           []*Model           `json:"request"`
	Response          []*Model           `json:"response"`
	Search            []string           `json:"search"`
	SimpleName        string             `json:"simpleName"`
	Status            string             `json:"status"`
	// StatusNote 是接口状态的备注(如替代接口),没有时不输出。
	StatusNote string   `json:"statusNote,omitempty"`
	Tags       []string `json:"tags"`
	Value      string   `json:"value"`
	Version    []string `json:"version"`
}

// GlobalHeaderHint 对应 Java CsapDocGlobalHeaderHint(@DocGlobalHeader):
// 文档页面"在线调试"时自动预填的请求头,不参与后端实际处理。
type GlobalHeaderHint struct {
	Description string `json:"description"`
	Example     string `json:"example"`
	Name        string `json:"name"`
	Required    bool   `json:"required"`
}

// AuthHint 对应 Java CsapDocAuthHint(@DocAuth):"在线调试"的认证建议。
// Scheme 为 bearer / basic / apikey / oauth2_client / none;In、Name 只在 apikey 时有意义
// (In 为 header / query),其他方案时为 nil,不输出。
type AuthHint struct {
	Description string  `json:"description"`
	In          *string `json:"in"`
	Name        *string `json:"name"`
	Scheme      string  `json:"scheme"`
}

// MethodHeader 对应 Java CsapDocMethodHeaders。
type MethodHeader struct {
	Description string `json:"description"`
	Example     string `json:"example"`
	Hidden      bool   `json:"hidden"`
	Key         string `json:"key"`
	Position    int    `json:"position"`
	Required    bool   `json:"required"`
	Value       string `json:"value"`
}

// Model 对应 Java CsapDocModel:一个请求参数(方法参数)或一个返回类型。
type Model struct {
	Description     *string      `json:"description"`
	Force           *bool        `json:"force"`
	Global          *bool        `json:"global"`
	Group           []string     `json:"group"`
	MethodParamName *string      `json:"methodParamName"`
	ModelType       string       `json:"modelType"`
	Name            string       `json:"name"`
	Parameters      []*Parameter `json:"parameters"`
	Value           *string      `json:"value"`
	Version         []string     `json:"version"`
}

// Parameter 对应 Java CsapDocParameter:一个字段。
type Parameter struct {
	Children     *Model              `json:"children"`
	DataType     string              `json:"dataType"`
	Decimals     int                 `json:"decimals"`
	DefaultValue string              `json:"defaultValue"`
	Description  string              `json:"description"`
	Example      string              `json:"example"`
	ExtendDescr  []map[string]string `json:"extendDescr"`
	Group        []string            `json:"group"`
	Hidden       bool                `json:"hidden"`
	Key          string              `json:"key"`
	KeyName      string              `json:"keyName"`
	Length       int                 `json:"length"`
	LongDataType *string             `json:"longDataType"`
	ModelType    *string             `json:"modelType"`
	Name         string              `json:"name"`
	ParamType    string              `json:"paramType"`
	Parameters   []*Parameter        `json:"parameters"`
	Position     int                 `json:"position"`
	Required     bool                `json:"required"`
	Validate     []ValidateAttribute `json:"validate"`
	Value        string              `json:"value"`
	Version      []string            `json:"version"`
}

// ValidateAttribute 对应 CsapDocParameter.ValidateAttribute。
type ValidateAttribute struct {
	Code    string `json:"code"`
	Descr   string `json:"descr"`
	Level   int    `json:"level"`
	Message string `json:"message"`
	Pattern string `json:"pattern"`
	Type    string `json:"type"`
}

// ParentResponse 对应 Java CsapDocParentResponse(/csap/apidoc/parent)。
type ParentResponse struct {
	ApiInfo            *ApiInfo                    `json:"apiInfo"`
	ApiList            []ParentModel               `json:"apiList"`
	EnumList           map[string][]map[string]any `json:"enumList"`
	GlobalApiList      []*Controller               `json:"globalApiList"`
	GlobalRequestParam []*Model                    `json:"globalRequestParam"`
	Groups             []string                    `json:"groups"`
	Resources          []Resource                  `json:"resources"`
	Versions           []string                    `json:"versions"`
}

// ParentModel 对应 CsapDocParentResponseModel:树形菜单的一个分组。
type ParentModel struct {
	Children []MethodModel `json:"children"`
	Key      string        `json:"key"`
	Title    string        `json:"title"`
}

// MethodModel 对应 CsapDocMethodModel:树形菜单的一个接口。
type MethodModel struct {
	IsLeaf bool   `json:"isLeaf"`
	Key    string `json:"key"`
	Method string `json:"method"`
	Path   string `json:"path"`
	Title  string `json:"title"`
	Type   string `json:"type"`
}

// MethodResponse 对应 Java CsapDocMethodResponse(/csap/apidoc/method)。
type MethodResponse struct {
	Description string          `json:"description"`
	Headers     []HeaderModel   `json:"headers"`
	Method      string          `json:"method"`
	Methods     []string        `json:"methods"`
	ParamType   string          `json:"paramType"`
	Patch       string          `json:"patch"`
	Request     []*RequestModel `json:"request"`
	Response    []*RequestModel `json:"response"`
	Tags        []string        `json:"tags"`
	Title       string          `json:"title"`
}

// HeaderModel 对应 MethodHeadersResponseModel。
type HeaderModel struct {
	Description string `json:"description"`
	Example     string `json:"example"`
	Key         string `json:"key"`
	Required    bool   `json:"required"`
	Value       string `json:"value"`
}

// RequestModel 对应 MethodRequestModel(方法详情里扁平化的字段树)。
type RequestModel struct {
	Children     []*RequestModel     `json:"children"`
	DataType     string              `json:"dataType"`
	DefaultValue string              `json:"defaultValue"`
	Description  string              `json:"description"`
	Example      string              `json:"example"`
	ExtendDescr  []map[string]string `json:"extendDescr"`
	Key          string              `json:"key"`
	ModelType    *string             `json:"modelType"`
	Name         string              `json:"name"`
	ParamType    string              `json:"paramType"`
	Required     bool                `json:"required"`
	Validate     []ValidateAttribute `json:"validate"`
	Value        string              `json:"value"`
}
