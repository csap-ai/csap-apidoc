# csap-apidoc-go 交接说明

> 写于 2026-11-17。这份交接来自 football-data-hub（kyty-data-center 的 Go 重写）的会话，以后在本目录单独开窗口继续做。设计以 [DESIGN.md](DESIGN.md) 为准，这里只记录现状、来龙去脉和接手时要注意的事。

## 1. 要做什么

做一个 CSAP Apidoc 的 Go 版框架，开源，协议为 Apache-2.0。需求是用户在对话中逐条提出的：

1. **运行时生成接口文档**，效果和 Java 版 `csap-api.html` 一样，可以在页面上看文档。
2. **后台管理**：可以指定文档里显示哪些请求参数、返回字段，修改后实时生效。
3. **控制实际返回**：返回字段按白名单输出。代码里返回了、但规则没放行的字段，序列化时去掉。
4. **请求参数校验**：可以指定哪些参数必填、按什么规则校验，修改后实时生效。对应 Java 的 csap-validation。
5. 做成框架，放在 apidoc 的 git 仓库里作为一个模块，开源。

第一个使用方是 football-data-hub（`/Users/ycf/Documents/local/sports/code/football-data-hub`）。它现在的返回字段白名单是写死在代码里的（结构体上的 `json:"-"`），将来要换成本框架。

## 2. 当前位置和状态

| 项 | 值 |
|---|---|
| 目录 | 仓库内 `csap-apidoc-go/`。M4、M5 在 git worktree `/Users/ycf/Documents/产品/csap/framework/csap-apidoc-go`(分支 `feat/go-m4-m5`)完成 |
| git | 分支 `feat/go-m4-m5`,基于 `feat/apidoc-go` 并已 rebase 到 `origin/main`(08be1d2)。远程:`origin` = GitHub `csap-ai/csap-apidoc`(主线),`gitee` = gitee 镜像 |
| 分支基线 | **GitHub `main`**（bee9d83，2026-05-02）。2026-11-17 统一了版本和分支，见 [版本与分支统一方案.md](版本与分支统一方案.md)；原来的 gitee 基线 b4b9819 存档为 tag `archive/chore-apidoc-next-iteration` |
| Java 版本 | 统一后第一个版本为 **1.1.0**（分支 `chore/unify-version-1.1.0`）；1.0.0 指 kyty 线上那份 |
| Go 模块路径 | `github.com/csap-ai/csap-apidoc/csap-apidoc-go`，最低 Go 1.22（以后发布到 GitHub `csap-ai` 组织） |
| 提交 | M1–M5 都已提交到 `feat/go-m4-m5`,**未推送,未打 tag**。发版 tag 由用户打:`csap-apidoc-go/v0.1.0` |
| 代码 | **M1–M5 全部完成**(2026-11-17),版本 v0.1.0 待发布(目标 2026-12-04)。M5:`adapter/nethttp`、`postman`、CI(`.github/workflows/go.yml`)。M4:`devtools`(Java 兼容接口、内嵌页面、鉴权、修改记录与回滚)、`strategy.Historian`/`Journal`(YAML、Memory 补上修改记录)、`globalHeaderHints`/`authHint`、前端更新到 main(1.1.1)。M3:`strategy/yaml`、`strategy/sql`、`integration/`。M2:`filter`、`validate`、gin 中间件。M1:`model`、`strategy`、`strategy/tag`、根包、`openapi`、`ui`、`adapter/gin`。`go vet`、`go test -race ./...` 通过,总覆盖率 80%(不计示例和只在 integration 测的 sql 包时更高) |
| 文档 | `README.md`、`CHANGELOG.md`、`docs/DESIGN.md`(设计)、本文件;官网接入文档 `website/docs/go/index.md` |
| 许可证 | 沿用仓库根目录的 `LICENSE`（Apache-2.0），模块里不另放 |

下一步:

