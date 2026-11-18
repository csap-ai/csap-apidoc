package gin

import (
	"bytes"
	"log/slog"
	"mime"
	"net/http"

	"github.com/gin-gonic/gin"

	apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/filter"
	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

// ResponseFilter 返回字段过滤中间件:接口配置了返回规则时,按白名单裁剪 JSON 返回,
// 代码里返回了、但规则没放行的字段不输出。没有规则的接口不受影响,也不缓冲。
//
//	r.Use(ginadapter.ResponseFilter(doc))
//
// 默认只裁剪 2xx 响应,4xx/5xx 的错误体原样输出;可用 apidoc.Options.ShouldFilter 自定义,
// 用 apidoc.Options.FilterEndpoints 按接口关闭。规则热更新后,下一个请求即按新规则过滤。
func ResponseFilter(doc *apidoc.Doc) gin.HandlerFunc {
	return func(c *gin.Context) {
		ep := doc.Snapshot().Endpoint(c.Request.Method, c.FullPath())
		if ep == nil || ep.ResponseMask == nil {
			c.Next()
			return
		}
		bw := &bufferWriter{ResponseWriter: c.Writer, status: http.StatusOK}
		c.Writer = bw
		c.Next()
		c.Writer = bw.ResponseWriter
		mask := ep.ResponseMask
		if !doc.ShouldFilter(c.Request, bw.status) {
			mask = nil
		}
		bw.flushTo(mask)
	}
}

// RequestValidator 请求参数校验中间件,模式(关闭 / 观察 / 拦截)见 validate.Options。
//
//	v := validate.New(doc, validate.Options{Mode: validate.Observe})
//	r.Use(ginadapter.RequestValidator(v))
func RequestValidator(v *validate.Validator) gin.HandlerFunc {
	return func(c *gin.Context) {
		if !v.Handle(c.Writer, c.Request, c.FullPath(), c.Param) {
			c.Abort()
			return
		}
		c.Next()
	}
}

// bufferWriter 缓冲业务 handler 的输出,结束后统一裁剪再写出。
type bufferWriter struct {
	gin.ResponseWriter
	buf         bytes.Buffer
	status      int
	wroteHeader bool
}

func (w *bufferWriter) WriteHeader(code int) {
	if code > 0 && !w.wroteHeader {
		w.status = code
	}
}

func (w *bufferWriter) WriteHeaderNow() { w.wroteHeader = true }

func (w *bufferWriter) Write(b []byte) (int, error) {
	w.wroteHeader = true
	return w.buf.Write(b)
}

func (w *bufferWriter) WriteString(s string) (int, error) {
	w.wroteHeader = true
	return w.buf.WriteString(s)
}

func (w *bufferWriter) Status() int   { return w.status }
func (w *bufferWriter) Size() int     { return w.buf.Len() }
func (w *bufferWriter) Written() bool { return w.wroteHeader }

// Flush 在缓冲模式下不做事:流式输出(SSE 等)的接口不应该配置返回过滤。
func (w *bufferWriter) Flush() {}

// flushTo 写出缓冲的响应,mask 为 nil 时原样写出。
func (w *bufferWriter) flushTo(mask *filter.Node) {
	out := w.buf.Bytes()
	ct, _, _ := mime.ParseMediaType(w.Header().Get("Content-Type"))
	if mask != nil && (ct == "application/json" || (len(ct) > 5 && ct[len(ct)-5:] == "+json")) {
		if pruned, err := filter.Prune(out, mask); err == nil {
			out = pruned
		} else {
			slog.Warn("apidoc: response is not valid JSON, not filtered", "err", err)
		}
	}
	if w.Header().Get("Content-Length") != "" {
		w.Header().Del("Content-Length")
	}
	if !w.wroteHeader && len(out) == 0 {
		// handler 什么都没写:只透传状态码
		w.ResponseWriter.WriteHeader(w.status)
		w.ResponseWriter.WriteHeaderNow()
		return
	}
	w.ResponseWriter.WriteHeader(w.status)
	_, _ = w.ResponseWriter.Write(out)
}
