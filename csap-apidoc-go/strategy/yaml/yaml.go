package yaml

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	yamlv3 "gopkg.in/yaml.v3"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// FileSuffix 是规则文件名的后缀,与 Java 一致:<Controller 简单名>-method-field.yaml。
const FileSuffix = "-method-field.yaml"

// Options 是 New 的参数。
type Options struct {
	// Dir 是规则文件目录(读写),如 Java 项目的 src/main/resources/csap-api-doc/method-field。
	Dir string
	// FS 不为 nil 时从这里只读加载(如 go:embed 打包的规则),Dir 表示 FS 里的子目录。
	FS fs.FS
	// Poll 是检查文件变化的间隔,默认 2 秒;小于 0 表示不监听。
	Poll time.Duration
	// History 是修改记录文件(每行一条 JSON,devtools 的修改记录和回滚用)。
	// 默认 <Dir>/.csap-apidoc-history.jsonl(以点开头,不会被当成规则文件加载);
	// "-" 表示只记在内存里,重启后丢失。只读(FS)时不记录。
	History string
}

// HistoryFile 是修改记录的默认文件名。
const HistoryFile = ".csap-apidoc-history.jsonl"

// Strategy 读写与 Java method-field.yaml 格式相同的规则文件。
//
// 文件格式(与 Java 相同,可以直接拿 Java 项目的文件用):
//
//	com.example.UserController:        # 分组(Controller 全名)
//	  get:                             # 接口(方法名)
//	    request:
//	      id.id: {required: true, paramType: QUERY, description: 用户ID}
//	    response:
//	      data.name: {include: true, description: 姓名}
//
// 目录下所有 .yaml/.yml 文件都会加载,文件名不要求与分组对应;同一个分组出现在多个文件里时,
// 按文件名排序以第一个为准(与 Java 合并规则一致)。写入时改的是包含该分组的文件,只替换该接口那一段,
// 其他内容和注释保留;分组还不存在时新建 <简单名>-method-field.yaml。
type Strategy struct {
	opts Options

	mu      sync.Mutex
	loaded  string            // 最近一次 Load 时的文件签名,Watch 拿它做基准
	owner   map[string]string // 分组 -> 文件名(相对 Dir)
	version int64
	subs    []chan struct{}
	journal *strategy.Journal
}

var (
	_ strategy.Saver     = (*Strategy)(nil)
	_ strategy.Watcher   = (*Strategy)(nil)
	_ strategy.Historian = (*Strategy)(nil)
)

// Dir 创建读写 dir 目录的 YAML 策略。
func Dir(dir string) *Strategy { return New(Options{Dir: dir}) }

// FS 创建从 fsys 只读加载的 YAML 策略,dir 是 fsys 里的子目录。
func FS(fsys fs.FS, dir string) *Strategy { return New(Options{FS: fsys, Dir: dir}) }

// New 创建 YAML 策略。
func New(opts Options) *Strategy {
	if opts.Poll == 0 {
		opts.Poll = 2 * time.Second
	}
	s := &Strategy{opts: opts, owner: map[string]string{}}
	switch {
	case opts.FS != nil:
	case opts.History == "-":
		s.journal = strategy.NewJournal("")
	case opts.History != "":
		s.journal = strategy.NewJournal(opts.History)
	default:
		s.journal = strategy.NewJournal(filepath.Join(opts.Dir, HistoryFile))
	}
	return s
}

func (s *Strategy) Name() string { return "yaml:" + s.opts.Dir }

// ReadOnly 为 true 表示从只读文件系统(FS)加载,不能保存。
func (s *Strategy) ReadOnly() bool { return s.opts.FS != nil }

func (s *Strategy) fsys() fs.FS {
	if s.opts.FS != nil {
		sub, err := fs.Sub(s.opts.FS, path.Clean(s.opts.Dir))
		if err != nil {
			return s.opts.FS
		}
		return sub
	}
	return os.DirFS(s.opts.Dir)
}

func (s *Strategy) files() ([]string, error) {
	entries, err := fs.ReadDir(s.fsys(), ".")
	if err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			return nil, nil
		}
		return nil, err
	}
	var out []string
	for _, e := range entries {
		n := e.Name()
		if e.IsDir() || strings.HasPrefix(n, ".") {
			continue
		}
		if strings.HasSuffix(n, ".yaml") || strings.HasSuffix(n, ".yml") {
			out = append(out, n)
		}
	}
	sort.Strings(out)
	return out, nil
}