1. 用户 review 后推送 `feat/go-m4-m5`、合并到 `main`,打 tag `csap-apidoc-go/v0.1.0`(目标 2026-12-04)。官网导航里 Go 页的标题在 `website/rspress.config.ts`,已是"Go 版",无需改。
2. 发布后按第 7 节第 3 条通知 football-data-hub 接入(本次没有发,等发版)。
3. 后续候选:echo / chi / fiber 适配;在线调试跨域代理(Java `/csap/apidoc/devtools/proxy`);MySQL 实测;返回过滤改为序列化前按结构体过滤以降开销(DESIGN 5.2)。

### 2.1 M1 的验证情况

- `go run ./examples/gin-basic` 后打开 `http://localhost:8080/csap-api.html`：Java 版前端能正常显示分组树、GET 的 QUERY/PATH 参数、POST 的嵌套请求体、外层包装和返回字段，浏览器控制台无报错；按规则只放行 `data.id`、`data.name` 的接口，文档里返回字段只剩这两个。
- `/csap/apidoc` 的各层对象（顶层、Controller、Method、Model、Parameter）的字段集合，与 kyty 线上导出的 `csap-apidoc.json` 完全一致（jq 比对）。
- `/csap/apidoc/method` 找不到接口时回 200 空 body、无 Content-Type；缺参数回 400；`/csap/apidoc/{name}?key=` 只返回副本，之后全量文档的方法数不变。
- 与 Java 实时返回的逐字节比对已在 M2 完成，见 6.2 节。

### 2.2 M2 的验证情况（2026-11-17）

- 返回过滤：路径白名单与 csap 兼容两种模式，`ginadapter.ResponseFilter`。黄金样本 79 个 kaiyun 接口的返回字段集合与 Java 完全一致（DESIGN 5.1）。
- 请求校验：`validate` 包 + `ginadapter.RequestValidator`，关闭 / 观察 / 拦截三种模式，Java 兼容的错误返回和 7 种 TipType，内置正则已修正（DESIGN 5.5）。Java 那边从没开过校验，没有真实返回可比，按源码复刻。
- 示例 `examples/gin-basic` 已接上两个中间件，实测过滤和拦截都生效。
- 性能：裁剪额外开销约为编码耗时的 50%，**没达到** 30% 的目标（DESIGN 5.2）。
- `go test -race ./...` 通过，覆盖率：根包 86%、filter 83%、validate 79%、strategy 94%。

### 2.4 M4 的验证情况(2026-11-17)

- 前端:文档页换成 main 上的构建产物(index-a8cfe7dd.js,1.1.1);管理页面用 `csap-apidoc-devtools/src/main/resources/static`(React 版,与源码同一提交)。两套都由 `scripts/sync-ui.sh --ref origin/main` 从 git 同步。管理页面的资源 `/assets2/` 和 vite 按需加载的 `assets2/` 都改写到 `<UIPrefix>/assets2/`;`ui` 的测试会沿着页面和按需加载的 js 逐个检查资源能取到。
- 接口:逐个对照 Java `ApidocStrategyController`、`DevtoolsController`、`ApidocDevtools`,以及前端 `src/api/devtools.ts` 和各弹窗实际读写的字段实现。前端实际用到的是 scannerApi、`/csap/apidoc/{name}?key=`、getFields、addRequestParam、addResponseParam、getMethodValidateFields、validatePatternTypes;write、writeSelect、writeFieldMethod、getAllMethod、hiddenMethod 也实现了(hiddenMethod 与 Java 一样是空实现)。
- 浏览器实测(gin 示例):登录页(前端自带的假登录) → 接口列表 → "返回参数"弹窗显示已选字段与可选字段 → 勾选 `data.age` 保存 → YAML 写回、修改记录写入、下一次请求返回多出 `age`;"请求参数"弹窗新增字段保存后,原有校验规则保留。发现并修了一个问题:前端给新字段填的 `paramType` 是接口的 `DEFAULT`,现在视为未指定。
- `getFields` 的"可选字段"来自 `Snapshot.FullMethod`(不套规则重新生成一遍),外层包装的 `data` 换成业务类型;单值参数(路径、查询)返回一个与参数同名的字段,规则键为 `id.id`。
- 鉴权:`devtools.New` 不传 `Auth` 返回错误;POST 只收 `application/json`(防 CSRF)。

