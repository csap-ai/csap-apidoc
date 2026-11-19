package team

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/internal/version"
)

const secret = "tok-SECRET-123"

// fast 把 1 "秒" 缩成 1 毫秒。
var fast = &timing{second: time.Millisecond, timeout: 2 * time.Second}

type recorded struct {
	path, method, auth, ctype string
	body                      []byte
	at                        time.Time
}

type fakeServer struct {
	*httptest.Server
	mu   sync.Mutex
	reqs []recorded
}

func newFake(t *testing.T, handler func(path string, n int) (int, string)) *fakeServer {
	t.Helper()
	f := &fakeServer{}
	f.Server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		f.mu.Lock()
		f.reqs = append(f.reqs, recorded{r.URL.Path, r.Method, r.Header.Get("Authorization"), r.Header.Get("Content-Type"), b, time.Now()})
		n := len(f.reqs)
		f.mu.Unlock()
		code, body := handler(r.URL.Path, n)
		w.WriteHeader(code)
		_, _ = io.WriteString(w, body)
	}))
	t.Cleanup(f.Close)
	return f
}

func (f *fakeServer) snapshot() []recorded {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]recorded(nil), f.reqs...)
}

func (f *fakeServer) waitFor(t *testing.T, n int) []recorded {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		if s := f.snapshot(); len(s) >= n {
			return s
		}
		time.Sleep(2 * time.Millisecond)
	}
	t.Fatalf("等待 %d 个请求超时,实际 %d", n, len(f.snapshot()))
	return nil
}

// waitRegistered 等到注册器确认注册成功(服务端记录请求时响应可能还没被客户端处理)。
func waitRegistered(t *testing.T, r *Registration) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		r.mu.Lock()
		ok := r.registered
		r.mu.Unlock()
		if ok {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatal("等待注册成功超时")
}

type logBuf struct {
	mu sync.Mutex
	b  bytes.Buffer
}

func (l *logBuf) Write(p []byte) (int, error) { l.mu.Lock(); defer l.mu.Unlock(); return l.b.Write(p) }
func (l *logBuf) String() string              { l.mu.Lock(); defer l.mu.Unlock(); return l.b.String() }
func (l *logBuf) count(sub string) int        { return strings.Count(l.String(), sub) }
func newLogger() (*slog.Logger, *logBuf) {
	lb := &logBuf{}
	return slog.New(slog.NewTextHandler(lb, &slog.HandlerOptions{Level: slog.LevelDebug})), lb
}

func ok(n int) string { return fmt.Sprintf(`{"heartbeatSeconds": %d}`, n) }

func TestRegisterRequestAndDeregister(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 200, ok(300) })
	log, lb := newLogger()
	r := Register(context.Background(), Options{
		URL: srv.URL + "/", Token: secret, Service: "order-service",
		BaseURL: "http://10.0.0.5:8080/", Logger: log, timing: fast,
	})
	reqs := srv.waitFor(t, 1)
	waitRegistered(t, r)
	q := reqs[0]
	if q.method != "POST" || q.path != "/api/open/v1/services/register" {
		t.Fatalf("方法/路径不对: %s %s", q.method, q.path)
	}
	if q.auth != "Bearer "+secret || q.ctype != "application/json" {
		t.Fatalf("header 不对: %q %q", q.auth, q.ctype)
	}
	var got map[string]string
	if err := json.Unmarshal(q.body, &got); err != nil {
		t.Fatal(err)
	}
	want := map[string]string{
		"service": "order-service", "instanceId": "10.0.0.5:8080", "baseUrl": "http://10.0.0.5:8080",
		"docPath": "/csap/apidoc", "language": "go", "frameworkVersion": version.Version,
	}
	if len(got) != len(want) {
		t.Fatalf("字段数不对: %v", got)
	}
	for k, v := range want {
		if got[k] != v {
			t.Errorf("%s = %q, want %q", k, got[k], v)
		}
	}

	r.Close()
	r.Close() // 幂等
	reqs = srv.snapshot()
	last := reqs[len(reqs)-1]
	if last.path != "/api/open/v1/services/deregister" || last.method != "POST" || last.auth != "Bearer "+secret {
		t.Fatalf("注销请求不对: %+v", last)
	}
	if string(last.body) != `{"service":"order-service","instanceId":"10.0.0.5:8080"}` {
		t.Fatalf("注销 body: %s", last.body)
	}
	n := 0
	for _, x := range reqs {
		if strings.HasSuffix(x.path, "deregister") {
			n++
		}
	}
	if n != 1 {
		t.Fatalf("注销应只发一次,实际 %d", n)
	}
	if strings.Contains(lb.String(), secret) {
		t.Fatalf("日志里出现了令牌: %s", lb.String())
	}
}