// Load 加载目录下全部规则。单个文件解析失败时跳过该文件并在 error 里报告,其他文件照常生效。
func (s *Strategy) Load(context.Context) (strategy.Rules, error) {
	sig := s.signature() // 先取签名再读文件,两者之间的修改会在下次检查时再加载
	files, err := s.files()
	if err != nil {
		return nil, err
	}
	rules := strategy.Rules{}
	owner := map[string]string{}
	var errs []error
	for _, f := range files {
		b, err := fs.ReadFile(s.fsys(), f)
		if err != nil {
			errs = append(errs, err)
			continue
		}
		doc, err := parse(b)
		if err != nil {
			errs = append(errs, fmt.Errorf("%s: %w", f, err))
			continue
		}
		for group, methods := range doc {
			if strategy.ReservedKey(group) {
				continue // @ 开头的键不是接口规则
			}
			if _, seen := owner[group]; seen {
				continue // 与 Java 一致:同名分组以先加载的文件为准
			}
			owner[group] = f
			for name, m := range methods {
				rules[strategy.Key(group, name)] = m.toRules()
			}
		}
	}
	s.mu.Lock()
	s.owner = owner
	s.loaded = sig
	s.mu.Unlock()
	return rules, errors.Join(errs...)
}

// Save 写入一个接口的规则。key 为 strategy.Key(group, name);rules 为空表示删除该接口的规则。
// 每次保存在修改记录文件里留一条记录(修改人 by、修改前后内容),见 Options.History。
func (s *Strategy) Save(ctx context.Context, key string, rules strategy.MethodRules, by string) (int64, error) {
	if s.opts.FS != nil {
		return 0, strategy.ErrReadOnly
	}
	i := strings.LastIndex(key, ".")
	if i <= 0 || i == len(key)-1 {
		return 0, fmt.Errorf("apidoc/yaml: invalid key %q", key)
	}
	group, name := key[:i], key[i+1:]

	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.owner) == 0 {
		// 还没加载过:先扫一遍,找到分组所在的文件
		s.mu.Unlock()
		_, _ = s.Load(ctx)
		s.mu.Lock()
	}
	file := s.owner[group]
	if file == "" {
		simple := group
		if j := strings.LastIndexAny(simple, "./"); j >= 0 {
			simple = simple[j+1:]
		}
		file = simple + FileSuffix
	}
	full := filepath.Join(s.opts.Dir, file)
	src, err := os.ReadFile(full)
	if err != nil && !errors.Is(err, fs.ErrNotExist) {
		return 0, err
	}
	var before strategy.MethodRules
	if len(bytes.TrimSpace(src)) > 0 {
		if doc, err := parse(src); err == nil {
			if m, ok := doc[group][name]; ok {
				before = m.toRules()
			}
		}
	}
	out, err := setMethod(src, group, name, fromRules(rules))
	if err != nil {
		return 0, fmt.Errorf("apidoc/yaml: %s: %w", file, err)
	}
	if err := os.MkdirAll(s.opts.Dir, 0o755); err != nil {
		return 0, err
	}
	if err := writeAtomic(full, out); err != nil {
		return 0, err
	}
	s.owner[group] = file
	s.version++
	v := s.version
	if s.journal != nil {
		if _, err := s.journal.Append(strategy.Change{Key: key, Before: strategy.RulesPtr(before),
			After: strategy.RulesPtr(rules), Version: v, UpdatedBy: by}); err != nil {
			return v, fmt.Errorf("apidoc/yaml: 规则已保存,但修改记录写入失败: %w", err)
		}
	}
	for _, c := range s.subs {
		select {
		case c <- struct{}{}:
		default:
		}
	}
	return v, nil
}

// History 返回修改记录,最新的在前。key 为空返回全部;limit<=0 时默认 100。
func (s *Strategy) History(_ context.Context, key string, limit int) ([]strategy.Change, error) {
	if s.journal == nil {
		return []strategy.Change{}, nil
	}
	return s.journal.List(key, limit)
}

