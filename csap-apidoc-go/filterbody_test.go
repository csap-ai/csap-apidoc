package apidoc

import "testing"

func TestSuccessField(t *testing.T) {
	cases := []struct {
		name string
		path string
		want any
		body string
		out  bool
	}{
		{"bool true", "success", true, `{"success":true}`, true},
		{"bool false", "success", true, `{"success":false,"data":1}`, false},
		{"int ok", "code", 0, `{"code":0}`, true},
		{"int fail", "code", 0, `{"code":500}`, false},
		{"float eq int", "code", 200, `{"code":200.0}`, true},
		{"string code ok", "code", "0000", `{"code":"0000"}`, true},
		{"string code fail", "code", "0000", `{"code":"E1"}`, false},
		{"nested", "result.code", 1, `{"result":{"code":2}}`, false},
		{"missing field -> filter", "success", true, `{"other":1}`, true},
		{"not json -> filter", "success", true, `plain text`, true},
		{"array body -> filter", "success", true, `[1,2]`, true},
		{"null field -> filter", "success", true, `{"success":null}`, true},
	}
	for _, c := range cases {
		if got := SuccessField(c.path, c.want)(nil, 200, []byte(c.body)); got != c.out {
			t.Errorf("%s: got %v want %v", c.name, got, c.out)
		}
	}
}
