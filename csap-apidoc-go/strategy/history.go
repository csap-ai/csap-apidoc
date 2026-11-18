package strategy

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// Change 是一条规则修改记录。YAML、SQL、Memory 三种可写策略都按这个格式记录。
type Change struct {
	ID        int64        `json:"id"`
	Key       string       `json:"key"`    // 接口规则的键 group.name
	Before    *MethodRules `json:"before"` // nil 表示修改前没有规则
	After     *MethodRules `json:"after"`  // nil 表示这次是删除
	Version   int64        `json:"version"`
	UpdatedBy string       `json:"updatedBy"`
	UpdatedAt time.Time    `json:"updatedAt"`
}

// Historian 是能查询修改记录、按记录回滚的策略。devtools 的修改记录和回滚接口依赖它。
type Historian interface {
	// History 返回修改记录,最新的在前。key 为空返回全部;limit<=0 时默认 100。
	History(ctx context.Context, key string, limit int) ([]Change, error)
	// Rollback 把某条记录对应的接口恢复成"修改前"的样子。回滚本身也是一次保存,会留下新的记录。
	Rollback(ctx context.Context, changeID int64, by string) (version int64, err error)
}

// ErrNoChange 表示找不到修改记录。
var ErrNoChange = errors.New("apidoc: change not found")

// IsEmpty 判断规则是否为空(保存空规则表示删除)。
func (m MethodRules) IsEmpty() bool { return len(m.Request) == 0 && len(m.Response) == 0 }

// Journal 保存修改记录,供没有数据库的策略(YAML、Memory)使用。
// Path 为空时只记在内存里(重启后丢失);否则追加写到该文件(每行一条 JSON)。
type Journal struct {
	Path string

	mu      sync.Mutex
	loaded  bool
	changes []Change
}

// NewJournal 创建修改记录。path 为空表示只记在内存里。
func NewJournal(path string) *Journal { return &Journal{Path: path} }

func (j *Journal) loadLocked() error {
	if j.loaded {
		return nil
	}
	j.loaded = true
	if j.Path == "" {
		return nil
	}
	b, err := os.ReadFile(j.Path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	sc := bufio.NewScanner(bytes.NewReader(b))
	sc.Buffer(make([]byte, 0, 64*1024), 64<<20)
	for sc.Scan() {
		line := bytes.TrimSpace(sc.Bytes())
		if len(line) == 0 {
			continue
		}
		var c Change
		if err := json.Unmarshal(line, &c); err != nil {
			continue // 写到一半的行(进程被杀)跳过
		}
		j.changes = append(j.changes, c)
	}
	return sc.Err()
}

// Append 追加一条记录,自动分配 ID 和时间,返回带 ID 的记录。
func (j *Journal) Append(c Change) (Change, error) {
	j.mu.Lock()
	defer j.mu.Unlock()
	if err := j.loadLocked(); err != nil {
		return c, err
	}
	c.ID = 1
	if n := len(j.changes); n > 0 {
		c.ID = j.changes[n-1].ID + 1
	}
	if c.UpdatedAt.IsZero() {
		c.UpdatedAt = time.Now()
	}
	if j.Path != "" {
		line, err := json.Marshal(c)
		if err != nil {
			return c, err
		}
		if err := os.MkdirAll(filepath.Dir(j.Path), 0o755); err != nil {
			return c, err
		}
		f, err := os.OpenFile(j.Path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
		if err != nil {
			return c, err
		}
		_, werr := f.Write(append(line, '\n'))
		cerr := f.Close()
		if werr != nil {
			return c, werr
		}
		if cerr != nil {
			return c, cerr
		}
	}
	j.changes = append(j.changes, c)
	return c, nil
}

// List 返回修改记录,最新的在前。key 为空返回全部;limit<=0 时默认 100。
func (j *Journal) List(key string, limit int) ([]Change, error) {
	if limit <= 0 {
		limit = 100
	}
	j.mu.Lock()
	defer j.mu.Unlock()
	if err := j.loadLocked(); err != nil {
		return nil, err
	}
	out := []Change{}
	for i := len(j.changes) - 1; i >= 0 && len(out) < limit; i-- {
		if key == "" || j.changes[i].Key == key {
			out = append(out, j.changes[i])
		}
	}
	return out, nil
}

// Get 按 ID 查一条记录。
func (j *Journal) Get(id int64) (Change, error) {
	j.mu.Lock()
	defer j.mu.Unlock()
	if err := j.loadLocked(); err != nil {
		return Change{}, err
	}
	i := sort.Search(len(j.changes), func(i int) bool { return j.changes[i].ID >= id })
	if i < len(j.changes) && j.changes[i].ID == id {
		return j.changes[i], nil
	}
	return Change{}, fmt.Errorf("%w: %d", ErrNoChange, id)
}

// RulesPtr 返回规则的副本指针,空规则返回 nil(用于 Change.Before / After)。
func RulesPtr(m MethodRules) *MethodRules {
	if m.IsEmpty() {
		return nil
	}
	cp := Clone(m)
	return &cp
}

// Clone 深拷贝一个接口的规则。
func Clone(m MethodRules) MethodRules {
	cp := func(in map[string]FieldRule) map[string]FieldRule {
		if in == nil {
			return nil
		}
		out := make(map[string]FieldRule, len(in))
		for k, v := range in {
			if v.Validate != nil {
				v.Validate = append([]ValidateRule(nil), v.Validate...)
			}
			out[k] = v
		}
		return out
	}
	return MethodRules{Request: cp(m.Request), Response: cp(m.Response)}
}
