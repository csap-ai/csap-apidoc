package strategy

import (
	"context"
	"errors"
	"strings"
	"sync"
)

// ErrReadOnly 表示该策略不支持写入。
var ErrReadOnly = errors.New("apidoc: strategy is read-only")

// ValidateRule 是一条请求参数校验规则,字段与 Java YAML 的 validate 段一致。
type ValidateRule struct {
	Type    string `json:"type" yaml:"type"`       // NotNull / NotEmpty / Pattern ...
	Pattern string `json:"pattern" yaml:"pattern"` // 正则或内置规则名
	Code    string `json:"code" yaml:"code"`
	Message string `json:"message" yaml:"message"`
	Descr   string `json:"descr" yaml:"descr"`
	Level   int    `json:"level" yaml:"level"`
}

// FieldRule 是一个字段的规则。Path 用点号分隔,写法与 Java 一致:
// 请求参数为 "<参数名>.<字段>"(如 request.username、id.id),返回字段为 "data.rows.id"。
// 指针字段为 nil 表示该来源没有配置,合并时不覆盖前面的值。
type FieldRule struct {
	Description string         `json:"description,omitempty" yaml:"description,omitempty"`
	Required    *bool          `json:"required,omitempty" yaml:"required,omitempty"`
	Include     *bool          `json:"include,omitempty" yaml:"include,omitempty"` // 返回字段:是否输出
	Hidden      *bool          `json:"hidden,omitempty" yaml:"hidden,omitempty"`   // 文档中隐藏
	ParamType   string         `json:"paramType,omitempty" yaml:"paramType,omitempty"`
	Example     string         `json:"example,omitempty" yaml:"example,omitempty"`
	Validate    []ValidateRule `json:"validate,omitempty" yaml:"validate,omitempty"`
	// Template 是套用字段模板时记下的模板名,只用于展示和"与模板不一致"的提示,运行时不读。
	Template string `json:"template,omitempty" yaml:"template,omitempty"`
	// Mock 是 Mock 响应里这个字段的取值(任意 JSON 值,字符串可以是 @int / @oneOf / @uuid 表达式),
	// 只在 Mock 里使用,见 docs/design/1.4.0-mock-schema.md。
	Mock any `json:"mock,omitempty" yaml:"mock,omitempty"`
}

// MethodRules 是一个接口的全部字段规则,以及可选的上下线状态(见 docs/design/1.4.0-api-status.md)。
type MethodRules struct {
	Request  map[string]FieldRule `json:"request,omitempty" yaml:"request,omitempty"`
	Response map[string]FieldRule `json:"response,omitempty" yaml:"response,omitempty"`
	// Status 是接口状态的覆盖值:DEFAULT(开发中)/ FINISH(完成)/ DEPRECATED(过时)。
	// 空表示没有覆盖;不认识的值按没有处理(向前兼容)。
	Status string `json:"status,omitempty" yaml:"status,omitempty"`
	// StatusNote 是状态备注,如替代接口、下线时间。
	StatusNote string `json:"statusNote,omitempty" yaml:"statusNote,omitempty"`
}

// 接口状态,与 Java ApiStatus 的名字一致(大小写敏感)。
const (
	StatusDefault    = "DEFAULT"
	StatusFinish     = "FINISH"
	StatusDeprecated = "DEPRECATED"
)

// ValidStatus 判断是不是认识的状态值。
func ValidStatus(s string) bool {
	return s == StatusDefault || s == StatusFinish || s == StatusDeprecated
}

// RawStore 是能按键读写原始 JSON 的规则来源,用来存取 @templates 这类保留键(不是接口规则)。
// 与 Save 同样走版本号、修改记录和多实例同步。raw 为空表示删除。
type RawStore interface {
	LoadRaw(ctx context.Context, key string) (raw string, ok bool, err error)
	SaveRaw(ctx context.Context, key, raw, by string) (version int64, err error)
}

// ReservedKey 判断规则键是不是保留键:以 @ 开头的不是接口规则(如 @templates),各规则来源加载时跳过。
func ReservedKey(key string) bool { return strings.HasPrefix(key, "@") }

// Rules 的键为 Key(group, name)。
type Rules map[string]MethodRules

// Key 返回规则的键:分组名 + "." + 接口名,与 Java 的 className.methodName 一致。
func Key(group, name string) string { return group + "." + name }

// Strategy 是字段规则的来源。
type Strategy interface {
	Name() string
	Load(ctx context.Context) (Rules, error)
}