// Rollback 把接口恢复成某条修改之前的样子,回滚本身也留一条记录。
func (s *Strategy) Rollback(ctx context.Context, changeID int64, by string) (int64, error) {
	if s.journal == nil {
		return 0, strategy.ErrReadOnly
	}
	c, err := s.journal.Get(changeID)
	if err != nil {
		return 0, err
	}
	var before strategy.MethodRules
	if c.Before != nil {
		before = *c.Before
	}
	return s.Save(ctx, c.Key, before, by)
}

// Watch 监听规则文件变化(轮询文件名、大小、修改时间),以及本策略自己的写入。阻塞到 ctx 结束。
func (s *Strategy) Watch(ctx context.Context, onChange func()) error {
	c := make(chan struct{}, 1)
	s.mu.Lock()
	s.subs = append(s.subs, c)
	s.mu.Unlock()
	defer func() {
		s.mu.Lock()
		for i, x := range s.subs {
			if x == c {
				s.subs = append(s.subs[:i], s.subs[i+1:]...)
				break
			}
		}
		s.mu.Unlock()
	}()
	var tick <-chan time.Time
	if s.opts.Poll > 0 && s.opts.FS == nil {
		t := time.NewTicker(s.opts.Poll)
		defer t.Stop()
		tick = t.C
	}
	changed := func() bool {
		s.mu.Lock()
		loaded := s.loaded
		s.mu.Unlock()
		return s.signature() != loaded
	}
	if changed() { // Load 之后、Watch 之前的修改
		onChange()
	}
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-c:
			onChange()
		case <-tick:
			if changed() {
				onChange() // 调用方会重新 Load,签名随之更新
			}
		}
	}
}

func (s *Strategy) signature() string {
	files, _ := s.files()
	var b strings.Builder
	for _, f := range files {
		info, err := fs.Stat(s.fsys(), f)
		if err != nil {
			continue
		}
		b.WriteString(f)
		b.WriteByte('|')
		b.WriteString(strconv.FormatInt(info.Size(), 10))
		b.WriteByte('|')
		b.WriteString(strconv.FormatInt(info.ModTime().UnixNano(), 10))
		b.WriteByte('\n')
	}
	return b.String()
}

func writeAtomic(name string, data []byte) error {
	tmp, err := os.CreateTemp(filepath.Dir(name), ".tmp-"+filepath.Base(name)+"-*")
	if err != nil {
		return err
	}
	defer os.Remove(tmp.Name())
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if info, err := os.Stat(name); err == nil {
		_ = os.Chmod(tmp.Name(), info.Mode())
	} else {
		_ = os.Chmod(tmp.Name(), 0o644)
	}
	return os.Rename(tmp.Name(), name)
}

// ---------- 文件格式 ----------

// method 是一个接口在 YAML 里的结构,字段顺序与 Java ParamGroupMethodProperty 一致。
type method struct {
	Request    map[string]*field `yaml:"request,omitempty"`
	Response   map[string]*field `yaml:"response,omitempty"`
	Status     string            `yaml:"status,omitempty"`     // 状态覆盖,Go / Java 1.4.0 新增
	StatusNote string            `yaml:"statusNote,omitempty"` // 状态备注
}

// field 对应 Java ParamDataValidate;hidden、example 是 Go 版新增,Java 读取时会忽略。
type field struct {
	Required    *bool      `yaml:"required,omitempty"`
	Include     *bool      `yaml:"include,omitempty"`
	ParamType   string     `yaml:"paramType,omitempty"`
	Description string     `yaml:"description,omitempty"`
	Hidden      *bool      `yaml:"hidden,omitempty"`
	Example     string     `yaml:"example,omitempty"`
	Validate    []validate `yaml:"validate,omitempty"`
	Template    string     `yaml:"template,omitempty"` // 套用的字段模板名,只用于展示
	Mock        any        `yaml:"mock,omitempty"`     // Mock 取值,见 docs/design/1.4.0-mock-schema.md
}

// validate 对应 Java ConstraintValidatorField 写进 YAML 的部分。
type validate struct {
	Descr   string     `yaml:"descr,omitempty"`
	Level   int        `yaml:"level,omitempty"`
	Type    string     `yaml:"type,omitempty"`
	Code    flexString `yaml:"code,omitempty"`
	Message string     `yaml:"message,omitempty"`
	Pattern string     `yaml:"pattern,omitempty"`
}

