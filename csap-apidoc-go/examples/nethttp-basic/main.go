// 示例:标准库 net/http(Go 1.22+ ServeMux)接入 csap-apidoc-go。
//
//	go run ./examples/nethttp-basic
//	打开 http://localhost:8081/csap-api.html(文档)、http://localhost:8081/devtools-ui(后台管理)
package main

import (
	"context"
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	nethttpadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/nethttp"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

// Result 是统一外层包装。
type Result struct {
	Code    string `json:"code" doc:"返回编码" doc_example:"0" doc_force:"true"`
	Message string `json:"message" doc:"返回的描述" doc_example:"成功"`
	Data    any    `json:"data" doc:"返回的数据"`
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}

type User struct {
	ID        int64     `json:"id" doc:"用户ID" doc_example:"1001"`
	Name      string    `json:"name" doc:"姓名" doc_example:"张三"`
	Phone     string    `json:"phone" doc:"手机号"`
	CreatedAt time.Time `json:"createdAt" doc:"创建时间"`
}

type GetUserReq struct {
	ID int64 `in:"path" json:"id" doc:"用户ID"`
}

type CreateUserReq struct {
	Name  string `json:"name" doc:"姓名"`
	Phone string `json:"phone" doc:"手机号"`
}

type UserHandler struct{}

func (UserHandler) Get(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(r.PathValue("id"), 10, 64)
	// 返回里有 phone、createdAt,规则只放行 id、name,实际输出会被裁剪
	writeJSON(w, Result{Code: "0", Message: "成功", Data: User{ID: id, Name: "张三", Phone: "13800000000", CreatedAt: time.Now()}})
}

func (UserHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req CreateUserReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, Result{Code: "400", Message: err.Error()})
		return
	}
	writeJSON(w, Result{Code: "0", Message: "成功", Data: User{ID: 1002, Name: req.Name, Phone: req.Phone}})
}

func main() {
	rulesDir := flag.String("rules", "examples/nethttp-basic/rules", "规则文件目录")
	addr := flag.String("addr", ":8081", "监听地址")
	withDevtools := flag.Bool("devtools", true, "开启后台管理 /devtools-ui(生产环境请关闭或加鉴权)")
	flag.Parse()

	doc := apidoc.New(apidoc.Options{
		Info:       &model.ApiInfo{Title: "net/http 示例", Version: "1.0", Contact: &model.Contact{}},
		Strategies: []strategy.Strategy{yaml.Dir(*rulesDir)},
		Wrapper:    &apidoc.Wrapper{Type: Result{}},
		// 在线调试时预填的请求头和认证方式(对应 Java @DocGlobalHeader / @DocAuth)
		HeaderHints: []model.GlobalHeaderHint{{Name: "X-Request-Id", Description: "链路 ID", Example: "demo"}},
		Auth:        apidoc.NewAuthHint("bearer", "登录后获得的令牌", "", ""),
	})
	doc.Group(apidoc.GroupInfo{Name: "example.UserHandler", Title: "用户管理", Path: "/api/v1/users"})
	go func() { _ = doc.Watch(context.Background()) }()

	mux := http.NewServeMux()
	m := nethttpadapter.Mount(mux, doc)
	m.Use(
		nethttpadapter.ResponseFilter(doc),
		nethttpadapter.RequestValidator(validate.New(doc, validate.Options{Mode: validate.Enforce})),
	)
	h := UserHandler{}
	api := m.Group("/api/v1/users")
	api.GET("/{id}", apidoc.Op("example.UserHandler", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{}), h.Get)
	api.POST("", apidoc.Op("example.UserHandler", "create").Summary("新增用户").Body(CreateUserReq{}).Resp(User{}), h.Create)

	if *withDevtools {
		auth, realm := devtools.Auth(devtools.AllowLocal), ""
		if pwd := os.Getenv("CSAP_DEVTOOLS_PASSWORD"); pwd != "" {
			auth, realm = devtools.BasicAuth("admin", pwd), "csap-apidoc devtools"
		}
		nethttpadapter.MountDevtools(mux, devtools.MustNew(doc, devtools.Options{Auth: auth, Realm: realm}))
		log.Printf("后台管理: http://%s/devtools-ui", hostPort(*addr))
	}

	log.Printf("文档: http://%s/csap-api.html", hostPort(*addr))
	srv := &http.Server{Addr: *addr, Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	log.Fatal(srv.ListenAndServe())
}

// hostPort 把 ":8080" 补成 "localhost:8080",用于打印访问地址。
func hostPort(addr string) string {
	if strings.HasPrefix(addr, ":") {
		return "localhost" + addr
	}
	return addr
}
