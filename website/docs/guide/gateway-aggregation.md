# 网关聚合

本页说明如何在一个文档页面里切换查看多个服务的接口文档：需要的配置、页面加载文档的方式，以及对服务地址和路由的要求。

:::info 适用范围
聚合入口的 `csap.apidoc.resources` 是 **Java 版**的配置,入口应用目前用 Java 版搭建。下游服务不限语言,只要提供 `/csap/apidoc/parent` 和 `/csap/apidoc/method`(Go 版同样提供)。
:::

## 工作方式

聚合不在服务端合并文档。每个服务各自生成并提供自己的文档接口，聚合入口只负责告诉页面「有哪些服务、各自的文档地址是什么」，页面再按需去各服务取文档：

1. 浏览器打开聚合入口的 `/csap-api.html`，页面请求入口自身的 `GET /csap/apidoc/parent`。
2. 响应里的 `resources` 字段就是配置的服务列表，页面用它填充顶部的「切换服务」下拉框。
3. 在下拉框里选中一个服务后，页面请求该服务的 `url`（即它的 `/csap/apidoc/parent`），用返回的接口树替换左侧列表。
4. 点开某个接口时，页面请求该服务的 `/csap/apidoc/method` 获取详情；调试和导出也以该服务的地址为基准。

```
浏览器 ──▶ 聚合入口  /csap-api.html
          │         /csap/apidoc/parent          （返回 resources）
          │
          ├─▶ /order-api/csap/apidoc/parent      ──▶ 订单服务 /csap/apidoc/parent
          ├─▶ /order-api/csap/apidoc/method      ──▶ 订单服务 /csap/apidoc/method
          └─▶ /order-api/orders/{id}（调试）     ──▶ 订单服务 /orders/{id}
```

## 配置

### 1. 每个服务

每个下游服务按[快速开始](/guide/quick-start)正常接入 CSAP Apidoc，保证自己的 `/csap/apidoc/parent` 和 `/csap/apidoc/method` 可以访问即可，不需要额外配置。

### 2. 聚合入口

聚合入口本身也是一个接入了 `csap-apidoc-boot-starter` 的 Spring Boot 应用，并且启动类上要有 `@EnableApidoc`：自动配置只在存在 `@EnableApidoc` 时才生效，否则不会注册文档接口。入口应用自己没有需要文档化的接口时，`@EnableApidoc` 可以不填扫描包。

然后在入口应用的配置里列出各服务：

```yaml
csap:
  apidoc:
    resources:
      - name: 订单服务
        url: /order-api/csap/apidoc/parent
        version: v1
      - name: 用户服务
        url: /user-api/csap/apidoc/parent
        version: v1
```

| 字段 | 说明 |
| --- | --- |
| `name` | 下拉框里显示的名称 |
| `url` | 该服务 `/csap/apidoc/parent` 的访问地址，格式要求见下文 |
| `version` | 会原样出现在接口返回的 `resources` 里，页面目前不使用它 |

配置类是 `ai.csap.apidoc.model.CsapDocResource`，由默认的 `CsapResourcesProvider` 从 `csap.apidoc.resources` 读取。需要从注册中心等地方动态生成服务列表时，可以自己提供一个 `CsapResourcesProvider` Bean，见 [Java 扩展点](/api/java)。

### 3. 路由

页面所有请求都发往聚合入口的同一个源，所以入口所在的网关或反向代理要把带前缀的路径转发到对应服务。对每个服务都要满足：

- `/<前缀>/csap/apidoc/parent` 能到达该服务的 `/csap/apidoc/parent`；
- `/<前缀>/csap/apidoc/method` 能到达该服务的 `/csap/apidoc/method`；
- `/<前缀>/<接口路径>` 能到达该服务的 `<接口路径>`（在线调试会用到）。

以 Spring Cloud Gateway 为例，去掉第一段前缀再转发：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-api
          uri: lb://order-service
          predicates:
            - Path=/order-api/**
          filters:
            - StripPrefix=1
```

如果下游服务本身配置了 `server.servlet.context-path: /order-api`，则不需要去掉前缀。

## `url` 的格式要求

页面把 `url` 末尾的 `/csap/apidoc/parent`（或 `/csap/apidoc`）去掉，得到该服务的基础地址，再拼出其他地址：

- 接口详情：`<基础地址>/csap/apidoc/method`
- 在线调试的默认 URL：`<基础地址><接口路径>`
- 导出时的服务地址：`<基础地址>`

1.1.1 起，下面这些写法都可以用，相对 / 绝对形式和全部前缀段都会保留，查询串（如 `?token=...`）也会带到详情请求上：

| 写法 | 详情请求 |
| --- | --- |
| `/order-api/csap/apidoc/parent`（单段前缀） | `/order-api/csap/apidoc/method` |
| `/gw/v2/order-api/csap/apidoc/parent`（多段前缀） | `/gw/v2/order-api/csap/apidoc/method` |
| `http://order-service:8081/csap/apidoc/parent`（绝对地址） | `http://order-service:8081/csap/apidoc/method`，需要对方允许跨域 |
| `/csap/apidoc/parent`（无前缀） | `/csap/apidoc/method`，即聚合入口自己 |

:::warning 1.1.0 的限制
1.1.0 的页面只取 `url` 按 `/` 切分后的第一段作为前缀，并拼成不带开头 `/` 的相对地址。绝对地址、多段前缀、无前缀的写法在 1.1.0 中都取不到接口详情，在线调试的地址还会重复拼接服务前缀。请升级到 1.1.1。
:::

## 切换服务后的行为

- 选中服务后，左侧接口树、接口详情、[在线调试](/guide/testing)的默认 URL 和[导出](/guide/export)都以该服务为准。在线调试的默认 URL 是「`url` 去掉末尾 `/csap/apidoc/parent`」再加上接口路径，例如 `/order-api/orders/{id}`。
- 服务列表只在页面首次加载时读取一次。修改 `resources` 后需要刷新页面。
- 首次加载时左侧显示的是聚合入口自己的接口。切换到其他服务后，要回到入口，可以刷新页面；也可以在 `resources` 里给入口自己加一项，`url` 写 `/csap/apidoc/parent`（1.1.1 起可用）。
- 在线调试里「服务」作用域的全局请求头、鉴权方案的服务绑定，都以这里选中的服务 `url` 作为服务标识。

## 跨域

按上面的方式通过同一个网关访问时，页面、文档接口和被调试的接口同源，不涉及跨域。只有把 `url` 写成其他域名的地址时，才需要对方服务允许跨域（1.1.0 中这种写法还取不到接口详情）。

## 关于 WebFlux 网关

CSAP Apidoc 的文档接口和页面是按 Spring MVC 应用提供的（见[安装](/guide/installation)），仓库里的示例工程也都是 Spring MVC。直接把聚合入口放在 Spring Cloud Gateway（WebFlux）应用里未经验证。稳妥的做法是用一个普通的 Spring MVC 应用作为聚合入口，由网关或 Nginx 把 `/csap-api.html`、`/assets/**`、`/favicon.ico` 和 `/csap/apidoc/parent` 转发给它，其余前缀转发给各个服务。