// flexString 兼容 Java 写出的 code: 1001(数字)和 code: "1001"(字符串)。
type flexString string

func (f *flexString) UnmarshalYAML(n *yamlv3.Node) error {
	if n.Kind != yamlv3.ScalarNode {
		return fmt.Errorf("line %d: code 应为字符串或数字", n.Line)
	}
	*f = flexString(n.Value)
	return nil
}

func parse(b []byte) (map[string]map[string]method, error) {
	var doc map[string]map[string]method
	if err := yamlv3.Unmarshal(b, &doc); err != nil {
		return nil, err
	}
	return doc, nil
}

func (m method) toRules() strategy.MethodRules {
	conv := func(in map[string]*field) map[string]strategy.FieldRule {
		if len(in) == 0 {
			return nil
		}
		out := make(map[string]strategy.FieldRule, len(in))
		for p, f := range in {
			if f == nil {
				out[p] = strategy.FieldRule{}
				continue
			}
			r := strategy.FieldRule{
				Description: f.Description, Required: f.Required, Include: f.Include, Hidden: f.Hidden,
				ParamType: strings.ToUpper(f.ParamType), Example: f.Example, Template: f.Template, Mock: f.Mock,
			}
			for _, v := range f.Validate {
				r.Validate = append(r.Validate, strategy.ValidateRule{
					Type: v.Type, Pattern: v.Pattern, Code: string(v.Code), Message: v.Message, Descr: v.Descr, Level: v.Level,
				})
			}
			out[p] = r
		}
		return out
	}
	return strategy.MethodRules{Request: conv(m.Request), Response: conv(m.Response), Status: m.Status, StatusNote: m.StatusNote}
}

func fromRules(r strategy.MethodRules) method {
	conv := func(in map[string]strategy.FieldRule) map[string]*field {
		if len(in) == 0 {
			return nil
		}
		out := make(map[string]*field, len(in))
		for p, f := range in {
			x := &field{Required: f.Required, Include: f.Include, ParamType: f.ParamType, Description: f.Description,
				Hidden: f.Hidden, Example: f.Example, Template: f.Template, Mock: f.Mock}
			for _, v := range f.Validate {
				x.Validate = append(x.Validate, validate{Descr: v.Descr, Level: v.Level, Type: v.Type,
					Code: flexString(v.Code), Message: v.Message, Pattern: v.Pattern})
			}
			out[p] = x
		}
		return out
	}
	m := method{Request: conv(r.Request), Response: conv(r.Response), StatusNote: r.StatusNote}
	if strategy.ValidStatus(r.Status) {
		m.Status = r.Status
	}
	return m
}

// setMethod 在原文件内容里替换(或新增、删除)一个接口的规则,其余节点和注释保持不变。
func setMethod(src []byte, group, name string, m method) ([]byte, error) {
	var doc yamlv3.Node
	if len(bytes.TrimSpace(src)) > 0 {
		if err := yamlv3.Unmarshal(src, &doc); err != nil {
			return nil, err
		}
	}
	if doc.Kind == 0 {
		doc = yamlv3.Node{Kind: yamlv3.DocumentNode, Content: []*yamlv3.Node{{Kind: yamlv3.MappingNode}}}
	}
	if doc.Kind != yamlv3.DocumentNode || len(doc.Content) == 0 || doc.Content[0].Kind != yamlv3.MappingNode {
		return nil, errors.New("文件顶层不是映射")
	}
	root := doc.Content[0]
	empty := len(m.Request) == 0 && len(m.Response) == 0 && m.Status == "" && m.StatusNote == ""

	gi := findKey(root, group)
	if gi < 0 {
		if empty {
			return src, nil
		}
		root.Content = append(root.Content,
			&yamlv3.Node{Kind: yamlv3.ScalarNode, Value: group},
			&yamlv3.Node{Kind: yamlv3.MappingNode})
		gi = len(root.Content) - 2
	}
	gnode := root.Content[gi+1]
	if gnode.Kind != yamlv3.MappingNode { // 如 "Group:" 后面为空
		gnode.Kind, gnode.Tag, gnode.Value, gnode.Content = yamlv3.MappingNode, "", "", nil
	}
	mi := findKey(gnode, name)
	if empty {
		if mi >= 0 {
			gnode.Content = append(gnode.Content[:mi], gnode.Content[mi+2:]...)
		}
	} else {
		var val yamlv3.Node
		if err := val.Encode(m); err != nil {
			return nil, err
		}
		sortMappingKeys(&val)
		if mi >= 0 {
			gnode.Content[mi+1] = &val
		} else {
			gnode.Content = append(gnode.Content, &yamlv3.Node{Kind: yamlv3.ScalarNode, Value: name}, &val)
		}
	}
	var buf bytes.Buffer
	enc := yamlv3.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(&doc); err != nil {
		return nil, err
	}
	_ = enc.Close()
	return buf.Bytes(), nil
}

