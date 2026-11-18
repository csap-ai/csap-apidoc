# 返回字段过滤

1.2.0 起，`csap-apidoc-boot-starter` 带有运行时返回字段过滤（模块 `csap-apidoc-runtime`）：开启后，配置了返回规则的接口，实际返回的 JSON 只包含规则放行的字段，与文档显示的返回字段一致。**默认关闭。**

:::info 适用范围
本页讲的是 **Java 版**的返回字段过滤(`csap-apidoc-runtime`)。Go 版有对应功能(`ResponseFilter` 中间件、`FilterMode`、`FilterEndpoints`、`ShouldFilter`,v0.2.0 起还有按响应体判断的 `ShouldFilterBody`),注册 `ResponseFilter` 中间件后,配了返回规则的接口才会过滤,见 [Go 版](/go/)。
:::

## 开启

```yaml
csap:
  apidoc:
    runtime:
      filter:
        enabled: true
```

不需要其他改动。Spring Boot 2 和 3 使用同一个 jar。只支持 Servlet 栈的 Spring MVC；WebFlux 应用里开启也不会生效，也不会报错。

## 哪些接口会被过滤

只过滤**配置了返回规则**的接口：该接口在当前参数策略（注解 `@Group`、YAML、SQLite）里有返回字段的条目。没有返回规则的接口原样输出。

规则来自文档生成，规则的写法见 [存储策略与返回过滤](/guide/storage-strategies#返回字段过滤)。应用启动后文档异步生成，生成完成之前的请求不过滤，日志里会打印一次提示。

规则在 DevTools 里修改并保存后，下一个请求即按新规则输出，不需要重启。

## 两种模式

```yaml
csap:
  apidoc:
    runtime:
      filter:
        mode: path   # path(默认)或 csap
```

### path（默认）

按字段路径放行：只输出放行的字段（规则 `include: true` 的字段和 `forceRep` 字段）以及它们的上级字段，其余去掉。

- 输出的字段就是文档里显示的返回字段；
- 放行的字段如果没有任何下级字段被放行，它的值整个输出；
- 数组、集合按元素逐个过滤；
- 字段名同时认驼峰和下划线写法（规则写 `leagueId`，JSON 是 `league_id` 也能匹配）。

### csap

兼容 csap-framework 原来的过滤方式，从 csap-framework 迁移、要求输出与原来一致时使用：

- 规则按"类型"记录：每个类型一个白名单和一个黑名单，同一类型出现在多个位置时规则合并；
- 先查黑名单，命中则不输出；白名单不为空时只输出白名单里的字段；白名单为空时输出黑名单之外的全部字段；
- 不会自动放行上级字段：上级字段没有放行，下级的规则不起作用。

csap 模式下，JSON 字段名与 Java 字段名不一致时，需要配置 `csap.apidoc.response-type`，见 [配置参考](/guide/configuration#扫描与字段处理)。

## 按接口覆盖

```yaml
csap:
  apidoc:
    runtime:
      filter:
        mode: path
        endpoints:
          "[com.example.UserController.get]": "off"
          "[com.example.OrderController.list]": csap
```

键是"类名.方法名"，YAML 里要用 `"[...]"` 包住（键里有点号）。值是 `off`、`path` 或 `csap`。同名的重载方法共用一份规则和配置。

## JSON 库

```yaml
csap:
  apidoc:
    runtime:
      filter:
        engine: auto   # auto(默认)| jackson | fastjson | fastjson2
```

`auto` 按接口实际使用的消息转换器选择：

| 消息转换器 | 引擎 |
|---|---|
| Spring 默认的 Jackson（`MappingJackson2HttpMessageConverter` 等 JSON 格式的 Jackson 转换器） | jackson |
| fastjson 1.x 的 `com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter` | fastjson |
| fastjson2 的 `FastJsonHttpMessageConverter`（`fastjson2-extension-spring5` / `spring6`） | fastjson2 |

指定某个引擎后，只过滤用该库输出的响应。其他消息转换器（如 Gson、XML）输出的响应不过滤，日志里对每种转换器提示一次。

Jackson 下，过滤使用消息转换器 ObjectMapper 的一个副本，原 ObjectMapper 不做修改；`@JsonView`、自定义 `@JsonFilter` 照常生效。

## 外层统一包装

项目里常用 `ResponseBodyAdvice` 把返回值包进统一结构（如 `{code, message, data}`）。过滤只作用于控制器方法的返回值那一部分，外层结构原样输出。返回类型本身就是包装类型（如 `ApiResult<User>`）时，包装类型的字段也按规则过滤，需要始终输出的字段标 `forceRep = true`。

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `csap.apidoc.runtime.filter.enabled` | `false` | 是否开启 |
| `csap.apidoc.runtime.filter.mode` | `path` | `path` 或 `csap` |
| `csap.apidoc.runtime.filter.engine` | `auto` | `auto`、`jackson`、`fastjson`、`fastjson2` |
| `csap.apidoc.runtime.filter.endpoints` | 空 | 按接口覆盖模式，键为"类名.方法名"，值为 `off`、`path`、`csap` |

## 限制

- 文档对 `Map` 类型字段不展开值的字段，所以 Map 字段放行后整个值输出。
- fastjson / fastjson2 下，放行字段的值如果是对象或集合，字段上的 `@JSONField(serializeUsing / format)` 不再生效（对象所属类上的配置仍然生效）。
- 有规则的接口序列化会稍慢：过滤不再打开 JSON 库的逐字段过滤器，而是按规则节点和类型预编译只含放行字段的序列化器并复用。100 行、约 17 KB 的返回体（JDK 17）：典型规则下三种 JSON 库的耗时与不过滤基本相同（只放行部分字段时输出变小，可能更快）；规则放行全部字段时约多 24%～32%。没有规则的接口不受影响。
- 以下情况整次响应改用较慢的逐字段过滤器（输出相同）：消息转换器配置了其他过滤器；fastjson2 开启了 ReferenceDetection、WriteClassName、BeanToArray、PrettyFormat 或 WriteNullListAsEmpty 等空值写法特性；fastjson 1.x 开启了 WriteClassName、BeanToArray、PrettyFormat。由自定义序列化器写出的值也按原来的方式过滤。

## 相关页面

- [存储策略与返回过滤](/guide/storage-strategies)
- [DevTools](/guide/devtools)
- [配置参考](/guide/configuration)
