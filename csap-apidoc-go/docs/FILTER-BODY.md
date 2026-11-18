# 按响应体判断是否过滤

默认情况下返回过滤只作用于 2xx 响应(`Options.ShouldFilter` 可按请求和状态码自定义)。有些接入方的业务失败也返回 HTTP 200,失败信息在外层包装里(`success=false`、`code≠0` 等)。Java 只过滤控制器正常返回的对象,失败体原样输出;Go 从 v0.2.0 起用 `Options.ShouldFilterBody` 做到同样的效果,不必再自写缓冲响应的中间件。

```go
doc := apidoc.New(apidoc.Options{
    Wrapper: &apidoc.Wrapper{Type: Result{}},
    // 响应体里 success 为 true 才裁剪;{"success":false,...} 原样输出
    ShouldFilterBody: apidoc.SuccessField("success", true),
})
```

## 钩子

```go
ShouldFilterBody func(r *http.Request, status int, body []byte) bool
```

- 响应已缓冲、裁剪之前调用;返回 `false` 时这次响应原样输出。`body` 是业务 handler 写出的原始字节,只读。
- **与 `ShouldFilter` 的顺序**:先 `ShouldFilter`(只看请求和状态码,粗筛),它返回 `true` 才会调用 `ShouldFilterBody`(再看响应体)。`ShouldFilter` 返回 `false` 时不会调用后者。两者都不设置时行为与 v0.1.1 完全相同(只裁剪 2xx)。
- gin 与 net/http 的 `ResponseFilter` 都支持;自定义适配器在裁剪前调用 `Doc.ShouldFilterBody(r, status, body)`。
- 只有配置了返回规则的接口才会缓冲响应、才会走到这里。

## 现成判断:`apidoc.SuccessField(path, want)`

按包装里某个字段判断成功,`path` 用点号分隔:

```go
apidoc.SuccessField("success", true)   // {"success":false,...} 不裁剪
apidoc.SuccessField("code", 0)         // {"code":500,...} 不裁剪;数字按值比较,0 与 0.0 相等
apidoc.SuccessField("code", "0000")    // 字符串编码
apidoc.SuccessField("result.code", 1)  // 嵌套字段
```

响应体不是 JSON、不是对象、没有这个字段或字段是 null 时,判断不了,按"裁剪"处理(和不设置钩子一样,不会因为格式不符而漏过滤)。更复杂的判断(多个字段、白名单错误码等)自己写钩子。
