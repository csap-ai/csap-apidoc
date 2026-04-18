# 试运行 v2 CORS 排错指南

> 适用于 `csap-apidoc-ui` 的 `⚡ 试运行 v2` 面板。
> 完整设计上下文见 [`features/environment-auth-headers.md`](features/environment-auth-headers.md) §6。

---

## 1. 为什么会撞到 CORS

试运行 v2 不走任何后端代理,而是 **浏览器直接** 用 `fetch` / `xhr`
把请求发到目标业务服务。当业务服务的域名与 `csap-apidoc-ui` 自身的
域名不同(包括协议 / 端口任一不同)时,浏览器会先检查目标响应头
里的 `Access-Control-Allow-*` 系列字段,不满足就拦截响应。

这是浏览器的安全策略,**与服务端是否能正确响应无关**:服务端日志
里看到 200,浏览器侧依然只能拿到一个空响应。

---

## 2. 常见症状

打开 DevTools,典型表现按出现频率排序:

- **Console 红字**

  ```
  Access to XMLHttpRequest at 'https://api-staging.example.com/orders'
  from origin 'https://apidoc.example.com' has been blocked by CORS
  policy: No 'Access-Control-Allow-Origin' header is present on the
  requested resource.
  ```

- **试运行响应区**:状态码显示 `0`,Body 区是「网络错误 / Failed to
  fetch」,Headers 区为空。tryItOutClient 把这种失败封装成
  `TryItOutFailure`,UI 不会吞成 toast 而是落到响应区红色提示框。

- **Preflight (OPTIONS) 失败**

  ```
  Response to preflight request doesn't pass access control check:
  It does not have HTTP ok status.
  ```

  通常意味着业务服务的过滤器 / 拦截器把 OPTIONS 请求当成业务请求处
  理了(比如要求 token 才放行),preflight 直接 401 / 403。

- **Cookie 不带过去**:服务端日志显示 session 拿不到,即使你已经在
  浏览器里登录过该业务域名。原因见下面 §3.4。

---

## 3. 四种典型场景及解法

> 表格沿用设计文档 §6 的三层模型,这里展开成可操作步骤。

### 3.1 业务服务自带 CORS — **零配置直接用**

