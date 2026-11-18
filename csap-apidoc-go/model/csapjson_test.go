package model

import (
	"strings"
	"testing"
)

func TestMarshalCsapFormat(t *testing.T) {
	v := map[string]any{
		"z":          "末尾",
		"a":          1,
		"nil":        nil,
		"empty":      []string{},
		"group":      []string{"default"},
		"methods":    []string{"GET"},
		"paramNames": []string{"id", "limit"},
		"objs":       []map[string]any{{"k": "v"}},
		"html":       "<a>&",
	}
	got, err := MarshalCsap(v, 0)
	if err != nil {
		t.Fatal(err)
	}
	want := strings.Join([]string{
		"{",
		"\t\"a\":1,",
		"\t\"empty\":[],",
		"\t\"group\":[\"default\"],",
		"\t\"html\":\"<a>&\",",
		"\t\"methods\":[",
		"\t\t\"GET\"",
		"\t],",
		"\t\"objs\":[",
		"\t\t{",
		"\t\t\t\"k\":\"v\"",
		"\t\t}",
		"\t],",
		"\t\"paramNames\":[",
		"\t\t\"id\",",
		"\t\t\"limit\"",
		"\t],",
		"\t\"z\":\"末尾\"",
		"}",
	}, "\n")
	if string(got) != want {
		t.Fatalf("got:\n%s\nwant:\n%s", got, want)
	}
}

func TestResultJSONEmbedsRaw(t *testing.T) {
	data, err := MarshalCsap(map[string]any{"x": true}, 1)
	if err != nil {
		t.Fatal(err)
	}
	out := string(ResultJSON("0", "成功", data))
	for _, s := range []string{
		"{\n\t\"code\":\"0\",\n\t\"data\":{\n\t\t\"x\":true\n\t},\n",
		"\t\"language\":\"ZH\",\n\t\"message\":\"成功\",\n\t\"success\":true,\n\t\"time\":",
	} {
		if !strings.Contains(out, s) {
			t.Fatalf("missing %q in\n%s", s, out)
		}
	}
	if !strings.HasSuffix(out, "\n}") {
		t.Fatalf("bad tail: %q", out[len(out)-5:])
	}
}

func TestNullFieldsOmitted(t *testing.T) {
	m := &Model{ModelType: ModelBaseData, Name: "string", Parameters: []*Parameter{}}
	got, _ := MarshalCsap(m, 0)
	s := string(got)
	for _, k := range []string{"description", "value", "methodParamName", "force", "global"} {
		if strings.Contains(s, "\""+k+"\"") {
			t.Fatalf("%s should be omitted: %s", k, s)
		}
	}
	if !strings.Contains(s, "\"parameters\":[]") {
		t.Fatalf("empty parameters should be []: %s", s)
	}
}
