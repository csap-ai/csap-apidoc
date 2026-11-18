# 存储策略与返回过滤

本页说明字段规则的三种来源（注解、YAML、SQLite）如何配置、文件格式是什么、怎样选择，以及这些规则如何决定接口文档里出现哪些请求和返回字段。

## 什么是字段规则

接口、实体、字段的说明文字始终来自注解（`@Api`、`@ApiOperation`、`@ApiModelProperty` 等，见 [注解使用指南](/guide/annotations)）。**字段规则**回答的是另一个问题：在某个接口里，实体的哪些字段作为请求参数、哪些作为返回字段，是否必填，参数位置是什么，带哪些校验规则。

同一个 `User` 实体，在 `createUser` 里需要 `username`、`password`，在 `getUser` 里只返回 `id`、`username`——这就是字段规则要描述的内容。

字段规则的来源由 `@EnableApidoc` 的 `paramType` 决定：

```java
@SpringBootApplication
@EnableApidoc(apiPackages = "com.example.controller", paramType = ApiStrategyType.YAML)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

| `paramType` | 规则来源 | DevTools 能否写回 |
|-------------|----------|-------------------|
| `ANNOTATION`（默认） | `@ApiModelProperty(groups = ...)` | 不能 |
| `YAML` | classpath 下的 `*-method-field.yaml` 文件 | 能 |
| `SQL_LITE` | classpath 下的 SQLite 数据库文件 | 能 |

YAML 和 SQLite 的实现都已经包含在 `csap-apidoc-boot-starter` 中（它传递依赖 `csap-apidoc-yaml`、`csap-apidoc-sqlite`），不需要额外引入。

:::warning type 与 paramType 的区别
`@EnableApidoc` 还有一个 `type` 属性（文档主体来源），和 `paramType` 不是一回事。选择规则来源请改 `paramType`，`type` 一般保持默认 `ANNOTATION`。

- 1.1.1 起 `type = YAML` / `JSON` 会注册对应的文档策略（1.1.0 中没有注册，文档静默为空）。`JSON` 策略在 1.1.1 及以前写出的 `.json` 文件内容其实是 YAML 格式；1.2.0 起写出真正的 JSON（带缩进），读取时仍能识别旧的 YAML 内容（文件以 `{` 或 `[` 开头按 JSON 解析，否则按 YAML 解析），旧文件下次被 DevTools 写入时会自动改写成 JSON。
- `type = SQL_LITE` 没有可用的实现，1.1.1 起启动时直接报错，并提示改用 `paramType = SQL_LITE`。
- `paramType = JSON` 目前没有实现。
:::

## 字段如何进入文档

不论使用哪种策略，框架处理实体的每个字段时都按下面的顺序判断：

1. 字段没有 `@ApiModelProperty`，或 `hidden = true`：跳过。
2. 请求参数中 `ignoreReq = true`、返回参数中 `ignoreRep = true`：跳过。
3. 请求参数中 `forceReq = true`、返回参数中 `forceRep = true`：**直接出现**，不需要规则（规则里如果也有这个字段，必填、参数位置、校验仍从规则取）。方法名匹配 `csap.apidoc.result` 的分页规则时，其中配置的分页字段同样直接出现（见 [配置](/guide/configuration)）。
4. 否则查当前策略的规则：
   - 请求参数：存在该字段的规则就出现；
   - 返回参数：存在该字段的规则**且** `include` 为 `true` 才出现。
5. 都不满足：不出现。

字段是对象、`List` 或 `Map` 时，只有它自己出现了，框架才会继续处理它的子字段。基础类型的方法参数（如 `@PathVariable Long id`）不经过这套判断，总是出现在请求参数中。

规则命中后，各项属性按以下优先级取值：

| 属性 | 取值顺序（前者优先） |
|------|----------------------|
| 请求字段的 `required` | 规则 > `@ApiModelProperty.required` |
| 返回字段的 `required` | 规则 > `@ApiModelProperty.required` |
| 参数位置 | 规则中的 `paramType` > `@ApiOperation.paramType`（非 `DEFAULT` 时；有 `@RequestBody` 时视为 `BODY`）> `@ApiModelProperty.paramType` |
| 基础类型参数的说明 | 规则中的 `description` > `@ApiProperty.description` |
| 校验规则 | YAML / SQLite：规则中的 `validate` 与字段上的 JSR-303 注解**合并**，按 `level` 升序排列；注解策略：只有 JSR-303 注解 |

三种策略之间**不合并**：选了 YAML，框架就不读 `@Group`；选了注解，就不读 YAML 文件。

## 注解策略

`paramType = ApiStrategyType.ANNOTATION`（默认）。规则写在 `@ApiModelProperty.groups` 里，每个 `@Group` 对应一个方法：

```java
@ApiModelProperty(value = "用户名", groups = {
        @Group(value = "createUser", request = @Request(required = true)),
        @Group(value = "getUser", response = @Response)
})
private String username;
```

`@Group.value` 匹配方法名（或 `Controller简单类名.方法名`）。详细规则见 [注解使用指南 - 按接口区分字段](/guide/annotations#按接口区分字段groups)。

适合字段少、规则稳定、希望规则和代码放在一起的项目。缺点是同一实体被很多接口使用时，注解会很长，而且 DevTools 不能修改注解。

## YAML 策略

### 开启

```java
@EnableApidoc(apiPackages = "com.example.controller", paramType = ApiStrategyType.YAML)
```

可选属性：

- `path`：规则目录，默认 `csap-api-doc`。支持 `${property:default}` 占位符。
- `prefixStrategy`：文件命名方式，默认 `FilePrefixStrategyType.DEFAULT`。

### 文件位置与命名

框架从 classpath 读取 `<path>/method-field/` 目录下的文件：

| `prefixStrategy` | 读取 | DevTools 写入 |
|------------------|------|---------------|
| `DEFAULT` | `classpath*:csap-api-doc/method-field/*-method-field.yaml` | 每个 Controller 一个文件：`<Controller简单类名>-method-field.yaml` |
| `FIXED` | `classpath*:csap-api-doc/method-field/api-method-field.yaml` | 全部写入 `api-method-field.yaml` |

文件名必须以 `-method-field.yaml` 结尾，扩展名只认 `.yaml`：写成 `.yml` 或其他名字不会被读取。

在 Maven 项目里，文件通常放在 `src/main/resources/csap-api-doc/method-field/`。

规则在应用启动生成文档时读取并缓存。手工修改 YAML 后需要重启应用；通过 DevTools 修改会同时更新缓存和文件。

### 文件格式

文件是三层结构：**Controller 全限定类名 → 方法名 → `request` / `response` → 字段键**。字段键的写法：

| 位置 | 字段键 | 示例 |
|------|--------|------|
| 请求体中的实体字段 | `<方法参数名>.<字段名>` | `user.username` |
| 嵌套字段 | 逐层用 `.` 连接 | `user.address.city` |
| 基础类型的方法参数 | `<参数名>.<参数名>` | `id.id` |
| 返回值的字段 | `<字段名>` | `code` |
| 返回值的嵌套字段 | 逐层用 `.` 连接 | `data.username` |

字段名指 `@ApiModelProperty.name`，没填时是 Java 字段名。

每个字段键下的属性：

| 属性 | 类型 | 说明 |
|------|------|------|
| `required` | boolean | 是否必填，默认 `false`。**请求字段只有 `required: true` 时，`validate` 才会登记到运行时校验** |
| `include` | boolean | 仅 `response` 使用：是否出现在返回参数中 |
| `paramType` | 枚举 | 仅 `request` 使用：`PATH`、`QUERY`、`BODY`、`FORM_DATA`、`HEADER`、`FORM` |
| `description` | string | 仅对基础类型的方法参数生效，覆盖 `@ApiProperty.description` |
| `validate` | list | 仅 `request` 使用：校验规则列表 |

`validate` 中每条规则的属性：

| 属性 | 说明 |
|------|------|
| `type` | `NotNull`（不能为 null）、`NotEmpty`（不能为空，null、空字符串、空集合都算空）、`Pattern`（正则） |
| `pattern` | `type: Pattern` 时的正则表达式，整串匹配 |
| `code` | 校验失败时返回的 `code` |
| `message` | 校验失败时的提示 |
| `level` | 执行顺序，数值越小越先执行，默认 `1` |
| `descr` | 规则说明，供 DevTools 展示 |
| `tipType` | 这条规则的提示格式，覆盖全局的 `csap.validation.tip-type`，取值见 [参数校验](/guide/validation#错误返回格式) |

### 完整示例

对应 [注解使用指南](/guide/annotations) 中的 `UserController`（包名 `com.example.controller`），文件 `src/main/resources/csap-api-doc/method-field/UserController-method-field.yaml`：

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
            descr: 用户名格式
      user.password:
        required: true
        paramType: BODY
        validate:
          - type: Pattern
            level: 1
            pattern: '^.{8,32}$'
            code: "1003"
            message: 长度必须在8-32位之间
            tipType: TIP_3
    response:
      data.id:
        include: true
      data.username:
        include: true
  getUser:
    request:
      id.id:
        required: true
        paramType: PATH
        description: 用户ID，大于0
        validate:
          - type: NotNull
            level: 1
            code: "1001"
            message: 不能为空
    response:
      data.id:
        include: true
      data.username:
        include: true
```

说明：

- `createUser(@RequestBody User user)` 的参数名是 `user`，所以请求字段键以 `user.` 开头。
- 返回类型是 `ApidocResult<User>`，它的 `code`、`message`、`data` 等字段标了 `forceRep`，不需要写规则；`data` 里的 `User` 字段需要逐个写 `include: true`。
- `User.username` 上如果还有 `@NotBlank` 等 JSR-303 注解，会和这里的 `validate` 合并执行。
- 读取 YAML 时会忽略未知属性，拼错的属性名不会报错，只会不生效。

### 写入时的注意事项

DevTools 写 YAML 时，目标路径是把 classpath 根目录中的 `target/classes` 替换成 `src/main/resources` 得到的。也就是说：

- 在 IDE 或 `mvn spring-boot:run` 中运行时，文件写回源码目录，可以直接提交到版本库；
- 其他目录结构（如 Gradle 的 `build/classes/java/main`）不会被替换，文件会写到构建输出目录里，重新构建时会丢失。

## SQLite 策略

### 开启

```java
@EnableApidoc(apiPackages = "com.example.controller",
              paramType = ApiStrategyType.SQL_LITE,
              path = "csap-api-doc",
              fileName = "apidoc")
```

框架读取 `classpath*:<path>/<fileName>.db`，即默认的 `csap-api-doc/apidoc.db`。`fileName` 为空时使用 `apidoc`。

### 表结构

规则存放在 `api_method` 表中，框架读写以下列：

| 列 | 内容 |
|----|------|
| `class_name` | Controller 全限定类名 |
| `simple_name` | Controller 简单类名 |
| `name` | 方法名 |
| `request` | 请求规则，JSON 字符串 |
| `response` | 返回规则，JSON 字符串 |
| `create_time` / `update_time` | 写入时间 |

`request`、`response` 两列的 JSON 结构与 YAML 文件中某个方法下的 `request`、`response` 完全相同（字段键 → `required` / `include` / `paramType` / `description` / `validate`）。

### 运行时的文件位置

- 在 IDE 中以目录形式运行时，框架直接使用 `src/main/resources` 下的数据库文件，DevTools 的修改会写回这个文件。
- 打成 jar 运行时，框架把数据库复制到系统临时目录（`java.io.tmpdir`）后读取。

规则在启动时读取一次并缓存。

:::tip 数据库文件会自动创建
1.1.1 起不需要手工准备数据库：数据库文件不存在时，读取规则得到空结果（不报错）；DevTools 第一次写入规则时会自动创建 `<path>/<fileName>.db` 并建好 `api_method` 表，打开连接时也会确保表存在。1.1.0 依赖一个没有随发行包提供的模板库，文件不存在时会出错。
:::

:::tip 旧模板建的库会自动迁移（1.2.0 起）
用旧模板库建出来的 `api_method` 表，`controller_id`、`info_id`、`value`、`key`、`group`、`version` 等列是 `NOT NULL` 且没有默认值，`key` 上还有唯一索引，而框架插入时不写这些列，DevTools 写入会失败。

1.2.0 起，框架每次打开数据库连接时都会检查 `api_method` 表：只要存在没有默认值的 `NOT NULL` 列（主键除外），或者 `key` 上有唯一索引，就在一个事务里迁移——按新的表结构建临时表、拷贝全部数据（`id` 不变，原来为 `NULL` 的列按新结构的默认值补齐）、删除旧表、改名，再重建原有的非唯一索引。旧表中新结构没有的列会保留下来。迁移后的表不再满足检查条件，所以只会迁移一次。

迁移会直接修改数据库文件：在 IDE 中运行时改的是 `src/main/resources` 下的文件，打成 jar 运行时改的是复制到临时目录的副本。重要数据请先备份。

1.1.1 只在表不存在时建表，不会改已有的表结构；仍在使用 1.1.1 时，请删除旧的 `.db` 文件，让框架重新创建（需要保留的规则请先导出 `request`、`response` 两列）。
:::

## 如何选择

| 场景 | 建议 |
|------|------|
| 接口少、实体专用，规则很少变化 | 注解：规则跟着代码走，不需要额外文件 |
| 实体被多个接口复用，需要按接口裁剪字段；希望用 DevTools 勾选字段并在代码评审中看到改动 | YAML：文本文件，便于 diff 和合并 |
| 希望把规则集中在单个文件里，且团队不需要逐行审阅规则改动 | SQLite：单个二进制文件，不便于 diff |

无论哪种策略，`forceReq` / `forceRep`、`ignoreReq` / `ignoreRep`、`hidden` 等写在注解上的开关都会生效，可以把"所有接口都要的字段"放在注解上，把"按接口变化的字段"放在规则里。

## 返回字段过滤

### 规则决定文档里的返回字段

按 [字段如何进入文档](#字段如何进入文档) 的规则，某个接口的返回参数只包含：

- 标了 `forceRep = true` 的字段；
- 方法名匹配 `csap.apidoc.result` 分页规则时，其中配置的分页字段；
- 当前策略中该方法 `response` 下有条目、且 `include` 为 `true` 的字段（注解策略下是 `@Group` 带 `response = @Response` 的字段）。

其余字段（包括 `ignoreRep = true` 的字段）不出现在文档的返回参数中。

### 运行时记录的字段清单

生成文档的同时，框架会把每个方法的返回字段清单登记到校验模块的 `IValidateFactory` 中：按"方法 → 实体类"分组，进入文档的字段记为 include，经过规则判断但未进入的字段记为 exclude（`hidden`、`ignoreRep` 的字段直接跳过，两边都不登记）。配置了 `csap.apidoc.response-type` 时，include 清单还会额外加入转换后的字段名：

| `csap.apidoc.response-type` | 额外加入的名字 |
|-----------------------------|----------------|
| `none`（默认） | 无 |
| `toCamelCase` | 含下划线的字段，加入驼峰写法（`user_name` → `userName`） |
| `toUnderlineCase` | 驼峰字段，加入下划线写法（`userName` → `user_name`） |

这份清单可以通过 `IValidateFactory#response(Class<?> controller, Method method)` 取得，返回 `List<FilterClassParam>`，每项包含实体类型 `type`、`includes`、`excludes`。前提是：

- 引入了 `csap-validation-boot-starter`（`IValidateFactory` Bean 由它注册；不引入时文档 starter 内部使用一个默认实现，文档照常生成，但它不是 Bean，无法注入）；
- 文档已经生成。文档在应用启动完成后异步生成。

:::warning 1.1.x 不会改写实际的响应 JSON
1.1.x 只登记上述清单，**没有**任何组件在序列化响应时使用它，`@JsonFieldFilter` / `@JsonFieldFilters` 也不再被读取。接口实际返回的 JSON 仍由你的序列化配置（如 Jackson）决定，与文档中的返回字段可能不一致。

1.2.0 起可以开启运行时返回字段过滤，让实际响应与文档一致，见 [返回字段过滤](/guide/runtime-filter)。
:::

## 相关页面

- [注解使用指南](/guide/annotations)
- [参数校验](/guide/validation)：`validate` 规则如何执行
- [DevTools](/guide/devtools)：可视化编辑 YAML / SQLite 规则
- [配置](/guide/configuration)：`csap.apidoc.response-type`、`csap.apidoc.result` 等配置项
