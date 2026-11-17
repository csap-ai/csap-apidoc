package tag

import (
	"reflect"
	"testing"
)

type s struct {
	A string `json:"a,omitempty" doc:"标题" doc_example:"x" doc_position:"2"`
	B string `json:"-"`
	C string `json:"-,"`
	D string `binding:"min=1,required" uri:"d"`
	E string `in:"header" header:"X-E"`
	F string `doc:"f" doc_desc:"详细" doc_hidden:"true" doc_force:"1" form:"f,default=1"`
}

func TestParse(t *testing.T) {
	typ := reflect.TypeOf(s{})
	get := func(i int) Meta { return Parse(typ.Field(i)) }
	if m := get(0); m.Name != "a" || !m.HasJSON || m.Value != "标题" || m.Description != "标题" || m.Example != "x" || m.Position != 2 {
		t.Fatalf("A: %+v", m)
	}
	if get(1).Name != "-" || get(2).Name != "-" {
		t.Fatal(`json:"-" skip, json:"-," means field named "-"`)
	}
	if m := get(3); !m.Required || m.URI != "d" || m.Name != "D" {
		t.Fatalf("D: %+v", m)
	}
	if m := get(4); m.In != "HEADER" || m.Header != "X-E" {
		t.Fatalf("E: %+v", m)
	}
	if m := get(5); m.Description != "详细" || !m.Hidden || !m.Force || m.Form != "f" {
		t.Fatalf("F: %+v", m)
	}
}
