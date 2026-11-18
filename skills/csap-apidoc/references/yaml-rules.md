# YAML 规则文件(*-method-field.yaml)

Java 和 Go **共用同一种规则文件格式**:Java 项目的规则文件可以直接给 Go 服务用,反过来也一样。不同的只是文件放哪、怎么加载。

## 位置与加载

| | Java | Go |
|---|---|---|
| 前提 | `@EnableApidoc(apiPackages = "...", paramType = ApiStrategyType.YAML)` | `apidoc.Options.Strategies` 里加 `yaml.Dir(目录)`(或 `yaml.FS` 只读加载) |
| 默认目录 | `src/main/resources/csap-api-doc/method-field/`(classpath `csap-api-doc/method-field/*-method-field.yaml`);`@EnableApidoc(path = "...")` 可改根目录 | 自己指定,惯例同样用 `csap-api-doc/method-field` |
| 文件名 | 必须以 `-method-field.yaml` 结尾,扩展名只认 `.yaml`(`.yml` 不会被读取);`prefixStrategy = FIXED` 时全部写在 `api-method-field.yaml` | 目录下所有 `.yaml` / `.yml` 都加载,文件名不限 |
| 同一分组出现在多个文件 | 只认一个,不要重复写 | 同一分组出现在多个文件里时的合并规则见 `strategy/yaml/yaml.go` |
| 修改后生效 | 手工改 YAML 需重启;DevTools 修改即时生效 | `doc.Watch(ctx)` 监听,文件改了自动重新加载;DevTools 修改即时生效 |

两边共用同一份文件时,按 Java 的要求命名(`<名字>-method-field.yaml`)。

## 结构

`分组(Java 是 Controller 全限定类名;Go 是 apidoc.Op 的分组名)→ 方法名(Go 是接口名)→ request / response → 字段键 → 属性`

字段键:

| 位置 | 写法 | 示例 |
|---|---|---|
| 请求体实体字段 | `<方法参数名>.<字段名>` | `user.username` |
| 嵌套字段 | 逐层用 `.` | `user.address.city` |
| 基础类型方法参数 | `<参数名>.<参数名>` | `id.id` |
| 返回字段 | `<字段名>` | `code` |
| 返回嵌套字段 | 逐层用 `.` | `data.rows.id` |

Java:字段名是 `@ApiModelProperty.name`,没写时用 Java 字段名(驼峰)。Go:请求体字段的前缀默认是 `request`(`Body(...)` 的 `BodyName` 可改),返回字段在 `data.` 下(有外层包装时);路径同时认驼峰和下划线写法。其余键的写法两边一致。

属性:

| 属性 | 用于 | 说明 |
|---|---|---|
| `required` | request / response | 是否必填,默认 false。Java:请求字段只有 `required: true` 时 `validate` 才会登记和执行;Go:没写 `required` 的字段,请求里传了值也会按 `validate` 校验(当前 Java 1.2.0 / 1.3.0 与 Go 的差别;已定 1.4.0 起 Java 向 Go 对齐) |
| `include` | response | `true` 才在返回参数中显示 |
| `paramType` | request | `PATH` `QUERY` `BODY` `FORM_DATA` `HEADER` `FORM` |
| `description` | request | 只对基础类型方法参数生效 |
| `validate` | request | 校验规则列表 |

`validate` 每项:`type`(两边都有 `NotNull` / `NotEmpty` / `Pattern`;Go 另有 `NotBlank`、`Min`、`Max`、`Length` / `Size`、`Enum`,Java 没有这些类型,不要写进 Java 也要用的文件)、`pattern`(正则,整串匹配)、`code`、`message`、`level`(越小越先执行)、`descr`、`tipType`。

## 示例

```yaml
com.example.controller.UserController:
  createUser:
    request:
      user.username:
        required: true
        paramType: BODY
        validate:
          - type: NotEmpty
            level: 1
            code: "1001"
            message: 不能为空
          - type: Pattern
            level: 2
            pattern: '^[A-Za-z0-9]{4,20}$'
            code: "1002"
            message: 只能是4-20位字母或数字
    response:
      data:
        include: true
      data.id:
        include: true
      data.username:
        include: true
  getUser:
    request:
      id.id:
        required: true
        paramType: PATH
        description: 用户ID
    response:
      data:
        include: true
      data.id:
        include: true
```

注意:

- 上级字段本身没有 `forceRep` 时也要写 `include: true`(上例的 `data`,假设它是自定义包装类的字段),否则下级不会出现;用框架自带的 `ApidocResult` 时 `data` 已是 `forceRep`,可以省略。
- `code` 用字符串写法 `"1001"`。
- Go 版新增的 `hidden`(只在文档隐藏)、`example` 属性,Java 读取时会忽略。
- Java 手工修改后重启应用生效,Go 监听文件自动生效;用 DevTools 修改两边都会即时生效并写回文件。
