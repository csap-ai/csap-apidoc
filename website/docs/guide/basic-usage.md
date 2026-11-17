# 基础使用

本页给出一个完整的 Controller + 请求 / 返回模型示例，并说明它在文档页面 `/csap-api.html` 上显示成什么样。依赖和 `@EnableApidoc` 的写法见 [快速开始](/guide/quick-start)。

示例以 Spring Boot 3 为准（校验注解使用 `jakarta.validation`），并用 Lombok 生成 getter / setter，和仓库里的示例工程一致。

## 示例代码

### 统一返回结构

```java
package com.example.demo.common;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "统一返回结构")
public class Result<T> {

    @ApiModelProperty(value = "业务码", example = "0", forceRep = true)
    private Integer code;

    @ApiModelProperty(value = "提示信息", example = "ok", forceRep = true)
    private String message;

    @ApiModelProperty(value = "业务数据", forceRep = true)
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setMessage("ok");
        result.setData(data);
        return result;
    }
}
```

`Result<T>` 的字段都写了 `forceRep = true`，所以任何返回 `Result<...>` 的接口都会显示这三个字段，`data` 的结构按实际泛型（如 `Result<Order>`）展开。

### 枚举

```java
package com.example.demo.order;

import ai.csap.apidoc.annotation.EnumMessage;
import ai.csap.apidoc.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatus {
    CREATED(1, "已创建"),
    PAID(2, "已支付"),
    CANCELLED(3, "已取消");

    @EnumValue
    private final Integer code;

    @EnumMessage
    private final String message;
}
```

`@EnumValue` 标出枚举的取值字段，`@EnumMessage` 标出说明字段。模型中类型为 `OrderStatus` 的字段，文档里会列出全部枚举值及其说明。

### 订单模型

```java
package com.example.demo.order;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@ApiModel(description = "订单")
public class Order {

    @ApiModelProperty(value = "订单 ID", example = "1001", forceRep = true)
    private Long id;

    @ApiModelProperty(value = "商品名称", required = true, example = "机械键盘",
            forceReq = true, forceRep = true)
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 64, message = "商品名称最长 64 个字符")
    private String productName;

    @ApiModelProperty(value = "购买数量", required = true, example = "2",
            forceReq = true, forceRep = true)
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    private Integer quantity;

    @ApiModelProperty(value = "订单状态", example = "1", forceRep = true)
    private OrderStatus status;

    @ApiModelProperty(value = "内部备注", hidden = true)
    private String internalRemark;
}
```

### Controller

```java
package com.example.demo.order;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;
import com.example.demo.common.Result;

@RestController
@RequestMapping("/api/orders")
@Api(value = "订单管理", description = "订单的查询、创建与删除", tags = {"订单"})
public class OrderController {

    @GetMapping
    @ApiOperation(value = "查询订单列表", description = "按商品名称关键字查询")
    public Result<List<Order>> list(
            @ApiProperty(value = "商品名称关键字", example = "键盘")
            @RequestParam(required = false) String keyword,
            @ApiProperty(value = "最多返回条数", defaultValue = "20", example = "20")
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(new ArrayList<>());
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "查询订单详情")
    public Result<Order> detail(
            @ApiProperty(value = "订单 ID", required = true, example = "1001")
            @PathVariable Long id) {
        Order order = new Order();
        order.setId(id);
        order.setProductName("机械键盘");
        order.setQuantity(2);
        order.setStatus(OrderStatus.CREATED);
        return Result.ok(order);
    }

    @PostMapping
    @ApiOperation(value = "创建订单", description = "创建一个新订单，返回带 ID 的订单")
    public Result<Order> create(@RequestBody Order order) {
        order.setId(1001L);
        order.setStatus(OrderStatus.CREATED);
        return Result.ok(order);
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "删除订单", hidden = true)
    public Result<Void> delete(@PathVariable Long id) {
        return Result.ok(null);
    }
}
```

## 注解各自起什么作用

