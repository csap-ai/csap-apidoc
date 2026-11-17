// 示例:gin 项目接入 csap-apidoc-go。
//
//	go run ./examples/gin-basic
//	打开 http://localhost:8080/csap-api.html
package main

import (
	"context"
	"flag"
	"log"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	ginadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/gin"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

// Result 是统一外层包装,对应 Java 的 ApiResult。
type Result struct {
	Code    string `json:"code" doc:"返回编码" doc_example:"0" doc_force:"true"`
	Message string `json:"message" doc:"返回的描述" doc_example:"成功" doc_force:"true"`
	Data    any    `json:"data" doc:"返回的数据" doc_force:"true"`
	Time    int64  `json:"time" doc:"服务端时间戳(毫秒)" doc_force:"true"`
}

func ok(c *gin.Context, data any) {
	c.JSON(http.StatusOK, Result{Code: "0", Message: "成功", Data: data, Time: time.Now().UnixMilli()})
}

// Page 是分页返回,泛型参数会显示成 Page<User>。
type Page[T any] struct {
	Total int64 `json:"total" doc:"总数"`
	Rows  []T   `json:"rows" doc:"数据"`
}

type Address struct {
	City   string `json:"city" doc:"城市" doc_example:"上海"`
	Street string `json:"street" doc:"街道"`
}

type User struct {
	ID        int64     `json:"id" doc:"用户ID" doc_example:"1001"`
	Name      string    `json:"name" doc:"姓名" doc_desc:"用户的真实姓名" doc_example:"张三"`
	Age       int       `json:"age" doc:"年龄"`
	Phone     string    `json:"phone" doc:"手机号"`
	Tags      []string  `json:"tags" doc:"标签"`
	Address   *Address  `json:"address" doc:"地址"`
	CreatedAt time.Time `json:"createdAt" doc:"创建时间"`
}

type ListUserReq struct {
	Keyword string `form:"keyword" doc:"关键字" doc_desc:"按姓名模糊匹配"`
	Page    int    `form:"page" doc:"页码" doc_default:"1"`
	Size    int    `form:"size" doc:"每页条数" doc_default:"20"`
}

type GetUserReq struct {
	ID int64 `uri:"id" doc:"用户ID"`
}

type CreateUserReq struct {
	Name    string   `json:"name" binding:"required" doc:"姓名"`
	Age     int      `json:"age" doc:"年龄"`
	Phone   string   `json:"phone" doc:"手机号"`
	Address *Address `json:"address" doc:"地址"`
}

type UserController struct{}

func (UserController) List(c *gin.Context) {
	ok(c, Page[User]{Total: 1, Rows: []User{{ID: 1001, Name: "张三", Age: 30}}})
}

func (UserController) Get(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	ok(c, User{ID: id, Name: "张三", Age: 30, Phone: "13800000000"})
}

func (UserController) Create(c *gin.Context) {
	var req CreateUserReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, Result{Code: "400", Message: err.Error()})
		return
	}
	ok(c, User{ID: 1002, Name: req.Name, Age: req.Age})
}

func main() {
	// 规则来自 YAML 文件(与 Java method-field.yaml 同格式),运行中修改文件会自动生效:
	//   - get:返回只输出 id、name(文档和实际返回都一样,phone 等字段会被去掉)
	//   - create:name 必填且长度 2-20,phone 用内置手机号正则
	rulesDir := flag.String("rules", "examples/gin-basic/rules", "规则文件目录")
	flag.Parse()
	rules := yaml.Dir(*rulesDir)

	doc := apidoc.New(apidoc.Options{
		Info:       &model.ApiInfo{Title: "示例接口", Description: "csap-apidoc-go gin 示例", Version: "1.0", License: "Apache 2.0", LicenseURL: "http://www.apache.org/licenses/LICENSE-2.0", Contact: &model.Contact{}},
		Strategies: []strategy.Strategy{rules},
		Wrapper:    &apidoc.Wrapper{Type: Result{}, Title: "公用参数", Description: "公用参数说明"},
	})
	doc.Group(apidoc.GroupInfo{Name: "example.UserController", Title: "用户管理", Description: "用户的增删改查", Path: "/api/v1/users"})

	go func() { _ = doc.Watch(context.Background()) }() // 监听规则文件变化

	r := gin.New()
	r.Use(gin.Recovery(),
		ginadapter.ResponseFilter(doc), // 按规则裁剪返回字段
		ginadapter.RequestValidator(validate.New(doc, validate.Options{Mode: validate.Enforce})), // 请求参数校验
	)
	g := ginadapter.Mount(r, doc)

	uc := UserController{}
	api := g.Group("/api/v1/users")
	api.GET("", apidoc.Op("example.UserController", "list").Summary("用户列表").Req(ListUserReq{}).Resp(Page[User]{}), uc.List)
	api.GET("/:id", apidoc.Op("example.UserController", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{}), uc.Get)
	api.POST("", apidoc.Op("example.UserController", "create").Summary("新增用户").Body(CreateUserReq{}).Resp(User{}), uc.Create)

	// 没有声明的路由也会出现在文档里(分组和名字由函数名推断)
	r.GET("/healthz", func(c *gin.Context) { c.String(http.StatusOK, "ok") })

	log.Println("文档: http://localhost:8080/csap-api.html")
	log.Fatal(r.Run(":8080"))
}
