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
| 目录 | `/Users/ycf/Documents/产品/csap/framework/apidoc/csap-apidoc-go` |
| git | `framework/apidoc` 是 `framework/old/csap-framework-apidoc` 这个仓库的第二个工作目录（`git worktree`），分支 `feat/apidoc-go` |
| 分支基线 | `origin/chore/apidoc-next-iteration` b4b9819（2026-04-11），即线上运行的代码，见第 3 节 |
| Go 模块路径 | `github.com/csap-ai/csap-apidoc/csap-apidoc-go`，最低 Go 1.22（以后发布到 GitHub `csap-ai` 组织） |
| 提交 | **没有提交，也没有推送**。`git status` 显示 `?? csap-apidoc-go/` |
| 代码 | **M1 已完成**（2026-11-17）：`model`（含 Java 格式编码器）、`strategy`（接口、合并、Memory）、`strategy/tag`、根包（生成、快照、`/csap/apidoc*`、`/csap/openapi.json`）、`openapi`、`ui`（内嵌前端）、`adapter/gin`、`examples/gin-basic`。`go vet`、`go test -race ./...` 通过，根包覆盖率 84%。其余包（`filter` `validate` `strategy/{yaml,sql}` `devtools` `postman` `adapter/nethttp`）仍只有 `doc.go` |
| 文档 | `README.md`、`docs/DESIGN.md`（设计）、本文件 |
| 许可证 | 沿用仓库根目录的 `LICENSE`（Apache-2.0），模块里不另放 |

下一步：DESIGN.md 第 10 节的事项都已确认（见第 7 节）。M1 已完成，接着做 **M2**（返回字段过滤、请求参数校验、基准测试），一直做到 M5。

### 2.1 M1 的验证情况

- `go run ./examples/gin-basic` 后打开 `http://localhost:8080/csap-api.html`：Java 版前端能正常显示分组树、GET 的 QUERY/PATH 参数、POST 的嵌套请求体、外层包装和返回字段，浏览器控制台无报错；按规则只放行 `data.id`、`data.name` 的接口，文档里返回字段只剩这两个。
- `/csap/apidoc` 的各层对象（顶层、Controller、Method、Model、Parameter）的字段集合，与 kyty 线上导出的 `csap-apidoc.json` 完全一致（jq 比对）。
- `/csap/apidoc/method` 找不到接口时回 200 空 body、无 Content-Type；缺参数回 400；`/csap/apidoc/{name}?key=` 只返回副本，之后全量文档的方法数不变。
- **还没做的**：与 Java 实时返回的逐字节比对（第 6.2 节），放到 M2 和返回过滤一起做。数组格式按第 6.2 节的规则实现（元素都是基本类型的数组写一行，`methods`、`paramNames` 除外）。但 MCP 用的那份 `csap-apidoc.json` 里所有数组都是逐个换行的，和这条规则对不上，可能是导出后被重新格式化过。M2 要用 `start-java.sh` 抓一份实时返回确认；如果规则有误，只需改 `model/csapjson.go` 里的 `inlineExcept` 和判断逻辑。

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

- **`framework/old/csap-framework-apidoc` 的工作区**：这是旧的本地副本，停在 2025-11-05，有 110 个文件改动没提交，是用户自己的工作。其中有些内容线上没有，例如：
  - devtools 前端的列宽调整功能（`csap-apidoc-devtools/devtools/src/components/table/ResizableTitle.tsx` 等）
  - `check_violations.sh`

  是否合并由用户决定，不要清理，也不要重置。另外两个工作目录共用 stash，不要直接用 `git stash` / `git stash pop`。
- `framework/` 下其他仓库（`csap-framework`、`csap-mybatis-plus` 等）也有用户未提交的改动，与本项目无关。
- 推送到 gitee 和打 tag 前，先问用户。

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

  Go 版要兼容这些接口，前端才能直接复用。前端源码在本仓库的 `csap-apidoc-ui/` 和 `csap-apidoc-devtools/devtools/`。

### 6.4 可用的真实数据

- kyty 的 77 份 YAML：`/Users/ycf/Documents/local/sports/code/kyty-data-center/kyty-data-center-boot/src/main/resources/csap-api-doc/method-field/`。M2 的返回过滤用它做黄金样本，M3 的 YAML 策略用它测试解析。
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