最理想的情况。Spring Boot 端常见写法:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry r) {
    r.addMapping("/**")
      .allowedOriginPatterns("https://apidoc.example.com")
      .allowedMethods("*")
      .allowedHeaders("*")
      .exposedHeaders("*")
      .allowCredentials(true)
      .maxAge(3600);
  }
}
```

试运行直接通,无需任何额外操作。

### 3.2 业务服务 **未** 开 CORS — 推荐改后端,临时可走代理

- **推荐方式 A(一劳永逸)**:让业务服务把 csap-apidoc-ui 部署域名
  加进 CORS allowlist。Dev 环境可以放宽到 `*`,**生产必须明确指定
  origin**(尤其是开了 `Allow-Credentials: true` 的场景,见 §3.4)。

- **方式 B(临时绕开)**:走 `csap-apidoc-devtools` 即将提供的可
  选反向代理。
  - **当前状态:未实装,跟 M8.x 一起跟进。**
  - 实装前的临时手段:在 Settings 里填
    `tryItOut.proxyUrl`,值是一个用户自建的开源 cors-proxy。
    **强烈不建议指向公共 cors-proxy 服务**:这等于把所有请求体(包
    括 bearer token)交给第三方。

### 3.3 HTTPS 页面调用 HTTP 服务 — Mixed Content 拦截

如果 `csap-apidoc-ui` 是通过 HTTPS 访问的,浏览器会**完全阻断**它
对 HTTP 目标发出的请求,`Access-Control-Allow-*` 怎么配都没用。
Console 提示:

```
Mixed Content: The page at 'https://apidoc.example.com/' was loaded
over HTTPS, but requested an insecure XMLHttpRequest endpoint
'http://api-internal.example.com/orders'. This request has been
blocked.
```

可选解法:

1. 把目标业务服务也升级到 HTTPS(推荐)。
2. 把 `csap-apidoc-ui` 单独走 HTTP 部署在内网(**生产强烈不推荐**,
   会丢掉所有 HTTPS 提供的安全收益)。
3. 在前面架一层 HTTPS 反向代理转发到 HTTP 后端(等价于方案 1)。

### 3.4 需要 Cookie 跨域 — `Allow-Credentials` + `withCredentials`

跨域携带 Cookie / Authorization Cookie,需要双方都打开:

- 业务服务响应必须同时带:
  - `Access-Control-Allow-Credentials: true`
  - `Access-Control-Allow-Origin: <精确 origin>`(**不能是 `*`**)
- 浏览器侧请求必须 `withCredentials: true`(`fetch` 对应
  `credentials: 'include'`)。

> **当前实现说明**:`csap-apidoc-ui/src/services/tryItOutClient.ts`
> 走的是 `csap-axios`,默认 **没有** 显式置 `withCredentials`,即默
> 认不带跨域 Cookie。如果你的鉴权完全依赖 Cookie,目前需要靠
> §3.1 / §3.2 的 header / token 走通,或等待后续在 Settings 加
> 「跨域携带 Cookie」开关(M8.x 跟进项)。

---

## 4. 快速 checklist

撞到「Send 之后响应区一片红」时,按顺序对照排查:

1. **业务服务在你试运行用的环境里,是否返回了 `Access-Control-Allow-Origin`
   响应头?**
   用 `curl -i -X OPTIONS -H "Origin: <ui域名>" -H "Access-Control-Request-Method: POST" <目标url>` 模拟一次 preflight。
2. **preflight (OPTIONS) 是否被业务服务以 200 / 204 返回?**
   常见坑:全局鉴权过滤器把 OPTIONS 当业务请求处理 → 401。需要在过
   滤器里把 `OPTIONS` 直接放行。
3. **自定义请求头(`Authorization`、`X-API-Key`、`X-Tenant-Id` 等)
   是否在响应的 `Access-Control-Allow-Headers` 里?**
   缺一个就整个请求被拦。
4. **业务服务在你试运行的方法(POST / PUT / DELETE / PATCH)上是否
   出现在 `Access-Control-Allow-Methods` 里?**
5. **协议是否一致?** 试运行的当前 origin 和目标 baseUrl 必须**同时是
   https 或同时是 http**(见 §3.3 mixed-content)。
6. **如果用 OAuth2 Client Credentials**:除了业务接口,**token 端点
   本身**也要能跨域 —— 否则换 token 那一步就先挂了。token 端点的
   响应头同样需要 `Access-Control-Allow-Origin`。
7. **如果依赖 Cookie**:确认双方都满足 §3.4 的条件;`Allow-Origin`
   必须是精确域名,不能是 `*`。

---

## 5. 生产环境推荐部署形态

CORS 最稳妥的解法是 **从一开始就让 ui 与业务服务同源或同二级域名**,
任何方案选其一都行:

- **同二级域名 + 子域分流(推荐)**

  ```
  apidoc.<corp>.com    →  csap-apidoc-ui
  api.<corp>.com       →  业务服务
  ```

  业务服务只需配 `Access-Control-Allow-Origin: https://apidoc.<corp>.com`
  一行,后续新增接口零额外工作。

- **同域反向代理**

  ```
  https://<corp>.com/apidoc/*   →  csap-apidoc-ui
  https://<corp>.com/api/*      →  业务服务
  ```

  浏览器侧根本就是同源,没有 CORS。代价是要在反向代理里维护两条
  rewrite 规则。

- **避免使用 `Access-Control-Allow-Origin: *`**:

  不带凭据的公开接口下勉强可用,带 token / cookie 的接口浏览器会
  拒绝(规范要求 `Allow-Credentials: true` 时不能配 `*`)。

---

## 6. 还是搞不定?

- 把 DevTools Network 面板的失败请求 → 右键 → `Copy as cURL`,带上
  完整响应头(尤其是 `Access-Control-*` 字段)。
- 同时贴 ui 端的 origin(`location.origin`)与目标 url。
- 如果是 preflight 失败,**单独抓那条 OPTIONS 的响应头**,是定位
  问题最快的线索。