### 2.5 M5 的验证情况(2026-11-17)

- net/http:`examples/nethttp-basic` 实测返回过滤、拦截校验、文档页、管理页、Postman、在线调试建议都正常。用 Go 1.22.10 工具链 `go vet ./...` 通过(本机老链接器在新 macOS 上跑不了测试二进制,测试用的是 1.27);CI 会在 1.22.x 和最新版上跑。
- Postman:按 `PostmanConverterService` 源码逐项对齐(由子任务实现,覆盖率 98%),**没有和 Java 实时输出做逐字节比对**;差别写在 `postman/postman.go` 顶部注释。需要时可用 football-data-hub 的比对环境启动 Java 抓 `/csap/openapi/postman` 比对(Java 的 apidoc 是 kyty 线上版,要换成 1.1.x)。

### 2.3 M3 的验证情况（2026-11-17）

- YAML：kyty 77 份文件全部解析、逐接口写回再加载不变；示例项目运行中改 YAML，3 秒内返回字段变化（DESIGN 3.4）。
- SQL：SQLite 和 PostgreSQL 16 各 15 遍通过（含两实例同步、只靠通知同步、加载与监听之间的修改不漏），MySQL 未实测（DESIGN 3.5）。PostgreSQL 用的是临时容器 `postgres:16-alpine`，测完已删，没碰 football-data-hub 的比对库。
- 修了一个 bug：监听的基准原来是"开始监听时"的版本/文件签名，加载与监听之间的修改会被永远漏掉；现在基准是"上次加载时"的。

## 3. Java 基线是怎么确认的

用户要求基线必须是"最新代码，也就是线上运行的"。确认方法如下：

- 线上 jar 是 `/Users/ycf/Documents/local/sports/code/kyty-data-center/kyty-data-center-boot/target/kyty-data-center-boot.jar`，依赖 `csap-apidoc-*-1.0.0`、`csap-validation-*-1.0.0`。这些 jar 里带有 `.java` 源码。
- 从 jar 里解出 175 个源码文件，逐个和各分支比较：
  - `origin/1.0.3`、`origin/chore/apidoc-next-iteration`：**175/175 一致**。
  - 本地 `old/` 的已提交版本 eb625c6：21 个不同。
  - `old/` 加上未提交的改动：6 个不同。
- `csap-apidoc-ui` 的构建产物（`csap-api.html`、`index-ae5c8bc5.js`、`index-fc22d3a3.css`）也和 jar 里的一致。
- `1.0.3` 与 `chore/apidoc-next-iteration` 的代码相同，后者只多了 `.github` 下的 CI 发布流程和英文 README。

复核命令：

```bash
unzip -q <jar> 'BOOT-INF/lib/csap-apidoc*' 'BOOT-INF/lib/csap-validation*' -d /tmp/x
```

然后对每个 jar 执行 `unzip '*.java'`，再和 `git show <ref>:<path>` 的内容比较。

## 4. 不要碰的东西

- **`framework/old/csap-framework-apidoc`**：2026-11-17 已处理。它原有的未提交草稿已提交到分支 `archive/old-local-drafts-2025-11` 并推到 gitee 存档（tag 同名），工作区现在是干净的。它是这个仓库 `.git` 所在的主工作目录，要等新位置重新克隆好、用户确认后才能删除。两个工作目录共用 stash，不要直接用 `git stash` / `git stash pop`。
- `framework/` 下其他仓库（`csap-framework`、`csap-mybatis-plus` 等）也有用户未提交的改动，与本项目无关。
- 推送（GitHub、gitee）、打 tag、删远程分支前，先问用户。

## 5. 用户偏好与约束（来自之前的对话）

- 文档一律写成本地 Markdown，不用 Claude Docs 云端文档。
- 对用户用中文。
- 面向用户的界面里不能出现"开云/KYTY"字样。这条原本针对 football-data-hub 的监控台，接入时注意。
- "有缺陷、不完整就直接修，不用确认。"但开源仓库的提交、推送、发版需要用户确认。

