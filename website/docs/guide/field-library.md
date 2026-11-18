# 字段库与模板（1.4.0 计划）

:::warning 1.4.0 计划，以 1.4.0 实现为准
字段库与模板计划随 **Java 1.4.0（2026-12-22）** 发布，Go 同步读取同一份模板，**当前已发布的版本没有**。本页按已审定的约定编写（设计见仓库 `docs/design/1.4.0-field-library.md`），具体行为以 1.4.0 实现为准。
:::

## 它解决什么问题

现在只有内置的正则模板（`PATTERN_00` 起的校验正则）。但一个字段通常要同时配说明、示例、必填和一组校验规则（例如"手机号"），每个接口的每个字段都得重复配一遍，团队里写法也不一致。字段库把这一整套存成**模板**，在任何接口的任何字段上一键套用。

## 模板格式

YAML，Java 与 Go 共用：

```yaml
version: 1
templates:
  - name: phone-cn            # 唯一标识：小写字母、数字、- _，最长 64
    title: 手机号(中国大陆)
    category: 通用             # 分组，可选
    description: 手机号        # 套用到字段的说明，可选
    example: "13800138000"    # 示例，可选
    required: false           # 套用时是否改变必填，可选；不写表示不改
    validate:                 # 校验规则，可选
      - type: Pattern
        pattern: PATTERN_35
        code: "400"
        message: 手机号格式不对
        level: 1
```

模板**不包含**参数位置（`paramType`）和返回字段是否输出（`include`），这两项属于接口，不属于字段。`validate` 的 `type` 只用 Java、Go 共有的 `NotNull`、`NotEmpty`、`Pattern`。

## 存放位置

| 层 | 内容 | 位置 |
|----|------|------|
| 内置模板 | 随 jar 发布的常用字段（手机号、邮箱、身份证、URL、页码、日期、金额等） | jar 内 `META-INF/csap-apidoc/field-templates.yaml`；只读 |
| 项目模板 | 团队自己的模板 | 跟着规则存储走：数据库 / Redis 里保留键 `@templates`；YAML / JSON 文件放在 `{path}/field-template/api-field-template.yaml` |

`ANNOTATION` 模式只有内置模板。内置与项目模板同名时，项目模板覆盖内置。以 `@` 开头的规则键不是接口规则，所有读取方会跳过它。项目模板跟随规则存储，所以多实例同步、修改记录、回滚、备份都不用另外做。

## 套用与更新

**套用 = 复制，不联动**：

1. 在 DevTools 字段编辑里选"从模板套用"，先显示预览（会改哪些项）；
2. 确认后，模板里写了的项覆盖字段对应项，`validate` **整体替换**（不是追加）；模板没写的项不动；
3. 字段规则记下 `template: <模板名>`，之后字段可以随意再改。

模板更新后，已套用的字段**不会自动变化**：DevTools 会提示"与模板不一致"，并提供"重新套用"。也可以把字段当前的内容"存为模板"。

规则里新增两个可选键：`template`（只用于展示，运行时不读）和 `example`（字段示例，文档生成时用它填示例）。

## DevTools 与接口

- 校验规则弹窗增加"模板"页：搜索、按分类、套用、存为模板；模板管理抽屉可新增、编辑、删除项目模板（内置的可"复制为项目模板"后修改）；
- 接口：`GET /csap/yaml/fieldTemplates`、`POST /csap/yaml/fieldTemplates`、`DELETE /csap/yaml/fieldTemplates/{name}`；不可写时返回 `1007`。

:::warning 升级注意
旧版本 Java DevTools 重写规则时会丢掉新增的 `template`、`example`；旧版本读到 `@templates` 会按"规则键格式不对"忽略并打一条警告。数据库、Redis 模式下多实例请一次全部升级。
:::

## 相关页面

- [参数校验](/guide/validation)
- [Mock 服务](/guide/mock)：`example` 会参与取值
- [存储策略与返回过滤](/guide/storage-strategies)
