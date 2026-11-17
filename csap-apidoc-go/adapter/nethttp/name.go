package nethttp

import "reflect"

// reflectPointer 返回函数值的入口地址,用于按函数名推断分组和接口名。
func reflectPointer(f any) uintptr {
	v := reflect.ValueOf(f)
	if v.Kind() != reflect.Func || v.IsNil() {
		return 0
	}
	return v.Pointer()
}
