package gin

import (
	"reflect"
	"runtime"
)

// nameOfFunction 返回函数全名,与 gin 内部 RouteInfo.Handler 的取法一致。
func nameOfFunction(f any) string {
	return runtime.FuncForPC(reflect.ValueOf(f).Pointer()).Name()
}
