# DevTools 页面的后端提示(hint)

DevTools 前端由 Java 和 Go 共用同一份构建产物,页面里的通用文案不写任何一种语言的具体用法;需要“怎么配置”的地方由**后端在状态接口里可选返回 hint**,前端优先显示。

## 格式

`hint` 类字段的值是**字符串**或**对象 `{ "zh": "...", "en": "..." }`**(按页面语言取,缺哪种语言就退回另一种);允许换行,前端等宽、保留换行显示。字段缺省时,前端显示通用文案和官网链接。

| 接口 | 字段 | 何时显示 |
|---|---|---|
| `GET devtools/rules/status` | `hint` | `supported=false`(当前存储不支持修改记录)时,说明怎么切换到数据库 / Redis 模式 |
| `GET devtools/mcp/status` | `hint` | `enabled=false` 时,说明怎么开启 MCP(含配置示例) |
| | `persistHint` | 设置不能持久化时的提示里,说明怎么持久化 |
| | `tokenHint` | 鉴权页“令牌”说明后追加:令牌在哪里配置 |
| | `securityNotes` | 数组,每项是字符串或 `{zh,en}`,追加到“安全建议”列表末尾(如与 Web 框架安全组件的配合) |

Java 返回 Java 写法(`@EnableApidoc`、`csap.apidoc.*`、Spring Security);Go 返回 Go 的写法。
