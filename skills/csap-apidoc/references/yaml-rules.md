# YAML 规则文件(*-method-field.yaml)

需要 `@EnableApidoc(apiPackages = "...", paramType = ApiStrategyType.YAML)`。

## 位置

- 默认:`src/main/resources/csap-api-doc/method-field/<Controller简单类名>-method-field.yaml`(classpath `csap-api-doc/method-field/*-method-field.yaml`)。
- `@EnableApidoc(path = "...")` 可改根目录(默认 `csap-api-doc`);`prefixStrategy = FilePrefixStrategyType.FIXED` 时全部写在 `api-method-field.yaml`。
- 文件名不要求和类名一致,但必须以 `-method-field.yaml` 结尾(扩展名只认 `.yaml`,`.yml` 不会被读取),框架读取目录下所有匹配文件;同一个 Controller 出现在多个文件里时只认一个,不要重复写。

## 结构

`Controller 全限定类名 → 方法名 → request / response → 字段键 → 属性`

字段键:

| 位置 | 写法 | 示例 |
|---|---|---|
| 请求体实体字段 | `<方法参数名>.<字段名>` | `user.username` |
| 嵌套字段 | 逐层用 `.` | `user.address.city` |
| 基础类型方法参数 | `<参数名>.<参数名>` | `id.id` |
| 返回字段 | `<字段名>` | `code` |
| 返回嵌套字段 | 逐层用 `.` | `data.rows.id` |

字段名是 `@ApiModelProperty.name`,没写时用 Java 字段名(驼峰)。

属性:

| 属性 | 用于 | 说明 |
|---|---|---|
| `required` | request / response | 是否必填,默认 false。请求字段只有 `required: true` 时 `validate` 才会登记 |
| `include` | response | `true` 才在返回参数中显示 |
| `paramType` | request | `PATH` `QUERY` `BODY` `FORM_DATA` `HEADER` `FORM` |
| `description` | request | 只对基础类型方法参数生效 |
| `validate` | request | 校验规则列表 |

`validate` 每项:`type`(`NotNull` / `NotEmpty` / `Pattern`)、`pattern`(正则,整串匹配)、`code`、`message`、`level`(越小越先执行)、`descr`、`tipType`。

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
- 手工修改后重启应用生效;用 DevTools 修改会即时生效并写回文件。
