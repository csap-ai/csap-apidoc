package strategy

import "testing"

func TestMerge(t *testing.T) {
	tr, fa := true, false
	a := Rules{"G.m": {Response: map[string]FieldRule{"x": {Include: &tr, Description: "a"}, "y": {Include: &tr}}}}
	b := Rules{"G.m": {Response: map[string]FieldRule{"x": {Include: &fa}}, Request: map[string]FieldRule{"r.r": {ParamType: "QUERY"}}}}
	m := Merge(a, b)["G.m"]
	if *m.Response["x"].Include || m.Response["x"].Description != "a" || !*m.Response["y"].Include {
		t.Fatalf("merge: %+v", m.Response)
	}
	if m.Request["r.r"].ParamType != "QUERY" {
		t.Fatal("request rules lost")
	}
	if *a["G.m"].Response["x"].Include != true {
		t.Fatal("Merge must not mutate inputs")
	}
}