## 6. 已有的发现（实现时直接用）

### 6.1 Java 版缺陷，Go 版不复刻

- **apidoc 缓存被改坏**：`GET /csap/apidoc/{tableName}?key=` 先浅拷贝 `CsapDocResponse`，再在 `peek` 里直接修改缓存中 Controller 的 `methodList`。之后全量文档、树形菜单、方法详情都会永久缺少方法，`flush=true` 也恢复不了，只能重启。在 kyty 上实测，方法数从 88 降到 46。
- **校验从没生效**：`ValidateIntercept` 只有在 `csap.validation.enabled=true` 时才注册（`@ConditionalOnProperty(havingValue="true")`，没有 `matchIfMissing`）。kyty 的 YAML 里写了 35 段 `validate` 规则，但没配这个开关，所以线上一条都没生效。
- **内置正则有错**：`PATTERN_12`、`PATTERN_21`、`PATTERN_27`、`PATTERN_30`、`PATTERN_33`、`PATTERN_36`、`PATTERN_41` 等，详见 DESIGN 第 5.5.3 节。另外 `PATTERN_43` 用了零宽断言，Go 的 RE2 不支持。

### 6.2 Java 的输出格式（csap 兼容模式需要逐字节对齐时用）

`CsapDocController` 没有 `@ResponseAdvice`，走的是 fastjson 路径 B：

- 外层是 `ApidocResult`：`{"code":"0","data":…,"language":"ZH","message":"成功","success":true,"time":<毫秒>}`。
- 格式化输出，用 tab 缩进，键按字母序，null 字段省略。
- **数组的格式规则**（逐字节比对后确认）：
  - 空数组写成 `[]`。
  - 对象数组（Java 里是 List）逐个元素换行。
  - 基本类型数组（Java 里是 `String[]` 等）写在一行，如 `"group":["default"]`。
  - 例外：`methods`（`List<RequestMethod>`）和 `paramNames`（`List<String>`）是 List，虽然元素是基本类型，也逐个换行。
- **2026-11-17 用实时返回确认**：上面的数组规则正确（MCP 用的 `csap-apidoc.json` 被重新格式化过，所有数组都换行，不能当基准）；文件末尾没有换行。
- **`$ref`**：参数节点同时有 `children` 和 `parameters` 时（Java `getParameters()` 返回的就是 `children.parameters` 同一个 List），fastjson 的循环引用检测把 `parameters` 的每个元素写成 `{"$ref":"$.data.apiList[0]…children.parameters[i]"}`（一行），空列表写 `[]`。Go 编码器按同样规则输出。用 Go 编码器重新编码 Java 的 2,588,849 字节实时返回，**逐字节一致**。
- 文档里参数节点的 `key`（如 `"2700240654411083705a6d0c"`）是每次启动随机生成的 ID，只给前端界面用，比对时要忽略。
- `/csap/apidoc/method` 找不到对应接口时，返回 HTTP 200、空 body、无 Content-Type。缺少必填参数时，返回 Spring 的 400 错误 JSON。
- `/csap/apidoc/{tableName}`：不带 `key` 时返回全量文档，不按 tableName 过滤；带 `key` 时，`apiList` 只保留这个 Controller，`methodList` 只保留这个方法；tableName 不存在时返回 `apiList: []`。
- `/csap/openapi/postman` 每次输出相同，`Content-Type: application/json;charset=UTF-8`。
- 静态资源：`/csap-api.html` 为 `text/html`，`/favicon.ico` 为 `image/x-icon`，`/assets/js/*` 为 `application/javascript`。

### 6.3 Java 前端调用的接口

- 文档页面（`csap-apidoc-ui`）：`/csap/apidoc/parent`、`/csap/apidoc/method`。
- 管理页面（`csap-apidoc-devtools/devtools`）：
  - `/csap/yaml/{write,writeSelect,writeFieldMethod,scannerApi,getAllMethod,addRequestParam,addResponseParam,getMethodValidateFields,validatePatternTypes}`
  - `/devtools/{getFields,hiddenMethod}`
  - 页面入口 `/devtools-ui`

  Go 版要兼容这些接口，前端才能直接复用。前端源码在本仓库的 `csap-apidoc-ui/` 和 `csap-apidoc-devtools/devtools/`。M4 已全部实现(见 2.4)。