// Saver 是可写入的策略(YAML、SQL、Memory)。
type Saver interface {
	Save(ctx context.Context, key string, rules MethodRules, by string) (version int64, err error)
}

// Watcher 是能感知外部变更的策略,变更时调用 onChange。Watch 阻塞到 ctx 结束。
type Watcher interface {
	Watch(ctx context.Context, onChange func()) error
}

// Merge 按顺序合并多个来源的规则,后面的覆盖前面的。
func Merge(all ...Rules) Rules {
	out := Rules{}
	for _, rs := range all {
		for k, mr := range rs {
			cur := out[k]
			cur.Request = mergeFields(cur.Request, mr.Request)
			cur.Response = mergeFields(cur.Response, mr.Response)
			if ValidStatus(mr.Status) {
				cur.Status = mr.Status
			}
			if mr.StatusNote != "" {
				cur.StatusNote = mr.StatusNote
			}
			out[k] = cur
		}
	}
	return out
}

func mergeFields(dst, src map[string]FieldRule) map[string]FieldRule {
	if len(src) == 0 {
		return dst
	}
	if dst == nil {
		dst = make(map[string]FieldRule, len(src))
	}
	for p, r := range src {
		cur, ok := dst[p]
		if !ok {
			dst[p] = r
			continue
		}
		if r.Description != "" {
			cur.Description = r.Description
		}
		if r.Required != nil {
			cur.Required = r.Required
		}
		if r.Include != nil {
			cur.Include = r.Include
		}
		if r.Hidden != nil {
			cur.Hidden = r.Hidden
		}
		if r.ParamType != "" {
			cur.ParamType = r.ParamType
		}
		if r.Example != "" {
			cur.Example = r.Example
		}
		if r.Validate != nil {
			cur.Validate = r.Validate
		}
		if r.Template != "" {
			cur.Template = r.Template
		}
		if r.Mock != nil {
			cur.Mock = r.Mock
		}
		dst[p] = cur
	}
	return dst
}

// Memory 是内存里的可写策略,用于测试、示例,以及在代码里直接下发规则。
// 修改记录也只保存在内存里。
type Memory struct {
	mu      sync.RWMutex
	rules   Rules
	version int64
	subs    []chan struct{}
	journal Journal
}

// NewMemory 创建内存策略。
func NewMemory(initial Rules) *Memory {
	if initial == nil {
		initial = Rules{}
	}
	return &Memory{rules: initial}
}

func (m *Memory) Name() string { return "memory" }

func (m *Memory) Load(context.Context) (Rules, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make(Rules, len(m.rules))
	for k, v := range m.rules {
		out[k] = v
	}
	return out, nil
}

func (m *Memory) Save(_ context.Context, key string, rules MethodRules, by string) (int64, error) {
	m.mu.Lock()
	before := m.rules[key]
	if rules.IsEmpty() {
		delete(m.rules, key)
	} else {
		m.rules[key] = Clone(rules)
	}
	m.version++
	v := m.version
	_, _ = m.journal.Append(Change{Key: key, Before: RulesPtr(before), After: RulesPtr(rules), Version: v, UpdatedBy: by})
	subs := append([]chan struct{}(nil), m.subs...)
	m.mu.Unlock()
	for _, c := range subs {
		select {
		case c <- struct{}{}:
		default:
		}
	}
	return v, nil
}

// History 返回修改记录,最新的在前。
func (m *Memory) History(_ context.Context, key string, limit int) ([]Change, error) {
	return m.journal.List(key, limit)
}

// Rollback 把接口恢复成某条修改之前的样子。
func (m *Memory) Rollback(ctx context.Context, changeID int64, by string) (int64, error) {
	c, err := m.journal.Get(changeID)
	if err != nil {
		return 0, err
	}
	var before MethodRules
	if c.Before != nil {
		before = *c.Before
	}
	return m.Save(ctx, c.Key, before, by)
}

func (m *Memory) Watch(ctx context.Context, onChange func()) error {
	c := make(chan struct{}, 1)
	m.mu.Lock()
	m.subs = append(m.subs, c)
	m.mu.Unlock()
	defer func() {
		m.mu.Lock()
		for i, s := range m.subs {
			if s == c {
				m.subs = append(m.subs[:i], m.subs[i+1:]...)
				break
			}
		}
		m.mu.Unlock()
	}()
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-c:
			onChange()
		}
	}
}