func TestDefaultsAndOverrides(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 200, ok(300) })
	r := Register(context.Background(), Options{
		URL: srv.URL, BaseURL: "https://api.example.com/ctx", InstanceID: "pod-1", DocPath: "docs/x", timing: fast,
	})
	defer r.Close()
	q := srv.waitFor(t, 1)[0]
	if q.auth != "" {
		t.Errorf("无令牌时不应带 Authorization: %q", q.auth)
	}
	var got registerRequest
	_ = json.Unmarshal(q.body, &got)
	if got.Service != "default" || got.InstanceID != "pod-1" || got.DocPath != "/docs/x" || got.BaseURL != "https://api.example.com/ctx" {
		t.Fatalf("%+v", got)
	}
	// InstanceID 默认取 host:port(无端口就是 host)
	r2 := Register(context.Background(), Options{URL: srv.URL, BaseURL: "https://api.example.com/ctx", timing: fast})
	defer r2.Close()
	if r2.reg.InstanceID != "api.example.com" {
		t.Fatalf("InstanceID = %q", r2.reg.InstanceID)
	}
}

func TestHeartbeatIntervalFromResponse(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 200, ok(20) })
	r := Register(context.Background(), Options{URL: srv.URL, BaseURL: "http://h:1", timing: fast})
	defer r.Close()
	reqs := srv.waitFor(t, 4)
	for i := 1; i < 4; i++ {
		d := reqs[i].at.Sub(reqs[i-1].at)
		if d < 15*time.Millisecond || d > time.Second { // 20ms,上限放宽以免负载下抖动
			t.Fatalf("第 %d 次心跳间隔 %v,期望约 20ms", i, d)
		}
		if !bytes.Equal(reqs[i].body, reqs[0].body) {
			t.Fatalf("心跳必须重复发送同一个请求体")
		}
	}
}

func TestClampHeartbeat(t *testing.T) {
	cases := map[string]int{"30": 30, "10": 10, "300": 300, "5": 10, "9999": 300, "0": 30, "-3": 30, "abc": 30, "": 30, "12.5": 30, "30.5": 30, "900": 300, "30.0": 30}
	for in, want := range cases {
		if got := clampHeartbeat(json.Number(in)); got != want {
			t.Errorf("clamp(%q) = %d, want %d", in, got, want)
		}
	}
	// 缺字段或响应体不是 JSON → 默认 30
	for _, body := range []string{`{}`, `not json`, ``, `{"heartbeatSeconds":"x"}`} {
		srv := newFake(t, func(string, int) (int, string) { return 200, body })
		r := Register(context.Background(), Options{URL: srv.URL, BaseURL: "http://h:1", timing: fast})
		srv.waitFor(t, 2) // 30ms 后第二次
		r.Close()
	}
}

func TestAuthFailureWarnsOnceAndSlowsDown(t *testing.T) {
	srv := newFake(t, func(p string, n int) (int, string) {
		if strings.HasSuffix(p, "register") {
			return 401, `{"error":"bad token ` + secret + `"}`
		}
		return 200, ok(30)
	})
	log, lb := newLogger()
	r := Register(context.Background(), Options{URL: srv.URL, Token: secret, BaseURL: "http://h:1", Logger: log, timing: fast})
	srv.waitFor(t, 1)
	time.Sleep(150 * time.Millisecond) // 若仍按 30ms 周期会有 5 次左右;300ms 周期只有 1 次
	if n := len(srv.snapshot()); n != 1 {
		t.Fatalf("401 后应按 300 周期重试,150ms 内应只有 1 次请求,实际 %d", n)
	}
	srv.waitFor(t, 2)
	r.Close()
	if c := lb.count("令牌无效或权限不足"); c != 1 {
		t.Fatalf("应只 warn 一次,实际 %d:\n%s", c, lb)
	}
	if strings.Contains(lb.String(), secret) {
		t.Fatal("日志出现令牌")
	}
	// 从未注册成功:Close 不发注销
	for _, q := range srv.snapshot() {
		if strings.HasSuffix(q.path, "deregister") {
			t.Fatal("未注册成功不应发注销")
		}
	}
}

func TestForbiddenAlsoSlowsDown(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 403, "" })
	r := Register(context.Background(), Options{URL: srv.URL, BaseURL: "http://h:1", timing: fast})
	defer r.Close()
	srv.waitFor(t, 1)
	time.Sleep(100 * time.Millisecond)
	if n := len(srv.snapshot()); n != 1 {
		t.Fatalf("实际 %d", n)
	}
}

func TestFailureLogThrottleAndRecovery(t *testing.T) {
	var mu sync.Mutex
	failing := true
	failed := 0 // 服务端返回 500 的次数 = 连续失败次数
	srv := newFake(t, func(p string, n int) (int, string) {
		mu.Lock()
		defer mu.Unlock()
		if failing && strings.HasSuffix(p, "register") {
			failed++
			return 500, "boom"
		}
		return 200, ok(10)
	})
	log, lb := newLogger()
	r := Register(context.Background(), Options{URL: srv.URL, Token: secret, BaseURL: "http://h:1", Logger: log, timing: fast})
	// 默认周期 30ms(尚无成功响应);等到至少 22 次失败
	srv.waitFor(t, 22)
	mu.Lock()
	failing = false
	mu.Unlock()
	deadline := time.Now().Add(3 * time.Second)
	for lb.count("已恢复") == 0 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	r.Close()
	if lb.count("已恢复") != 1 {
		t.Fatalf("恢复应 info 一次:\n%s", lb)
	}
	mu.Lock()
	nFailed := failed
	mu.Unlock()
	wantWarns := (nFailed-1)/10 + 1 // 第 1、11、21… 次失败各一条
	if warns := lb.count("level=WARN"); warns != wantWarns {
		t.Fatalf("%d 次失败应有 %d 条 warn,实际 %d:\n%s", nFailed, wantWarns, warns, lb)
	}
	if strings.Contains(lb.String(), secret) {
		t.Fatal("日志出现令牌")
	}
}

