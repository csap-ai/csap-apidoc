# 注解参考

本页列出 CSAP Apidoc 1.2.0 全部注解及其属性，内容按 `csap-apidoc-annotation` 模块的源码整理。用法和示例见 [注解使用指南](/guide/annotations)。

:::info 适用范围
本页是 **Java 版**的注解参考。Go 版没有注解,用结构体标签和 `apidoc.Op` 声明,见 [Go 版](/go/)。
:::

注解都在 `ai.csap.apidoc.annotation` 包下（`@EnableApidoc` 除外）。表格「说明」一栏标注「未读取」的属性，目前没有代码使用它，设置后不影响文档。

## 速查

| 注解 | 标注位置 | 作用 |
|------|----------|------|
| [@EnableApidoc](#enableapidoc) | 启动类 | 启用文档、指定扫描范围和字段规则来源 |
| [@Api](#api) | 类 | 声明 Controller |
| [@ApiOperation](#apioperation) | 方法 | 声明接口 |
| [@ApiProperty](#apiproperty) | 参数、方法 | 描述单个参数 |
| [@ApiPropertys](#apipropertys) | 方法 | `@ApiProperty` 容器 |
| [@ApiModel](#apimodel) | 类 | 声明实体类 |
| [@ApiModelProperty](#apimodelproperty) | 字段 | 描述实体字段 |
| [@Group](#group) | 注解属性 | 按方法声明字段规则 |
| [@Request](#request) | 注解属性 | `@Group` 的请求规则 |
| [@Response](#response) | 注解属性 | `@Group` 的返回规则 |
| [@Headers](#headers) | 注解属性 | 接口请求头 |
| [@DocGlobalHeader](#docglobalheader) | 类、方法、包 | 调试界面全局请求头提示 |
| [@DocAuth](#docauth) | 类、方法、包 | 调试界面鉴权方式提示 |
| [@ApiResponseCode](#apiresponsecode) | 类（枚举） | 返回码表 |
| [@EnumValue](#enumvalue--enummessage) | 枚举字段 | 枚举值字段 |
| [@EnumMessage](#enumvalue--enummessage) | 枚举字段 | 枚举说明字段 |
| [@DataTypeValidate](#datatypevalidate) | 参数、字段 | 类型转换失败时的返回码 |
| [@JsonFieldFilter](#jsonfieldfilter--jsonfieldfilters) | 方法 | 未读取 |
| [@JsonFieldFilters](#jsonfieldfilter--jsonfieldfilters) | 方法 | 未读取 |
| [@Description](#description) | 类、字段、方法 | 未读取 |
| [@RequestParamBody](#requestparambody) | 参数 | 未读取 |

## @EnableApidoc

包名 `ai.csap.apidoc.boot.autoconfigure`，位于 `csap-apidoc-boot-starter`。标在 Spring Boot 启动类（或任意配置类）上。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String[]` | `{}` | `apiPackages` 的别名 |
| `apiPackages` | `String[]` | `{}` | 扫描 Controller 的包，始终包含子包；支持 `*`、`**`、`?` 通配符 |
| `apiPackageClasses` | `Class<?>[]` | `{}` | 直接指定 Controller 类，只收录带 `@Api` 的类 |
| `type` | `ApiStrategyType` | `ANNOTATION` | 文档主体的来源，一般保持默认。`YAML`、`JSON` 从 1.1.1 起会注册对应策略；`SQL_LITE` 启动即报错，提示改用 `paramType = SQL_LITE` |
| `paramType` | `ApiStrategyType` | `ANNOTATION` | 字段规则的来源：`ANNOTATION`、`YAML`、`SQL_LITE`。`JSON` 目前没有实现。见 [存储策略](/guide/storage-strategies) |
| `requestType` | `String` | `"default"` | 请求参数解析器的名称，内置只有 `default` |
| `responseType` | `String` | `"default"` | 返回参数解析器的名称，内置只有 `default` |
| `path` | `String` | `"csap-api-doc"` | YAML / SQLite 规则文件所在的 classpath 目录；支持 `${property:default}` 占位符 |
| `fileName` | `String` | `""` | SQLite 数据库文件名（不含 `.db`），为空时为 `apidoc`；支持占位符。只对 `SQL_LITE` 生效 |
| `prefixStrategy` | `FilePrefixStrategyType` | `DEFAULT` | YAML 文件命名：`DEFAULT` 每个 Controller 一个文件；`FIXED` 全部写入 `api-` 前缀的单个文件 |
| `enumPackages` | `String[]` | `{}` | 扫描 `@ApiResponseCode` 枚举的包 |
| `enumPackageClasses` | `Class<?>[]` | `{}` | 直接指定 `@ApiResponseCode` 枚举类 |
| `modelPackages` | `String[]` | `{}` | 未读取（值会被保存，但没有代码使用） |
| `modelPackageClasses` | `Class<?>[]` | `{}` | 未读取 |
| `showChildPackageFlag` | `boolean` | `true` | 未读取。`apiPackages` 总是扫描子包 |

`ApiStrategyType` 的取值：`JSON`、`ANNOTATION`、`SQL_LITE`、`YAML`。

## @Api

标注位置：类（`@Inherited`）。框架通过 Spring 的 `AnnotationUtils.findAnnotation` 查找，写在接口或父类上也能识别。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | `""` | Controller 名称 |
| `description` | `String` | `""` | Controller 说明 |
| `position` | `int` | `0` | 排序位置 |
| `hidden` | `boolean` | `false` | 隐藏整个 Controller |
| `hiddenMethod` | `String[]` | `{}` | 按方法名隐藏方法 |
| `showMethod` | `String[]` | `{}` | 方法白名单；非空时只展示列表中的方法。与 `hiddenMethod` 同时包含某方法时，该方法被隐藏 |
| `tags` | `String[]` | `{}` | 标签，参与文档搜索 |
| `group` | `String[]` | `"default"` | 分组 |
| `version` | `String[]` | `"default"` | 版本 |
| `protocols` | `Protocols` | `HTTP` | 协议，见 [Protocols](#protocols) |
| `status` | `ApiStatus` | `DEFAULT` | 状态，见 [ApiStatus](#apistatus) |

## @ApiOperation

标注位置：方法。通过 `AnnotatedElementUtils.findMergedAnnotation` 查找，写在接口或父类方法上也能识别。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 接口名称 |
| `description` | `String` | `""` | 接口说明 |
| `hidden` | `boolean` | `false` | 隐藏该接口 |
| `tags` | `String[]` | `{}` | 标签，参与文档搜索 |
| `headers` | `Headers[]` | 见说明 | 请求头。默认值为一个 `@Headers(key = "Content-Type", value = "application/json", required = true, description = "上下文类型", example = "Content-Type=application/json")` |
| `paramType` | `ParamType` | `DEFAULT` | 方法内字段的参数位置。`DEFAULT` 且方法有 `@RequestBody` 参数时按 `BODY` 处理；其他值覆盖所有模型字段的 `paramType` |
| `status` | `ApiStatus` | `DEFAULT` | 状态 |
| `group` | `String[]` | `"default"` | 分组；保持默认时不继承 `@Api` |
| `version` | `String[]` | `"default"` | 版本；保持默认时不继承 `@Api` |
| `auth` | `boolean` | `true` | 未读取 |
| `authType` | `ApiAuthorization` | `OAUTH_2` | 未读取 |

## @ApiProperty

标注位置：参数、方法（`@Inherited`）。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 参数说明 |
| `name` | `String` | `""` | 显示的参数名，为空时使用 Java 参数名 |
| `description` | `String` | `""` | 详细说明。YAML / SQLite 规则里写了 `description` 时以规则为准 |
| `dataTypeClass` | `Class` | `Void.class` | 未读取 |
| `position` | `int` | `0` | 排序位置 |
| `length` | `int` | `0` | 长度 |
| `decimals` | `int` | `0` | 小数位数 |
| `hidden` | `boolean` | `false` | 标记为隐藏 |
| `example` | `String` | `""` | 示例值 |
| `defaultValue` | `String` | `""` | 默认值 |
| `required` | `boolean` | `false` | 是否必填。YAML / SQLite 策略下，规则里有这个参数时以规则中的 `required` 为准（规则写 `false` 而注解写 `true` 时按非必填），没有这个参数的规则时才用 `@ApiProperty.required`；注解策略下就是 `@ApiProperty.required`。文档显示的必填与运行时校验是否登记用同一个结果，为 `true` 时参数的校验规则才会登记到运行时校验（1.3.0 起；1.2.x 中文档显示取两者的「或」，校验登记只看规则）。见 [参数校验](/guide/validation) |
| `paramType` | `ParamType` | `QUERY` | 参数位置。参数带 `@PathVariable` 时为 `PATH` |
| `group` | `String[]` | `"default"` | 分组；保持默认时继承方法 |
| `version` | `String[]` | `"default"` | 版本；保持默认时继承方法 |

:::warning
目前生成文档时只读取标在**参数上**的 `@ApiProperty`。标在方法上（靠 `name` 匹配参数）的写法不会被读取。
:::

## @ApiPropertys

标注位置：方法（`@Inherited`）。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `ApiProperty[]` | 必填 | 多个参数说明 |

目前生成文档时不读取 `@ApiPropertys`。

## @ApiModel

标注位置：类（`@Inherited`）。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | `""` | 实体名称；不加 `@ApiModel` 时文档显示类的全限定名 |
| `description` | `String` | `""` | 实体说明 |
| `tags` | `String[]` | `{}` | 未读取 |
| `group` | `String[]` | `"default"` | 分组；保持默认时继承所在方法 |
| `version` | `String[]` | `"default"` | 版本；保持默认时继承所在方法 |
| `required` | `boolean` | `true` | 未读取 |

## @ApiModelProperty

标注位置：字段。没有此注解的字段不会出现在文档中。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 字段说明；也是校验提示里的"字段描述" |
| `name` | `String` | `""` | 显示的字段名，为空时用 Java 字段名；字段规则按这个名字匹配 |
| `description` | `String` | `""` | 详细说明 |
| `dataTypeClass` | `Class<?>` | `Void.class` | 未读取 |
| `position` | `int` | `0` | 排序位置 |
| `length` | `int` | `0` | 长度 |
| `decimals` | `int` | `0` | 小数位数 |
| `hidden` | `boolean` | `false` | 在所有接口中隐藏 |
| `example` | `String` | `""` | 示例值 |
| `defaultValue` | `String` | `""` | 默认值 |
| `paramType` | `ParamType` | `QUERY` | 参数位置；会被 `@ApiOperation.paramType` 和字段规则覆盖 |
| `required` | `boolean` | `false` | 是否必填；有匹配的字段规则时以规则为准 |
| `forceReq` | `boolean` | `false` | 用作请求参数时总是出现，无需字段规则；优先级低于 `ignoreReq` |
| `forceRep` | `boolean` | `false` | 用作返回参数时总是出现，无需字段规则；优先级低于 `ignoreRep` |
| `ignoreReq` | `boolean` | `false` | 在所有请求参数中忽略 |
| `ignoreRep` | `boolean` | `false` | 在所有返回参数中忽略 |
| `group` | `String[]` | `"default"` | 分组；保持默认时继承所属实体 |
| `version` | `String[]` | `"default"` | 版本；保持默认时继承所属实体 |
| `groups` | `Group[]` | `{}` | 按方法声明的字段规则，只在注解策略下读取，见 [@Group](#group)。1.3.0 起弃用，2.0 删除 |

## @Group

:::warning 已弃用
`@Group`、`@Request`、`@Response` 和 `@ApiModelProperty.groups` 自 1.3.0 起弃用，2.0 删除，1.3.0 中行为不变。按接口选择字段请改用 YAML / SQLite 规则（`@EnableApidoc(paramType = ...)`），所有接口都显示的字段用 `forceReq` / `forceRep`。
:::

只能作为其他注解的属性使用（`@Target(ANNOTATION_TYPE)`），用于 `@ApiModelProperty.groups`。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 方法名；写成 `Controller简单类名.方法名` 时精确匹配某个 Controller 的方法 |
| `request` | `Request` | `@Request(value = false)` | 该方法中作为请求参数时的规则 |
| `response` | `Response` | `@Response(include = false)` | 该方法中作为返回参数时的规则。要返回此字段必须写 `response = @Response` |

## @Request

只能作为注解属性使用。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `paramType` | `ParamType` | `QUERY` | 参数位置，覆盖 `@ApiModelProperty.paramType` |
| `value` | `boolean` | `true` | 未读取。匹配到 `@Group` 的字段总会进入该方法的请求参数 |
| `required` | `boolean` | `false` | 是否必填，覆盖 `@ApiModelProperty.required` |

## @Response

只能作为注解属性使用。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `include` | `boolean` | `true` | 是否出现在该方法的返回参数中 |
| `required` | `boolean` | `false` | 返回参数中显示的"必填" |

## @Headers

只能作为注解属性使用，用于 `@ApiOperation.headers`。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 请求头的值 |
| `key` | `String` | 必填 | 请求头名称 |
| `required` | `boolean` | `false` | 是否必传 |
| `example` | `String` | `""` | 示例，如 `Content-Type=application/json` |
| `description` | `String` | `""` | 说明 |
| `position` | `int` | `0` | 排序位置 |
| `hidden` | `boolean` | `false` | 标记为隐藏 |
| `group` | `String[]` | `"default"` | 未读取 |
| `version` | `String[]` | `"default"` | 未读取 |

## @DocGlobalHeader

标注位置：类、方法、包（`package-info.java`）。可重复（容器注解 `@DocGlobalHeader.List`）。只作为文档 UI 调试时的预填建议，不影响请求处理。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | `String` | 必填 | 请求头名称 |
| `description` | `String` | `""` | 说明 |
| `example` | `String` | `""` | 示例值，UI 用作输入框默认值 |
| `required` | `boolean` | `false` | 是否必填，UI 高亮提示 |

合并规则：包、类、方法三处声明合并，同名按 方法 > 类 > 包 覆盖。这两个注解不继承：写在接口、父类上不会生效；包级只读 Controller 所在包本身的 `package-info.java`。结果输出在接口数据的 `globalHeaderHints` 字段。

## @DocAuth

标注位置：类、方法、包。不可重复。只作为文档 UI 调试时的鉴权预设提示，不影响后端鉴权。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `scheme` | `String` | 必填 | `bearer`、`basic`、`apikey`、`oauth2_client`、`none`；其他值会记 WARN 日志并原样输出 |
| `description` | `String` | `""` | 说明 |
| `in` | `String` | `"header"` | 仅 `apikey`：`header` 或 `query` |
| `name` | `String` | `""` | 仅 `apikey`：请求头或查询参数名 |

取值规则：方法 > 类 > 包，取第一个找到的声明。结果输出在接口数据的 `authHint` 字段，非 `apikey` 时 `in` 和 `name` 为空。

## @ApiResponseCode

标注位置：类（`@Inherited`），实际用于枚举。枚举需要被 `@EnableApidoc` 的 `enumPackages` 或 `enumPackageClasses` 扫描到。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 返回码表的说明 |
| `group` | `String[]` | `"default"` | 未读取 |
| `version` | `String[]` | `"default"` | 未读取 |

枚举需实现一个继承 `ai.csap.apidoc.util.IValidate` 的接口，码表中的名称、编码、说明分别取自 `getName()`、`getCode()`、`getMessage()`。示例见 [注解使用指南](/guide/annotations#全局返回码表apiresponsecode)。

## @EnumValue / @EnumMessage

标注位置：字段（枚举类内部的实例字段）。两者都没有属性。

字段类型为枚举时，文档为每个枚举常量输出 `name`（常量名）、`code`（`@EnumValue` 字段的值）和 `message`（`@EnumMessage` 字段的值）。

## @DataTypeValidate

标注位置：字段、参数。由 `csap-validation-boot-starter` 注册的 `ValidateExceptionHandler`（类在 `csap-validation-core`）读取。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `code` | `String` | `"400"` | Controller 方法参数类型转换失败时返回的 `code` |
| `message` | `String` | `"数据格式错误"` | 返回的 `message` |
| `data` | `String` | `""` | 未读取 |

只在标注于 Controller 方法参数、且该参数抛出 `MethodArgumentTypeMismatchException` 时生效。没有此注解时，返回 `code = "400"`、`message = "数据格式错误"`。标在字段上不会被读取。

## @JsonFieldFilter / @JsonFieldFilters

标注位置：方法。

`@JsonFieldFilter`：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `type` | `Class<?>` | `Object.class` | 要过滤的类 |
| `include` | `String[]` | `{}` | 保留的字段 |
| `exclude` | `String[]` | `{}` | 去掉的字段 |

`@JsonFieldFilters`：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `JsonFieldFilter[]` | 必填 | 多个过滤规则 |

目前没有代码读取这两个注解，标注后返回的 JSON 不会变化。

## @Description

标注位置：类、字段、方法。目前未读取。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | `String` | 必填 | 描述 |
| `author` | `String` | `"administration"` | 作者 |
| `dataTime` | `String` | `""` | 时间 |

## @RequestParamBody

标注位置：参数。没有属性。目前未读取。

## 枚举类型

### ParamType

参数位置：`PATH`、`QUERY`、`BODY`、`FORM_DATA`、`HEADER`、`FORM`、`DEFAULT`。`DEFAULT` 主要供 `@ApiOperation.paramType` 使用，表示"不统一指定"。

### ApiStatus

接口状态：`DEFAULT`（开发中）、`FINISH`（完成）、`DEPRECATED`（过时）。

### Protocols

协议：`HTTP`、`HTTPS`、`WS`、`WSS`。

### ApiAuthorization

`@ApiOperation.authType` 的类型：`BEARER_TOKEN`、`BASIC_AUTH`、`OAUTH_2`、`OTHER`。由于 `authType` 未被读取，这个枚举目前不影响文档。
