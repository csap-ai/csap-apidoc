package postman

import (
	"bytes"
	"encoding/json"
	"os"
	"testing"

	"github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"
)

// TestGoldenJavaExample 用 Java 示例工程(example/example-spring-boot2)的实时输出做黄金样本:
// testdata/example-boot2-apidoc.json 是 GET /csap/apidoc 的返回,
// testdata/example-boot2-postman.json 是同一实例 GET /csap/openapi/postman 的返回。
// Go 用前者生成的 Postman Collection 必须与后者逐字节相同。
func TestGoldenJavaExample(t *testing.T) {
	src, err := os.ReadFile("testdata/example-boot2-apidoc.json")
	if err != nil {
		t.Fatal(err)
	}
	want, err := os.ReadFile("testdata/example-boot2-postman.json")
	if err != nil {
		t.Fatal(err)
	}
	var wrap struct {
		Data *model.Response `json:"data"`
	}
	if err := json.Unmarshal(src, &wrap); err != nil {
		t.Fatal(err)
	}
	got, err := Build(wrap.Data)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, want) {
		i := 0
		for i < len(got) && i < len(want) && got[i] == want[i] {
			i++
		}
		lo := max(i-80, 0)
		t.Fatalf("与 Java 输出不同,首个差异在第 %d 字节:\nGo:   %q\nJava: %q", i,
			got[lo:min(i+80, len(got))], want[lo:min(i+80, len(want))])
	}
}
