# AI 编码助手技能(Skill)

本页介绍 `csap-apidoc` 技能:安装后,Claude Code 等支持 Agent Skills 的 AI 编码助手就知道如何给项目接入 CSAP Apidoc、写注解和 YAML 规则、排查"文档里看不到字段"。

技能源文件在仓库的 [`skills/csap-apidoc/`](https://github.com/csap-ai/csap-apidoc/tree/main/skills/csap-apidoc),随版本一起维护。当前版本对应 Java 1.2.x。

## 安装

技能就是一个目录,复制到助手读取技能的位置即可。以 Claude Code 为例:

只给当前项目用(建议,团队成员拉代码就有):

```bash
mkdir -p .claude/skills
git clone --depth 1 https://github.com/csap-ai/csap-apidoc.git /tmp/csap-apidoc
cp -R /tmp/csap-apidoc/skills/csap-apidoc .claude/skills/
```

给本机所有项目用:

```bash
mkdir -p ~/.claude/skills
cp -R /tmp/csap-apidoc/skills/csap-apidoc ~/.claude/skills/
```

之后在对话里提到 CSAP Apidoc、`@EnableApidoc`、`method-field.yaml` 等,助手会自动加载这个技能。

## 技能里有什么

- 正确的 Maven 坐标和依赖(以及旧文档里错误坐标的识别)
- `@EnableApidoc` 和各注解的写法
- 字段"默认不显示"的规则,以及 `forceReq` / `forceRep`、`@Group`、YAML 三种选择字段的方式
- YAML 规则文件的位置、字段键写法和完整示例
- 文档数据接口(`/csap/apidoc` 等)和 DevTools 的用法
- 返回字段过滤、运行时参数校验（1.2.0 起，默认关闭）的开启方式和注意事项,避免助手对用户做出不成立的承诺
- 接入后的检查步骤

## 计划

1.3.0 计划提供"文档即 MCP":AI 助手可以通过 MCP 直接查询并调用你服务的接口,届时技能会同步加入 MCP 的使用方法。见 [路线图](https://github.com/csap-ai/csap-apidoc/blob/main/docs/ROADMAP.md)。
