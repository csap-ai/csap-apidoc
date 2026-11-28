# AI 编码助手技能(Skill)

本页介绍 `csap-apidoc` 技能:安装后,Claude Code 等支持 Agent Skills 的 AI 编码助手就知道如何给 Java(Spring Boot)或 Go(gin、net/http)项目接入 CSAP Apidoc、写注解或结构体标签和 YAML 规则、排查"文档里看不到字段"。技能会先判断项目是什么语言,再按对应语言的写法处理,不会把 Java 的写法套到 Go 项目上。

技能源文件在仓库的 [`skills/csap-apidoc/`](https://github.com/csap-ai/csap-apidoc/tree/main/skills/csap-apidoc),随版本一起维护。当前对应 Java 1.5.1 和 Go v0.3.1;后续语言发布后会陆续加入。

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

之后在对话里提到 CSAP Apidoc、`@EnableApidoc`、`apidoc.Op`、`csap-apidoc-go`、`method-field.yaml` 等,助手会自动加载这个技能。

## 技能里有什么

技能分共通部分和分语言的部分:

- **共通**:先判断项目语言的方法;运行时生成文档、字段规则、返回过滤、请求校验、DevTools 的概念;两种语言共用的文档数据接口(`/csap/apidoc` 等)和 YAML 规则文件格式;两种语言的差别(例如非必填字段上的校验规则:Go 传了值就执行,Java 1.4.0 起相同,1.3.0 及以前不执行);避免助手对用户做出不成立的承诺(返回过滤和校验是否默认开启等)。
- **Java 版**:正确的 Maven 坐标和依赖(以及旧文档里错误坐标的识别)、`@EnableApidoc` 和各注解的写法、字段"默认不显示"的规则与 `forceReq` / `forceRep`、`@Group`、YAML 三种选择字段的方式、`csap.apidoc.*` 和 `csap.validation.*` 配置、DevTools。
- **Go 版**:`go get` 和版本、`apidoc.New` 与 `Options`、gin / net/http 适配、`apidoc.Op` 声明接口、结构体标签、返回过滤(`FilterMode`、`FilterEndpoints`、`ShouldFilter`)、请求校验中间件、devtools 鉴权。
- 接入后的检查步骤。

## 和 MCP 一起用

1.3.0 起服务可以开启内嵌 MCP 端点(见 [MCP](/guide/mcp)),AI 助手连上后能直接查询和调用接口。技能里写了何时优先用 MCP 工具、怎样读工具的返回和错误、写操作为什么默认不开放,以及开启、客户端配置和排错(`references/mcp.md`)。技能负责"怎么接入和维护",MCP 负责"直接调用",两者配合使用。