func TestUnreachableDoesNotBlockOrLeakToken(t *testing.T) {
	srv := httptest.NewServer(http.NotFoundHandler())
	addr := srv.URL
	srv.Close() // 端口立即不可达
	log, lb := newLogger()
	start := time.Now()
	r := Register(context.Background(), Options{URL: addr, Token: secret, BaseURL: "http://h:1", Logger: log, timing: fast})
	if time.Since(start) > 100*time.Millisecond {
		t.Fatal("Register 不应阻塞")
	}
	deadline := time.Now().Add(3 * time.Second)
	for lb.count("level=WARN") == 0 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	start = time.Now()
	r.Close()
	if time.Since(start) > time.Second {
		t.Fatal("不可达时 Close 不应等待")
	}
	if lb.count("level=WARN") == 0 {
		t.Fatal("应有 warn")
	}
	if strings.Contains(lb.String(), secret) {
		t.Fatal("日志出现令牌")
	}
}

func TestRequestTimeout(t *testing.T) {
	release := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { <-release }))
	defer srv.Close()
	defer close(release)
	log, lb := newLogger()
	// 心跳周期单位缩成 10ms,请求超时 30ms
	r := Register(context.Background(), Options{URL: srv.URL, BaseURL: "http://h:1", Logger: log, timing: &timing{second: 10 * time.Millisecond, timeout: 30 * time.Millisecond}})
	defer r.Close()
	deadline := time.Now().Add(3 * time.Second)
	for lb.count("level=WARN") == 0 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	if lb.count("level=WARN") == 0 {
		t.Fatalf("超时应记录 warn:\n%s", lb)
	}
}

func TestDisabledAndMissingBaseURL(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 200, ok(30) })
	log, lb := newLogger()

	r := Register(context.Background(), Options{Token: secret, BaseURL: "http://h:1", Logger: log})
	if r.cancel != nil || lb.String() != "" {
		t.Fatal("URL 为空应完全静默且不起 goroutine")
	}
	r.Close()

	r = Register(context.Background(), Options{URL: srv.URL, Logger: log})
	if r.cancel != nil || lb.count("level=WARN") != 1 {
		t.Fatalf("BaseURL 为空应 warn 一次且不注册:\n%s", lb)
	}
	r.Close()
	r = Register(context.Background(), Options{URL: srv.URL, BaseURL: "not a url", Logger: log})
	if r.cancel != nil {
		t.Fatal("非法 BaseURL 不应注册")
	}
	time.Sleep(20 * time.Millisecond)
	if n := len(srv.snapshot()); n != 0 {
		t.Fatalf("不应发请求,实际 %d", n)
	}
	var nilReg *Registration
	nilReg.Close()
}

func TestContextCancelStopsHeartbeat(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 200, ok(10) })
	ctx, cancel := context.WithCancel(context.Background())
	r := Register(ctx, Options{URL: srv.URL, BaseURL: "http://h:1", timing: fast})
	srv.waitFor(t, 2)
	cancel()
	time.Sleep(30 * time.Millisecond)
	n := len(srv.snapshot())
	time.Sleep(60 * time.Millisecond)
	if m := len(srv.snapshot()); m != n {
		t.Fatalf("取消后仍在发心跳: %d -> %d", n, m)
	}
	r.Close() // 取消后 Close 仍可调用,不会卡住
}

// TestWriteRequestFixture 把固定配置发出的请求体落到 testdata/register-request.json,
// 供与 Java 端发出的请求体做字段比对(frameworkVersion 两边版本号不同,比对时忽略)。
func TestWriteRequestFixture(t *testing.T) {
	srv := newFake(t, func(string, int) (int, string) { return 200, ok(300) })
	r := Register(context.Background(), Options{
		URL: srv.URL, Token: "t", Service: "order-service", BaseURL: "http://10.0.0.5:8080", timing: fast,
	})
	body := srv.waitFor(t, 1)[0].body
	waitRegistered(t, r)
	r.Close()
	var pretty bytes.Buffer
	if err := json.Indent(&pretty, body, "", "  "); err != nil {
		t.Fatal(err)
	}
	pretty.WriteByte('\n')
	if err := os.WriteFile("testdata/register-request.json", pretty.Bytes(), 0o644); err != nil {
		t.Fatal(err)
	}
}
