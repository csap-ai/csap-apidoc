package apidiff

import (
	"encoding/json"
	"fmt"
	"strings"
)

// Summary 是各级别的条数(不含被忽略的)。
type Summary struct {
	Breaking int `json:"breaking"`
	Warning  int `json:"warning"`
	Info     int `json:"info"`
	Ignored  int `json:"ignored"`
}

// Source 描述被比较的一份文档。
type Source struct {
	Source    string `json:"source"`
	Title     string `json:"title,omitempty"`
	Version   string `json:"version,omitempty"`
	Endpoints int    `json:"endpoints"`
	CreatedAt string `json:"createdAt,omitempty"`
}

// Report 是一次比较的结果。
type Report struct {
	Old     Source   `json:"old"`
	New     Source   `json:"new"`
	Summary Summary  `json:"summary"`
	Changes []Change `json:"changes"`
}

// NewReport 比较并套用忽略清单。
func NewReport(oldDoc, newDoc *Doc, ignore []IgnoreRule) *Report {
	ch := Diff(oldDoc, newDoc)
	ApplyIgnore(ch, ignore)
	r := &Report{Changes: ch,
		Old: Source{oldDoc.Source, oldDoc.Title, oldDoc.Version, len(oldDoc.Endpoints), oldDoc.CreatedAt},
		New: Source{newDoc.Source, newDoc.Title, newDoc.Version, len(newDoc.Endpoints), newDoc.CreatedAt}}
	for _, c := range ch {
		switch {
		case c.Ignored:
			r.Summary.Ignored++
		case c.Level == Breaking:
			r.Summary.Breaking++
		case c.Level == Warning:
			r.Summary.Warning++
		default:
			r.Summary.Info++
		}
	}
	return r
}

// Fails 按 failOn 判断是否应当让 CI 失败:failOn 为 Breaking 时有破坏性变更失败,为 Warning 时有警告也失败;
// failOn 为空表示永不失败。被忽略的变更不计。
func (r *Report) Fails(failOn Level) bool {
	switch failOn {
	case Breaking:
		return r.Summary.Breaking > 0
	case Warning:
		return r.Summary.Breaking > 0 || r.Summary.Warning > 0
	}
	return false
}

func (r *Report) visible(min Level) []Change {
	var out []Change
	for _, c := range r.Changes {
		if c.Level.rank() >= min.rank() {
			out = append(out, c)
		}
	}
	return out
}

// JSON 输出 JSON 报告(min 以下级别的变更不列出,汇总数字不受影响)。
func (r *Report) JSON(min Level) ([]byte, error) {
	c := *r
	c.Changes = r.visible(min)
	if c.Changes == nil {
		c.Changes = []Change{}
	}
	b, err := json.MarshalIndent(&c, "", "  ")
	return append(b, '\n'), err
}

var levelTitle = map[Level]string{Breaking: "破坏性变更", Warning: "需要留意", Info: "兼容的变化"}

func (r *Report) header() string {
	desc := func(s Source) string {
		d := s.Source
		if s.Version != "" {
			d += "(" + s.Version + ")"
		}
		return fmt.Sprintf("%s,%d 个接口", d, s.Endpoints)
	}
	return fmt.Sprintf("旧:%s\n新:%s", desc(r.Old), desc(r.New))
}

func (r *Report) summaryLine() string {
	return fmt.Sprintf("破坏性 %d,需留意 %d,兼容 %d,已忽略 %d", r.Summary.Breaking, r.Summary.Warning, r.Summary.Info, r.Summary.Ignored)
}

// Text 输出适合终端和日志的文本报告。
func (r *Report) Text(min Level) string {
	var b strings.Builder
	b.WriteString(r.header() + "\n" + r.summaryLine() + "\n")
	for _, lv := range []Level{Breaking, Warning, Info} {
		if lv.rank() < min.rank() {
			continue
		}
		var rows []Change
		for _, c := range r.Changes {
			if c.Level == lv && !c.Ignored {
				rows = append(rows, c)
			}
		}
		if len(rows) == 0 {
			continue
		}
		fmt.Fprintf(&b, "\n== %s(%d)==\n", levelTitle[lv], len(rows))
		for _, c := range rows {
			fmt.Fprintf(&b, "  [%s] %s\n", c.Kind, locate(c))
			fmt.Fprintf(&b, "      %s\n", c.Message)
		}
	}
	if r.Summary.Ignored > 0 {
		fmt.Fprintf(&b, "\n(另有 %d 条命中忽略清单,未计入;用 -format json 可查看)\n", r.Summary.Ignored)
	}
	if r.Summary.Breaking == 0 {
		b.WriteString("\n没有破坏性变更。\n")
	}
	return b.String()
}

func locate(c Change) string {
	s := c.Endpoint
	if s == "" {
		s = "(全局)"
	}
	if c.Where != "" && c.Field != "" {
		s += " " + c.Where + ":" + c.Field
	} else if c.Field != "" {
		s += " " + c.Field
	}
	return s
}

// Markdown 输出 Markdown 报告,可直接追加到 $GITHUB_STEP_SUMMARY 或贴进 PR 评论。
func (r *Report) Markdown(min Level) string {
	var b strings.Builder
	b.WriteString("## API 变更检查\n\n")
	status := "✅ 没有破坏性变更"
	if r.Summary.Breaking > 0 {
		status = fmt.Sprintf("❌ 发现 %d 处破坏性变更", r.Summary.Breaking)
	}
	fmt.Fprintf(&b, "**%s**(需留意 %d,兼容 %d,已忽略 %d)\n\n", status, r.Summary.Warning, r.Summary.Info, r.Summary.Ignored)
	fmt.Fprintf(&b, "- 旧:`%s`,%d 个接口\n- 新:`%s`,%d 个接口\n", r.Old.Source, r.Old.Endpoints, r.New.Source, r.New.Endpoints)
	for _, lv := range []Level{Breaking, Warning, Info} {
		if lv.rank() < min.rank() {
			continue
		}
		var rows []Change
		for _, c := range r.Changes {
			if c.Level == lv && !c.Ignored {
				rows = append(rows, c)
			}
		}
		if len(rows) == 0 {
			continue
		}
		fmt.Fprintf(&b, "\n### %s(%d)\n\n| 接口 | 位置 | 类型 | 说明 |\n|---|---|---|---|\n", levelTitle[lv], len(rows))
		for _, c := range rows {
			ep := c.Endpoint
			if ep == "" {
				ep = "(全局)"
			}
			loc := c.Where
			if c.Field != "" {
				loc = strings.TrimPrefix(loc+" "+c.Field, " ")
			}
			fmt.Fprintf(&b, "| `%s` | %s | `%s` | %s |\n", mdEscape(ep), mdEscape(loc), c.Kind, mdEscape(c.Message))
		}
	}
	return b.String()
}

func mdEscape(s string) string {
	s = strings.ReplaceAll(s, "|", "\\|")
	return strings.ReplaceAll(s, "\n", " ")
}