- 文档页面(1.1.1)还会调用 `/csap/apidoc/devtools/proxy`(在线调试的跨域代理,用户在页面上配置后才用),Go 版未实现。

### 6.4 可用的真实数据

- kyty 的 77 份 YAML：`/Users/ycf/Documents/local/sports/code/kyty-data-center/kyty-data-center-boot/src/main/resources/csap-api-doc/method-field/`。M3 的 YAML 策略用它测试解析。注意：YAML 里有不少已经不存在的字段（如 `data.current`、`data.size`、`success`），也缺 `forceRep` 字段（如 `data.lastId`），所以**只看 YAML 推不出 Java 的实际输出**，要结合类型。
- M2 的黄金样本（2026-11-17 抓取，放在会话 scratchpad，不进仓库）：用 `start-java.sh a` 启动 Java，用本地 A 库种子里的测试客户端（`env/seed-auth.sql`，`App-Id`/`App-Secret` 请求头，只在本机库有效）调 88 个 GET 接口，79 个成功返回（7 个 403 无权限、1 个 400、1 个走路径 B 不过滤）。抓取方法：`jq` 从 `/csap/apidoc` 取路由，逐个 `curl "$path?limit=3"`，存为 `<Controller 全名>.<方法名>.json`。重跑黄金测试：`CSAP_GOLDEN_DOC=<apidoc.json> CSAP_GOLDEN_RESP=<目录> go test ./filter -run Golden -v`。
- football-data-hub 的比对环境可以启动 Java 实例，抓取真实返回做逐字节比对：`tools/parity/env/start-java.sh a`，端口 38081。启动后：
  - `curl http://127.0.0.1:38081/csap/apidoc` 可以拿到 Java 生成的全量文档：59 个 Controller、88 个方法，约 2.5MB。
  - 抓 `?key=` 的结果前要先重启 Java，否则会被第 6.1 节的缓存缺陷影响。
- 跨项目依赖：MCP 服务 `kyty-data-center/kyty-soccerdatas-mcp` 用 `data/csap-apidoc.json`（从线上 `/csap/apidoc` 导出）生成工具定义。Go 版的 `/csap/apidoc` 只要兼容 Java 格式，MCP 就不用改。

## 7. 已确认事项（用户 2026-11-17）

1. **发布位置**：GitHub `csap-ai/csap-apidoc`，模块路径 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`。GitHub 仓库是 `https://github.com/csap-ai/csap-apidoc`（用户 2026-11-17 给出），模块路径据此定为 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`。**注意**：GitHub `main`（2026-05-02 bee9d83）和 gitee 不是同一条提交历史，GitHub 上没有 b4b9819；它比线上基线多了 2026-04 之后的开发，如文档页请求历史和请求快照（M9.1/M9.2）、i18n、`csap-apidoc-mock` 设计、只发 Maven Central 等。Go 代码怎么合到 GitHub 仓库、内嵌哪一版前端，推送前要和用户定。推送和建分支前先问用户。
2. **前端**：内嵌。用本仓库的前端源码构建后，通过 `go:embed` 打进模块，不拆成独立仓库。
3. **football-data-hub 接入**：**等本框架开发完成后，通知 football-data-hub 那边，由那边接入**，本框架这边不改 football-data-hub 的代码。通知方式：给会话「Java到Go迁移可行性分析 (fork)」（id 尾号 cebe1e）发消息（`SendMessage`，`to` 填这个会话名），写明以下内容：
   - 完成到哪个里程碑、提交号、模块路径。
   - gin 接入示例。
   - YAML 规则格式，以及与 Java `method-field.yaml` 的兼容情况。
   - 返回过滤的 csap 兼容模式怎么开启。
   - 请求校验和观察模式怎么开启。
   - 已知限制。

   如果发消息失败，就告诉用户，由用户转达。