func findKey(m *yamlv3.Node, key string) int {
	for i := 0; i+1 < len(m.Content); i += 2 {
		if m.Content[i].Value == key {
			return i
		}
	}
	return -1
}

// sortMappingKeys 让 request/response 下的字段按路径长度、再按字母排序,
// 上级在前,读起来与 Java 的 sortedKeyLength 一致。
func sortMappingKeys(n *yamlv3.Node) {
	if n.Kind != yamlv3.MappingNode {
		return
	}
	for i := 1; i < len(n.Content); i += 2 {
		k := n.Content[i-1].Value
		if (k == "request" || k == "response") && n.Content[i].Kind == yamlv3.MappingNode {
			c := n.Content[i]
			type pair struct{ k, v *yamlv3.Node }
			ps := make([]pair, 0, len(c.Content)/2)
			for j := 0; j+1 < len(c.Content); j += 2 {
				ps = append(ps, pair{c.Content[j], c.Content[j+1]})
			}
			sort.SliceStable(ps, func(a, b int) bool {
				ka, kb := ps[a].k.Value, ps[b].k.Value
				if len(ka) != len(kb) {
					return len(ka) < len(kb)
				}
				return ka < kb
			})
			c.Content = c.Content[:0]
			for _, p := range ps {
				c.Content = append(c.Content, p.k, p.v)
			}
		}
	}
}

// TemplateFile 是项目字段模板文件相对规则目录的位置:与 method-field 目录并列的 field-template/api-field-template.yaml。
const TemplateFile = "field-template/api-field-template.yaml"

var _ strategy.RawStore = (*Strategy)(nil)

func (s *Strategy) templatePath() string {
	return filepath.Join(filepath.Dir(filepath.Clean(s.opts.Dir)), filepath.FromSlash(TemplateFile))
}

// LoadRaw 读取项目字段模板文件(只认保留键 @templates)。文件不存在时 ok 为 false。
// 返回的是文件原文(YAML),由字段库解析。规则目录来自 FS(只读打包)时没有项目模板。
func (s *Strategy) LoadRaw(_ context.Context, key string) (string, bool, error) {
	if key != "@templates" {
		return "", false, fmt.Errorf("apidoc/yaml: 不支持的保留键 %q", key)
	}
	if s.opts.FS != nil || s.opts.Dir == "" {
		return "", false, nil
	}
	b, err := os.ReadFile(s.templatePath())
	if errors.Is(err, os.ErrNotExist) {
		return "", false, nil
	}
	return string(b), err == nil, err
}

// SaveRaw 写项目字段模板文件(只认 @templates)。raw 是 JSON 或 YAML,统一写成 YAML;为空表示删除文件。
// 模板文件不走版本号和修改记录(返回 0),由 Git 管理即可。规则目录来自 FS(只读打包)时返回 ErrReadOnly。
func (s *Strategy) SaveRaw(_ context.Context, key, raw, _ string) (int64, error) {
	if key != "@templates" {
		return 0, fmt.Errorf("apidoc/yaml: 不支持的保留键 %q", key)
	}
	if s.opts.FS != nil || s.opts.Dir == "" {
		return 0, strategy.ErrReadOnly
	}
	path := s.templatePath()
	if raw == "" {
		if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
			return 0, err
		}
		return 0, nil
	}
	var v any
	if err := yamlv3.Unmarshal([]byte(raw), &v); err != nil {
		return 0, err
	}
	out, err := yamlv3.Marshal(v)
	if err != nil {
		return 0, err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return 0, err
	}
	return 0, os.WriteFile(path, out, 0o644)
}
