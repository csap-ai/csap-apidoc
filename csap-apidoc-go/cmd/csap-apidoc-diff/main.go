// csap-apidoc-diff 比较两份 /csap/apidoc 文档,检测破坏性变更;有破坏性变更时退出码为 1,适合放进 CI。
//
//	csap-apidoc-diff snapshot -o baseline.json http://localhost:8080/csap/apidoc   # 存基线
//	csap-apidoc-diff baseline.json http://localhost:8080/csap/apidoc               # 对比(diff 可省略)
//
// 退出码:0 通过;1 有破坏性变更(或按 -fail-on 判定失败);2 参数或读取错误。
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/apidiff"
)

const usage = `用法:
  csap-apidoc-diff [diff] [选项] <旧文档> <新文档>
  csap-apidoc-diff snapshot [选项] <文档>

文档可以是 /csap/apidoc 的 URL(http/https,路径为空时补 /csap/apidoc)、保存下来的 JSON 文件,或 snapshot 生成的快照文件。
Java 和 Go 服务输出同一协议,都能比较。

diff 选项:
  -format text|json|markdown   报告格式,默认 text
  -o 文件                      报告写到文件,默认标准输出
  -fail-on breaking|warning|never  什么级别让退出码非 0,默认 breaking
  -min-level info|warning|breaking 报告里列出的最低级别,默认 info(汇总数字不受影响)
  -ignore 文件                 忽略清单(每行 "接口 [变更类型 [字段]]",支持 *),可重复
  -H "Name: Value"             请求 URL 时附带的请求头,可重复(如 Authorization)

snapshot 选项:
  -o 文件                      快照写到文件,默认标准输出
  -H "Name: Value"             同上
`

type multi []string

func (m *multi) String() string     { return strings.Join(*m, ",") }
func (m *multi) Set(v string) error { *m = append(*m, v); return nil }

func main() { os.Exit(run(os.Args[1:], os.Stdout, os.Stderr)) }

func run(args []string, stdout, stderr io.Writer) int {
	if len(args) > 0 {
		switch args[0] {
		case "-h", "-help", "--help", "help":
			fmt.Fprint(stdout, usage)
			return 0
		case "snapshot":
			return runSnapshot(args[1:], stdout, stderr)
		case "diff":
			args = args[1:]
		}
	}
	return runDiff(args, stdout, stderr)
}

func fail(stderr io.Writer, format string, a ...any) int {
	fmt.Fprintf(stderr, "csap-apidoc-diff: "+format+"\n", a...)
	return 2
}

func runSnapshot(args []string, stdout, stderr io.Writer) int {
	fs := flag.NewFlagSet("snapshot", flag.ContinueOnError)
	fs.SetOutput(stderr)
	out := fs.String("o", "", "快照写到文件")
	var headers multi
	fs.Var(&headers, "H", "请求头")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	if fs.NArg() != 1 {
		fmt.Fprint(stderr, usage)
		return 2
	}
	doc, err := apidiff.Load(context.Background(), fs.Arg(0), headers)
	if err != nil {
		return fail(stderr, "%v", err)
	}
	b, err := doc.Snapshot(time.Now())
	if err != nil {
		return fail(stderr, "%v", err)
	}
	if *out == "" {
		_, _ = stdout.Write(b)
		return 0
	}
	if err := os.WriteFile(*out, b, 0o644); err != nil {
		return fail(stderr, "%v", err)
	}
	fmt.Fprintf(stderr, "已保存快照 %s(%d 个接口)\n", *out, len(doc.Endpoints))
	return 0
}

func runDiff(args []string, stdout, stderr io.Writer) int {
	fs := flag.NewFlagSet("diff", flag.ContinueOnError)
	fs.SetOutput(stderr)
	format := fs.String("format", "text", "text / json / markdown")
	out := fs.String("o", "", "报告写到文件")
	failOn := fs.String("fail-on", "breaking", "breaking / warning / never")
	minLevel := fs.String("min-level", "info", "info / warning / breaking")
	var ignores, headers multi
	fs.Var(&ignores, "ignore", "忽略清单文件")
	fs.Var(&headers, "H", "请求头")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	if fs.NArg() != 2 {
		fmt.Fprint(stderr, usage)
		return 2
	}
	min, err := apidiff.ParseLevel(*minLevel)
	if err != nil {
		return fail(stderr, "-min-level: %v", err)
	}
	var failLevel apidiff.Level
	if *failOn != "never" {
		if failLevel, err = apidiff.ParseLevel(*failOn); err != nil || failLevel == apidiff.Info {
			return fail(stderr, "-fail-on 可选 breaking / warning / never")
		}
	}
	var rules []apidiff.IgnoreRule
	for _, p := range ignores {
		f, err := os.Open(p)
		if err != nil {
			return fail(stderr, "%v", err)
		}
		rs, err := apidiff.ParseIgnore(f)
		f.Close()
		if err != nil {
			return fail(stderr, "%s: %v", p, err)
		}
		rules = append(rules, rs...)
	}
	ctx := context.Background()
	oldDoc, err := apidiff.Load(ctx, fs.Arg(0), headers)
	if err != nil {
		return fail(stderr, "旧文档: %v", err)
	}
	newDoc, err := apidiff.Load(ctx, fs.Arg(1), headers)
	if err != nil {
		return fail(stderr, "新文档: %v", err)
	}
	rep := apidiff.NewReport(oldDoc, newDoc, rules)
	var text string
	switch *format {
	case "text":
		text = rep.Text(min)
	case "markdown", "md":
		text = rep.Markdown(min)
	case "json":
		b, err := rep.JSON(min)
		if err != nil {
			return fail(stderr, "%v", err)
		}
		text = string(b)
	default:
		return fail(stderr, "-format 可选 text / json / markdown")
	}
	if *out == "" {
		fmt.Fprint(stdout, text)
	} else if err := os.WriteFile(*out, []byte(text), 0o644); err != nil {
		return fail(stderr, "%v", err)
	}
	if rep.Fails(failLevel) {
		return 1
	}
	return 0
}