| 位置 | 注解 | 本例中的作用 |
| --- | --- | --- |
| 类 | `@Api` | 接口分组名称「订单管理」、描述和标签；没有它整个类不会出现在文档中 |
| 方法 | `@ApiOperation` | 接口名称和描述；`hidden = true` 的「删除订单」不在文档页面中显示 |
| 方法参数 | `@ApiProperty` | 简单参数的说明、示例、默认值、是否必填；`@PathVariable` 参数自动识别为 Path 参数，其余默认是 Query 参数 |
| 模型类 | `@ApiModel` | 模型说明 |
| 模型字段 | `@ApiModelProperty` | 字段说明、示例、是否必填，以及字段在请求 / 返回中是否显示 |
| 模型字段 | `@NotBlank`、`@Size`、`@Min` 等 | 作为校验规则显示在文档中 |
| 枚举字段 | `@EnumValue`、`@EnumMessage` | 生成枚举值列表 |

注解的全部属性见 [注解](/guide/annotations)。

## 字段在哪些接口中出现

同一个 `Order` 既是「创建订单」的请求体，也是多个接口的返回数据。按上面的写法，各字段的显示结果是：

| 字段 | 「创建订单」请求参数 | 返回数据中的 `data` | 原因 |
| --- | --- | --- | --- |
| `id` | 不显示 | 显示 | 只有 `forceRep` |
| `productName` | 显示（必填） | 显示 | `forceReq` + `forceRep` |
| `quantity` | 显示（必填） | 显示 | `forceReq` + `forceRep` |
| `status` | 不显示 | 显示，附枚举值 | 只有 `forceRep` |
| `internalRemark` | 不显示 | 不显示 | `hidden = true` |

`forceReq` / `forceRep` 会让字段在**所有**用到该模型的接口里显示。如果需要「详情返回全部字段、列表只返回部分字段」这种按接口区分的效果，用 `@ApiModelProperty` 的 `groups` 按方法名指定，或者改用 YAML / SQLite 策略并在 DevTools 中勾选，见 [存储策略](/guide/storage-strategies)。

## 文档页面上看到什么

启动应用，打开 `http://localhost:8080/csap-api.html`。

### 顶部

- **分组 / 版本**：按 `@Api`、`@ApiOperation` 的 `group`、`version` 筛选接口。本例都使用默认值 `default`。
- **导出文档**：把当前文档导出为 OpenAPI 3.0、Postman Collection 或 Markdown 文件，见 [导出](/guide/export)。
- **切换服务**：配置了 `csap.apidoc.resources` 时出现，用于在多个服务的文档之间切换，见 [网关聚合](/guide/gateway-aggregation)。

### 左侧接口列表

列表顶部有「搜索 API 接口」输入框，按接口名称、描述、标签搜索。下方是「订单管理」分组，里面有三个接口：查询订单列表、查询订单详情、创建订单。「删除订单」因为 `hidden = true` 不出现。

### 右侧接口详情

点开一个接口，右侧分为「参数文档」和「在线测试」两部分。

**参数文档** 中：

- 接口名称、描述、请求方式和路径，例如 `POST /api/orders`。
- 请求参数按位置（Path / Query / Body / Form Data / Header）列出，每个参数显示名称、数据类型、是否必填、默认值、示例和描述。
  - 「查询订单详情」的 `id` 是 Path 参数，必填，示例 `1001`。
  - 「查询订单列表」的 `keyword`、`limit` 是 Query 参数，`limit` 默认值 `20`。
  - 「创建订单」的请求体只有 `productName` 和 `quantity`。
- 带校验注解的字段会显示校验规则，例如 `productName` 显示「不能为空」和「最长 64 个字符」两条规则及其提示信息。
- 返回数据按层级展示：`code`、`message`、`data`，`data` 下是 `id`、`productName`、`quantity`、`status`；`status` 下列出枚举值 1 已创建、2 已支付、3 已取消。
- 「复制为 TypeScript」可以把参数结构生成为 TypeScript 类型定义，复制到前端项目中使用。

**在线测试** 中可以填写参数并点击「发送请求」，页面直接调用接口并显示返回结果，见 [在线调试](/guide/testing)。

## 文档数据接口

页面上的内容都来自应用提供的 JSON 接口，也可以直接调用：

```bash
curl http://localhost:8080/csap/apidoc
curl http://localhost:8080/csap/apidoc/parent
```

全部接口见 [REST 接口](/api/rest)。

:::tip 修改注解后
未开启 DevTools 时，文档在应用启动时生成一次并缓存。修改注解或模型后需要重启应用，页面才会反映变化。
:::

## 下一步

- [注解](/guide/annotations)：全部注解和属性。
- [存储策略](/guide/storage-strategies)：按接口选择字段的三种方式。
- [参数校验](/guide/validation)：让文档中的校验规则在运行时生效。
