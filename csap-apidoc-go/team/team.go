// Package team 把服务的地址注册到团队版文档中心(协议见仓库 docs/design/team-integration.md 第 1 节)。
//
// 默认不启用:Options.URL 为空时 Register 什么都不做,不起 goroutine、不发请求。
// 注册、心跳、注销都在后台进行,团队版不可达或返回错误不会影响业务,也不会 panic 到调用方。
//
//	reg := team.Register(ctx, team.Options{
//	    URL: "http://team.example.com:8090", Token: os.Getenv("CSAP_TEAM_TOKEN"),
//	    Service: "order-service", BaseURL: "http://10.0.0.5:8080",
//	})
//	defer reg.Close() // 发送注销并停止心跳
package team

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/version"
)

const (
	// DefaultService 是 Options.Service 为空时使用的服务名,与 strategy/sql.DefaultService 一致。
	DefaultService = "default"
	// DefaultDocPath 是 Options.DocPath 为空时的文档接口路径。
	DefaultDocPath = "/csap/apidoc"

	language          = "go"
	registerPath      = "/api/open/v1/services/register"
	deregisterPath    = "/api/open/v1/services/deregister"
	requestTimeout    = 3 * time.Second
	deregisterTimeout = 2 * time.Second
	defaultHeartbeat  = 30
	minHeartbeat      = 10
	maxHeartbeat      = 300
	authRetrySeconds  = 300
	warnEvery         = 10
)

// Options 是注册参数。除 URL 为空表示"不启用"外,其余字段的规则见各字段说明。
type Options struct {
	// URL 是团队版地址,例如 http://team.example.com:8090。为空表示不启用。
	URL string
	// Token 是团队版控制台创建的 REGISTER 令牌,建议从环境变量读取。不会出现在日志和错误里。
	Token string
	// Service 是服务名。为空时用 DefaultService("default");
	// 使用数据库模式时请传 sqlStrategy.Service(),让两处一致。
	Service string
	// BaseURL 是团队版能访问到的服务地址,不带末尾 /,包含路径前缀。必填:
	// Go 无法可靠得知监听地址,为空时不注册并 warn 一次。
	BaseURL string
	// InstanceID 同一服务内唯一。为空时取 BaseURL 的 host:port。
	InstanceID string
	// DocPath 是文档接口路径,默认 /csap/apidoc。
	DocPath string
	// HTTPClient 可选;每次请求另有 3 秒(注销 2 秒)超时。
	HTTPClient *http.Client
	// Logger 可选,默认 slog.Default()。
	Logger *slog.Logger

	timing *timing // 仅测试用:缩短时间单位
}

// timing 让测试把"秒"换成毫秒(避免等 10 秒起步的心跳),并单独指定请求超时。
type timing struct{ second, timeout time.Duration }

// Registration 是注册器,Close 发送注销并停止心跳。零值和 nil 都可以安全地 Close。
type Registration struct {
	reg   registerRequest
	url   string
	token string
	log   *slog.Logger
	cli   *http.Client
	tm    timing

	cancel context.CancelFunc
	done   chan struct{}

	mu         sync.Mutex
	closed     bool
	registered bool // 至少成功注册过一次,Close 才发注销
}

// registerRequest 是注册/心跳请求体;字段顺序与契约一致。
type registerRequest struct {
	Service          string `json:"service"`
	InstanceID       string `json:"instanceId"`
	BaseURL          string `json:"baseUrl"`
	DocPath          string `json:"docPath"`
	Language         string `json:"language"`
	FrameworkVersion string `json:"frameworkVersion"`
}

type deregisterRequest struct {
	Service    string `json:"service"`
	InstanceID string `json:"instanceId"`
}

// Register 启动后台注册和心跳。它不会阻塞、不会 panic;参数不全或未启用时返回一个什么都不做的注册器。
// ctx 取消会停止心跳(不发注销);要发注销请调用 Close。
func Register(ctx context.Context, opts Options) (r *Registration) {
	r = &Registration{}
	defer func() {
		if e := recover(); e != nil { // 绝不 panic 到调用方
			r = &Registration{}
		}
	}()
	if ctx == nil {
		ctx = context.Background()
	}
	log := opts.Logger
	if log == nil {
		log = slog.Default()
	}
	teamURL := strings.TrimRight(strings.TrimSpace(opts.URL), "/")
	if teamURL == "" {
		return r // 未启用:静默
	}
	token := strings.TrimSpace(opts.Token)
	baseURL := strings.TrimRight(strings.TrimSpace(opts.BaseURL), "/")
	if baseURL == "" {
		log.Warn("csap-apidoc 团队版注册已跳过:未设置 BaseURL(Go 无法推断监听地址,请传入团队版能访问到的服务地址)")
		return r
	}
	u, err := url.Parse(baseURL)
	if err != nil || u.Host == "" || (u.Scheme != "http" && u.Scheme != "https") {
		log.Warn("csap-apidoc 团队版注册已跳过:BaseURL 不是有效的 http(s) 地址", "baseUrl", redactURL(baseURL))
		return r
	}
	baseURL = u.String()
	instanceID := strings.TrimSpace(opts.InstanceID)
	if instanceID == "" {
		instanceID = u.Host
	}
	service := strings.TrimSpace(opts.Service)
	if service == "" {
		service = DefaultService
	}
	docPath := strings.TrimSpace(opts.DocPath)
	if docPath == "" {
		docPath = DefaultDocPath
	}
	if !strings.HasPrefix(docPath, "/") {
		docPath = "/" + docPath
	}
	cli := opts.HTTPClient
	if cli == nil {
		cli = &http.Client{}
	}
	tm := timing{second: time.Second, timeout: requestTimeout}
	if opts.timing != nil {
		tm = *opts.timing
	}
	runCtx, cancel := context.WithCancel(ctx)
	r = &Registration{
		reg: registerRequest{
			Service: service, InstanceID: instanceID, BaseURL: baseURL, DocPath: docPath,
			Language: language, FrameworkVersion: version.Version,
		},
		url: teamURL, token: token, log: log, cli: cli, tm: tm,
		cancel: cancel, done: make(chan struct{}),
	}
	go r.loop(runCtx)
	return r
}

