# 字段库与模板

约定见仓库 [docs/design/1.4.0-field-library.md](../../docs/design/1.4.0-field-library.md),包是 `fieldlib`。把常用字段(说明、示例、必填、校验规则)做成模板,在任何接口的任何字段上套用。

```yaml
version: 1
templates:
  - name: phone-cn            # 小写字母、数字、- _,最长 64
    title: 手机号(中国大陆)
    description: 手机号
    example: "13800138000"
    validate:
      - {type: Pattern, pattern: PATTERN_35, code: "400", message: 手机号格式不对, level: 1}
```

- **两层**:内置模板(`fieldlib.Builtin()`,16 条,随库发布,只读;与 Java 版是同一份文件 `field-templates.yaml`,以 Java 为准,单测逐字节比对)+ 项目模板;同名时项目覆盖内置(`fieldlib.Merge`)。
- **项目模板的位置跟着规则存储走**:数据库和 Redis 用保留键 `@templates`(与规则一样走版本号、修改记录、多实例同步),YAML 用 `field-template/api-field-template.yaml`(与 `method-field` 目录并列)。以 `@` 开头的规则键不是接口规则,各规则来源加载时都会跳过它。读写用 `fieldlib.LoadProject` / `SaveProject`(存储需实现 `strategy.RawStore`:sql、redis、yaml 都已实现)。
- **套用 = 复制,不联动**:`fieldlib.Preview` 列出会改哪些项,`fieldlib.Apply` 套用:模板里写了的 `description` / `example` / `required` 各自覆盖,`validate` **整体替换**(重复套用不叠加),没写的项不动;字段记下 `template: 模板名`(只用于展示,运行时不读)。模板更新后已套用的字段不会自动变,`fieldlib.Diverges` 判断"与模板不一致"。`fieldlib.FromRule` 把字段存成模板("另存为模板")。
- **DevTools 接口**:`GET /csap/yaml/fieldTemplates`(内置 + 项目,标来源)、`POST /csap/yaml/fieldTemplates`(保存项目模板)、`DELETE /csap/yaml/fieldTemplates/{name}`(内置只读,不能删)。
- 规则里还有两个可选字段:`example`(字段示例)、`template`(套用的模板名)。
