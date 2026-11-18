package filter

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
)

// TestGoldenJava 用 Java 线上版本的真实返回验证路径白名单(ModePath)与 Java 按类过滤的输出一致。默认跳过,需要:
//
//	CSAP_GOLDEN_DOC=<Java /csap/apidoc 的返回>     提供每个接口最终可见的返回字段(= 类型里存在的 ∩ YAML include,∪ forceRep)
//	CSAP_GOLDEN_RESP=<Java 接口返回目录>           文件名 <Controller 全名>.<方法名>.json
//
// 这些数据属于使用方,不放进本仓库。Go 版的构建器从结构体、doc_force 标签和规则算出同样的可见字段集合
// (单元测试覆盖),这里验证的是:按路径白名单裁剪,与 Java 按类挂 PropertyPreFilter 的实际输出相同。
//
// Java 序列化开了 WriteMapNullValue,白名单里的字段即使为 null 也会输出,所以 Java 返回里
// 每个对象的键集合就是它的白名单。测试逐个对象比较"Go 掩码放行的键"和"Java 实际输出的键",
// 两边必须完全相同;再验证 Prune 不改动 Java 的返回、并能去掉额外注入的字段。
func TestGoldenJava(t *testing.T) {
	docFile, respDir := os.Getenv("CSAP_GOLDEN_DOC"), os.Getenv("CSAP_GOLDEN_RESP")
	if docFile == "" || respDir == "" {
		t.Skip("CSAP_GOLDEN_DOC / CSAP_GOLDEN_RESP 未设置")
	}
	visible := loadVisible(t, docFile)
	files, _ := filepath.Glob(filepath.Join(respDir, "*.json"))
	checked := 0
	for _, f := range files {
		key := strings.TrimSuffix(filepath.Base(f), ".json")
		src, _ := os.ReadFile(f)
		if !bytes.HasPrefix(src, []byte(`{"code":"0"`)) {
			continue // 只比对成功且走了过滤(紧凑输出)的返回
		}
		vis, ok := visible[key]
		if !ok {
			t.Errorf("%s: 文档里没有这个接口", key)
			continue
		}
		mask := Compile(vis.paths, vis.maps)

		var v any
		dec := json.NewDecoder(bytes.NewReader(src))
		dec.UseNumber()
		if err := dec.Decode(&v); err != nil {
			t.Fatalf("%s: %v", key, err)
		}
		var diffs []string
		compareKeys("$", v, mask, &diffs)
		if len(diffs) > 0 {
			t.Errorf("%s: 键集合与 Java 不同:\n  %s", key, strings.Join(diffs, "\n  "))
			continue
		}
		out, err := Prune(src, mask)
		if err != nil || !bytes.Equal(out, src) {
			t.Errorf("%s: Prune 改动了 Java 的返回(err=%v)", key, err)
			continue
		}
		injected := inject(t, v, mask)
		out, err = Prune(injected, mask)
		if err != nil {
			t.Fatalf("%s: %v", key, err)
		}
		if !bytes.Equal(normalize(t, out), normalize(t, src)) {
			t.Errorf("%s: 注入的额外字段没有被去掉", key)
			continue
		}
		checked++
	}
	t.Logf("比对通过 %d 个接口", checked)
	if checked == 0 {
		t.Fatal("没有可比对的样本")
	}
}

type visibleSet struct {
	paths []string
	maps  map[string]bool
}

// loadVisible 从 Java /csap/apidoc 里取每个接口返回字段的 keyName(经由 children 递归,跳过 $ref)。
func loadVisible(t *testing.T, file string) map[string]visibleSet {
	b, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	type param struct {
		KeyName  string `json:"keyName"`
		DataType string `json:"dataType"`
		Children *struct {
			Parameters []json.RawMessage `json:"parameters"`
		} `json:"children"`
	}
	var doc struct {
		Data struct {
			ApiList []struct {
				MethodList []struct {
					Key      string `json:"key"`
					Response []struct {
						Parameters []json.RawMessage `json:"parameters"`
					} `json:"response"`
				} `json:"methodList"`
			} `json:"apiList"`
		} `json:"data"`
	}
	if err := json.Unmarshal(b, &doc); err != nil {
		t.Fatal(err)
	}
	out := map[string]visibleSet{}
	for _, c := range doc.Data.ApiList {
		for _, m := range c.MethodList {
			vs := visibleSet{maps: map[string]bool{}}
			var walk func([]json.RawMessage)
			walk = func(ps []json.RawMessage) {
				for _, raw := range ps {
					var p param
					if err := json.Unmarshal(raw, &p); err != nil || p.KeyName == "" {
						continue
					}
					vs.paths = append(vs.paths, p.KeyName)
					if p.Children != nil {
						if strings.HasPrefix(p.DataType, "Map<") {
							vs.maps[p.KeyName] = true
						}
						walk(p.Children.Parameters)
					}
				}
			}
			for _, r := range m.Response {
				walk(r.Parameters)
			}
			out[m.Key] = vs
		}
	}
	return out
}

func compareKeys(path string, v any, n *Node, diffs *[]string) {
	if n == nil {
		return
	}
	switch t := v.(type) {
	case []any:
		for _, e := range t {
			compareKeys(path+"[]", e, n, diffs)
		}
	case map[string]any:
		if n.MapValues != nil {
			for k, e := range t {
				compareKeys(path+"."+k, e, n.MapValues, diffs)
			}
			return
		}
		got := map[string]bool{}
		for k := range t {
			got[strategy.ToUnderline(k)] = true
		}
		want := map[string]bool{}
		for k := range n.Fields {
			want[strategy.ToUnderline(k)] = true
		}
		var only []string
		for k := range got {
			if !want[k] {
				only = append(only, "Java 有、Go 没放行: "+path+"."+k)
			}
		}
		for k := range want {
			if !got[k] {
				only = append(only, "Go 放行、Java 没输出: "+path+"."+k)
			}
		}
		sort.Strings(only)
		if len(only) > 0 {
			*diffs = append(*diffs, only...)
			return
		}
		for k, e := range t {
			_, child := n.allow(k)
			compareKeys(path+"."+k, e, child, diffs)
		}
	}
}

// inject 给每个受掩码约束的对象加一个不在白名单里的字段(掩码为 nil 的值本来就原样输出,不注入)。
func inject(t *testing.T, v any, mask *Node) []byte {
	var walk func(any, *Node)
	walk = func(x any, n *Node) {
		if n == nil {
			return
		}
		switch tt := x.(type) {
		case map[string]any:
			for k, e := range tt {
				_, c := n.allow(k)
				walk(e, c)
			}
			if n.MapValues == nil {
				tt["zz_injected_extra"] = map[string]any{"a": 1}
			}
		case []any:
			for _, e := range tt {
				walk(e, n)
			}
		}
	}
	walk(v, mask)
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	return b
}

func normalize(t *testing.T, b []byte) []byte {
	var v any
	dec := json.NewDecoder(bytes.NewReader(b))
	dec.UseNumber()
	if err := dec.Decode(&v); err != nil {
		t.Fatal(err)
	}
	out, _ := json.Marshal(v)
	return out
}