// Close 停止心跳,并在注册成功过的前提下发送一次注销(2 秒超时,失败忽略)。可重复调用。
func (r *Registration) Close() {
	if r == nil || r.cancel == nil {
		return
	}
	defer func() { _ = recover() }()
	r.mu.Lock()
	if r.closed {
		r.mu.Unlock()
		return
	}
	r.closed = true
	r.mu.Unlock()

	r.cancel()
	<-r.done // 进行中的请求已被取消,很快返回

	r.mu.Lock()
	registered := r.registered
	r.mu.Unlock()
	if !registered {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), deregisterTimeout)
	defer cancel()
	if _, err := r.post(ctx, deregisterPath, deregisterRequest{Service: r.reg.Service, InstanceID: r.reg.InstanceID}); err != nil {
		r.log.Debug("csap-apidoc 团队版注销失败(已忽略)", "error", r.scrub(err.Error()))
		return
	}
	r.log.Info("csap-apidoc 已向团队版注销", "service", r.reg.Service, "instanceId", r.reg.InstanceID)
}

func (r *Registration) loop(ctx context.Context) {
	defer close(r.done)
	defer func() {
		if e := recover(); e != nil {
			r.log.Error("csap-apidoc 团队版注册协程异常退出", "panic", r.scrub(fmt.Sprint(e)))
		}
	}()

	interval := defaultHeartbeat
	failures := 0 // 连续失败次数
	authWarned := false
	everFailed := false
	first := true
	for {
		heartbeat, status, err := r.registerOnce(ctx)
		if ctx.Err() != nil {
			return
		}
		switch {
		case err == nil:
			interval = heartbeat
			r.mu.Lock()
			r.registered = true
			r.mu.Unlock()
			if everFailed {
				r.log.Info("csap-apidoc 与团队版的连接已恢复", "service", r.reg.Service, "instanceId", r.reg.InstanceID)
			} else if first {
				r.log.Info("csap-apidoc 已向团队版注册", "service", r.reg.Service, "instanceId", r.reg.InstanceID, "heartbeatSeconds", interval)
			}
			failures, authWarned, everFailed = 0, false, false
		case status == http.StatusUnauthorized || status == http.StatusForbidden:
			everFailed = true
			interval = authRetrySeconds
			if !authWarned {
				authWarned = true
				r.log.Warn("csap-apidoc 向团队版注册失败:令牌无效或权限不足,之后每 300 秒重试一次", "status", status)
			}
		default:
			everFailed = true
			failures++
			if failures == 1 || failures%warnEvery == 1 {
				r.log.Warn("csap-apidoc 向团队版注册/心跳失败", "consecutiveFailures", failures, "error", r.scrub(err.Error()))
			}
		}
		first = false

		t := time.NewTimer(time.Duration(interval) * r.tm.second)
		select {
		case <-ctx.Done():
			t.Stop()
			return
		case <-t.C:
		}
	}
}

// registerOnce 发一次注册/心跳请求,成功时返回响应里的心跳秒数(已限制范围)。
func (r *Registration) registerOnce(ctx context.Context) (heartbeat, status int, err error) {
	ctx, cancel := context.WithTimeout(ctx, r.tm.timeout)
	defer cancel()
	body, err := r.post(ctx, registerPath, r.reg)
	if err != nil {
		if se, ok := err.(*statusError); ok {
			return 0, se.status, err
		}
		return 0, 0, err
	}
	var resp struct {
		HeartbeatSeconds json.Number `json:"heartbeatSeconds"`
	}
	if json.Unmarshal(body, &resp) != nil {
		return defaultHeartbeat, 0, nil
	}
	return clampHeartbeat(resp.HeartbeatSeconds), 0, nil
}

// clampHeartbeat:无法解析、不是整数或不是正数 → 30;超出 10–300 的取最近的边界。
func clampHeartbeat(n json.Number) int {
	f, err := n.Float64()
	if err != nil || f <= 0 || f != math.Trunc(f) || math.IsInf(f, 0) {
		return defaultHeartbeat
	}
	switch {
	case f < minHeartbeat:
		return minHeartbeat
	case f > maxHeartbeat:
		return maxHeartbeat
	}
	return int(f)
}

type statusError struct{ status int }

func (e *statusError) Error() string { return fmt.Sprintf("团队版返回 HTTP %d", e.status) }

func (r *Registration) post(ctx context.Context, path string, payload any) ([]byte, error) {
	data, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, r.url+path, bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("构造请求失败: %s", r.scrub(err.Error()))
	}
	req.Header.Set("Content-Type", "application/json")
	if r.token != "" {
		req.Header.Set("Authorization", "Bearer "+r.token)
	}
	resp, err := r.cli.Do(req)
	if err != nil {
		return nil, fmt.Errorf("%s", r.scrub(err.Error()))
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return nil, &statusError{status: resp.StatusCode}
	}
	return b, nil
}

// scrub 去掉文本里的令牌和 URL 中的用户信息,日志和错误一律先过它。
func (r *Registration) scrub(s string) string {
	if r.token != "" {
		s = strings.ReplaceAll(s, r.token, "***")
	}
	return s
}

func redactURL(raw string) string {
	u, err := url.Parse(raw)
	if err != nil {
		return "(无效地址)"
	}
	u.User = nil
	return u.String()
}
