# 接口状态(开发中 / 完成 / 过时)

约定见仓库 [docs/design/1.4.0-api-status.md](../../docs/design/1.4.0-api-status.md)。状态:`DEFAULT`(开发中)、`FINISH`(完成)、`DEPRECATED`(过时)。

- **有效状态**:规则存储里的 `status` > 代码里声明的状态(`Op(...).Deprecated()`,不是 DEFAULT 时)> `DEFAULT`。规则里写 `status: DEFAULT` 可以覆盖代码里的"过时"。不认识的值按没有处理。
- **规则格式**:状态和备注存在接口的规则里,与 `request`、`response` 并列,各种存储通用:

  ```yaml
  example.UserController:
    list:
      status: DEPRECATED
      statusNote: 请改用 /v2/users
  ```

  规则为空的判定变了:**只有状态、没有字段规则的接口不会被当成空规则删除**。
- **文档**:有效状态写入每个接口的 `status`,备注写入 `statusNote`(没有时不输出)。
- **OpenAPI**:过时接口输出 `deprecated: true`;非 DEFAULT 的状态输出 `x-csap-status`;备注追加到 `description` 末尾。**Postman**:过时接口名称前加 `[过时] `。
- **DevTools**:`POST /csap/yaml/setStatus {"className","methodName","status","statusNote"}`,`status` 传 `null` 清除覆盖;有修改记录、可回滚。
- 升级提示:旧版本(v0.1.x)读到 `status` 会忽略;旧版本重写某接口的字段规则时会丢掉它的状态,多实例混跑旧版本时请一次升完。
