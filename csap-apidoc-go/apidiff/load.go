package apidiff

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// Load 读取一份文档:`http(s)://` 开头按 URL 请求(路径为空时补 /csap/apidoc),否则当作本地文件
// (/csap/apidoc 的 JSON 或快照文件)。headers 是请求 URL 时附带的请求头,如 "Authorization: Bearer xxx"。
func Load(ctx context.Context, src string, headers []string) (*Doc, error) {
	var b []byte
	var err error
	name := src
	if strings.HasPrefix(src, "http://") || strings.HasPrefix(src, "https://") {
		b, name, err = fetch(ctx, src, headers)
	} else {
		b, err = os.ReadFile(src)
	}
	if err != nil {
		return nil, err
	}
	return Parse(b, name)
}

func fetch(ctx context.Context, raw string, headers []string) ([]byte, string, error) {
	u, err := url.Parse(raw)
	if err != nil {
		return nil, raw, err
	}
	if u.Path == "" || u.Path == "/" {
		u.Path = "/csap/apidoc"
	}
	// 报告里只显示不带查询串和账号的地址,避免把令牌写进日志
	shown := u.Scheme + "://" + u.Host + u.Path
	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, shown, err
	}
	for _, h := range headers {
		k, v, ok := strings.Cut(h, ":")
		if !ok {
			return nil, shown, fmt.Errorf("请求头 %q 格式应为 Name: Value", strings.SplitN(h, ":", 2)[0])
		}
		req.Header.Set(strings.TrimSpace(k), strings.TrimSpace(v))
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, shown, fmt.Errorf("请求 %s 失败: %w", shown, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, shown, fmt.Errorf("请求 %s 返回 HTTP %d", shown, resp.StatusCode)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, 256<<20))
	return b, shown, err
}
