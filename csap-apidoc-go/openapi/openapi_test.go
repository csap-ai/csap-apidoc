package openapi

import (
	"testing"

	"github.com/csap-ai/csap-framework-apidoc/csap-apidoc-go/model"
)

func TestBuild(t *testing.T) {
	obj := model.ModelObject
	base := model.ModelBaseData
	doc := &model.Response{
		ApiInfo: model.DefaultApiInfo(),
		ApiList: []*model.Controller{{
			Value: "用户",
			MethodList: []*model.Method{{
				Key: "U.get", Value: "查询", Methods: []string{"GET"}, ApiPath: []string{"/users"}, Paths: []string{"/{id}"},
				Request: []*model.Model{{ModelType: model.ModelBaseData, Parameters: []*model.Parameter{
					{Name: "id", DataType: "Long", ParamType: model.ParamPath, Value: "ID"},
				}}},
				Response: []*model.Model{{ModelType: model.ModelTObject, Parameters: []*model.Parameter{
					{Name: "code", DataType: "String", ModelType: &base, Required: true},
					{Name: "data", DataType: "User", ModelType: &obj, Children: &model.Model{Parameters: []*model.Parameter{
						{Name: "tags", DataType: "List<String>", ModelType: &base},
					}}},
				}}},
			}, {
				Key: "U.create", Value: "新增", Methods: []string{"POST"}, ApiPath: []string{"/users"}, Status: model.StatusDeprecated,
				Request: []*model.Model{{ModelType: model.ModelObject, Parameters: []*model.Parameter{
					{Name: "name", DataType: "String", ParamType: model.ParamBody, Required: true,
						Validate: []model.ValidateAttribute{{Type: "Pattern", Pattern: "^[a-z]+$"}}},
				}}},
			}},
		}},
	}
	spec := Build(doc)
	paths := spec["paths"].(map[string]any)
	get := paths["/users/{id}"].(map[string]any)["get"].(map[string]any)
	p := get["parameters"].([]any)[0].(map[string]any)
	if p["in"] != "path" || p["required"] != true || p["schema"].(map[string]any)["format"] != "int64" {
		t.Fatalf("param: %v", p)
	}
	s := get["responses"].(map[string]any)["200"].(map[string]any)["content"].(map[string]any)["application/json"].(map[string]any)["schema"].(map[string]any)
	data := s["properties"].(map[string]any)["data"].(map[string]any)
	tags := data["properties"].(map[string]any)["tags"].(map[string]any)
	if tags["type"] != "array" || tags["items"].(map[string]any)["type"] != "string" {
		t.Fatalf("tags schema: %v", tags)
	}
	if req := s["required"].([]string); len(req) != 1 || req[0] != "code" {
		t.Fatalf("required: %v", req)
	}
	post := paths["/users"].(map[string]any)["post"].(map[string]any)
	if post["deprecated"] != true {
		t.Fatal("deprecated")
	}
	body := post["requestBody"].(map[string]any)["content"].(map[string]any)["application/json"].(map[string]any)["schema"].(map[string]any)
	name := body["properties"].(map[string]any)["name"].(map[string]any)
	if name["pattern"] != "^[a-z]+$" {
		t.Fatalf("pattern: %v", name)
	}
}
