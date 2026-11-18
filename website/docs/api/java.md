# Java 扩展点

本页列出使用者可以自己实现或替换的接口与 Bean，说明每个扩展点在什么时候被调用、怎么注册，并给出最小示例。

## 总览

| 扩展点 | 用途 | 注册方式 |
| --- | --- | --- |
| `CsapResourcesProvider` | 提供网关聚合的服务列表 | 声明同类型 Bean，替换默认实现 |
| `CsapHeadersProvider` | 给接口追加公共请求头 | 声明同类型 Bean，替换默认实现 |
| `GlobalCsapRequestParamProvider` | 给分页类接口追加公共请求参数 | 声明同类型 Bean，替换默认实现 |
| `GlobalCsapResponseParamProvider` | 公共返回参数（目前未使用） | 声明同类型 Bean |
| `ParamGroupStrategy` | 字段规则来源（是否出现、必填、校验） | 继承内置实现并声明 Bean |
| `IMethodRequest` / `IMethodResponse` | 请求 / 返回参数的解析方式 | 声明 Bean，并在 `@EnableApidoc` 里按名称选用 |

这些类型都在 `csap-apidoc-core` 中，引入 `csap-apidoc-boot-starter` 即可使用。下面的示例都放在一个普通的 `@Configuration` 类里注册。

## CsapResourcesProvider

```java
package ai.csap.apidoc;

public interface CsapResourcesProvider extends Supplier<List<CsapDocResource>> {
}
```

提供[网关聚合](/guide/gateway-aggregation)的服务列表，结果出现在 `/csap/apidoc`、`/csap/apidoc/parent` 返回的 `resources` 字段里。默认实现 `BaseCsapResourcesProvider` 读取 `csap.apidoc.resources` 配置。自动配置带有 `@ConditionalOnMissingBean(CsapResourcesProvider.class)`，声明自己的 Bean 后默认实现不再注册。

适用场景：服务列表需要从注册中心、数据库等地方动态生成。

```java
import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.CsapResourcesProvider;
import ai.csap.apidoc.model.CsapDocResource;

@Configuration
public class ApidocResourcesConfig {

    @Bean
    public CsapResourcesProvider csapResourcesProvider() {
        return () -> Arrays.asList(
                CsapDocResource.builder().name("订单服务").url("/order-api/csap/apidoc/parent").version("v1").build(),
                CsapDocResource.builder().name("用户服务").url("/user-api/csap/apidoc/parent").version("v1").build());
    }
}
```

:::tip 调用时机
`get()` 在生成文档时调用一次，结果随文档一起缓存。开启 `csap.apidoc.devtool.enabled` 时，每次按类重新扫描也会再调用一次。它不是每个请求都调用，服务列表变化后通常需要重启才能反映到页面上。`url` 的格式要求见[网关聚合](/guide/gateway-aggregation)。
:::

## CsapHeadersProvider

```java
package ai.csap.apidoc;

public interface CsapHeadersProvider extends Supplier<List<CsapDocMethodHeaders>> {
}
```

返回的请求头会追加到接口文档的请求头列表（`methodHeaders`）里。默认实现 `DefaultCsapHeardersProvider` 返回空列表；自动配置带有 `@ConditionalOnMissingBean(CsapHeadersProvider.class)`。

```java
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.CsapHeadersProvider;
import ai.csap.apidoc.model.CsapDocMethodHeaders;

@Configuration
public class ApidocHeadersConfig {

    @Bean
    public CsapHeadersProvider csapHeadersProvider() {
        return () -> Collections.singletonList(
                CsapDocMethodHeaders.builder()
                        .key("X-Tenant-Id")
                        .value("")
                        .example("demo")
                        .required(true)
                        .description("租户标识")
                        .build());
    }
}
```

:::warning 生效范围
公共请求头追加在 `@ApiOperation(headers = …)` 声明的请求头之后。`headers` 的默认值含一个 `Content-Type`，所以一般接口都会带上公共请求头；只有显式写成 `headers = {}` 的接口不会追加。另外它只影响文档展示，不会让在线调试自动发送这些请求头；调试时的公共请求头请在页面的「全局请求头」里配置，见[在线调试](/guide/testing)。
:::

## GlobalCsapRequestParamProvider

```java
package ai.csap.apidoc;

public interface GlobalCsapRequestParamProvider extends Supplier<Map<String, List<CsapDocModel>>> {
    @Override
    default Map<String, List<CsapDocModel>> get() {
        return GlobalCsapRequestParamProvider.defaultParam();
    }
}
```

给分页类接口统一追加请求参数。生成每个接口的文档时：

1. 先看方法名是否包含 `csap.apidoc.result.request-page-method` 里的任一字符串（默认只有 `Page`）；
2. 包含的话，把 `get()` 返回的 Map 里「键是方法名子串」的那些参数模型追加到该接口的请求参数里。

接口上的静态方法 `GlobalCsapRequestParamProvider.defaultParam()` 返回内置的默认参数，默认实现 `DefaultGlobalCsapRequestParamProvider` 直接使用它：在键 `Page` 下提供 `isSearchCount`、`pageSize`、`currentPage`、`column`、`asc` 五个 Query 参数。自动配置带有 `@ConditionalOnMissingBean`。

下面的示例把分页参数换成 `pageNum`、`pageSize`。因为接口只有默认方法、没有抽象方法，不能写成 Lambda，需要写一个实现类：

```java
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.GlobalCsapRequestParamProvider;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

@Configuration
public class ApidocPageParamConfig {

    @Bean
    public GlobalCsapRequestParamProvider globalCsapRequestParamProvider() {
        return new PageParamProvider();
    }

    static class PageParamProvider implements GlobalCsapRequestParamProvider {

        @Override
        public Map<String, List<CsapDocModel>> get() {
            CsapDocModel page = CsapDocModel.builder()
                    .name("com.example.PageQuery")
                    .value("分页参数")
                    .description("分页参数")
                    .modelType(ModelType.OBJECT)
                    .global(true)
                    .parameters(Arrays.asList(
                            queryParam("pageNum", "Integer", "页码", "1"),
                            queryParam("pageSize", "Integer", "每页条数", "20")))
                    .build();
            return Collections.singletonMap("Page", Collections.singletonList(page));
        }

        private static CsapDocParameter queryParam(String name, String dataType, String desc, String example) {
            return CsapDocParameter.builder()
                    .name(name)
                    .value(desc)
                    .description(desc)
                    .dataType(dataType)
                    .paramType(ParamType.QUERY)
                    .required(false)
                    .example(example)
                    .defaultValue(example)
                    .force(true)
                    .build();
        }
    }
}
```

想让方法名里含 `List` 的接口也带上这些参数，在 Map 里再加一个 `List` 键，同时配置：

```yaml
csap:
  apidoc:
    result:
      request-page-method: [Page, List]
```

## GlobalCsapResponseParamProvider

```java
package ai.csap.apidoc;

public interface GlobalCsapResponseParamProvider extends Supplier<Map<String, List<CsapDocModel>>> {
    @Override
    default Map<String, List<CsapDocModel>> get() {
        return GlobalCsapResponseParamProvider.defaultParam();
    }
}
```

默认实现（静态方法 `GlobalCsapResponseParamProvider.defaultParam()`）描述了 `ApidocResult` 的 `code`、`message`、`data`、`language`、`time` 五个字段。自动配置会注册它（带 `@ConditionalOnMissingBean`），但 **目前的文档生成流程没有调用它**，替换它不会改变任何文档输出。这里列出只是为了说明它的现状。

## ParamGroupStrategy

`ai.csap.apidoc.strategy.ParamGroupStrategy` 决定「某个字段在某个接口里」的规则：是否出现、是否必填、参数位置、校验规则。`@EnableApidoc(paramType = …)` 选择使用哪一个。规则如何与注解信息合并见[架构](/guide/architecture)。

| `paramType` | 内置实现 | 自动配置条件 |
| --- | --- | --- |
| `ANNOTATION` | `ai.csap.apidoc.AnnotationParamGroupStrategy` | `@ConditionalOnMissingBean` |
| `YAML` | `ai.csap.apidoc.strategy.YamlParamGroupStrategy` | `@ConditionalOnMissingBean` |
| `SQL_LITE` | `ai.csap.apidoc.strategy.SQLiteParamGroupStrategy` | 无条件注册 |

`ApidocContext` 按 `strategyType().getName()` 索引这些 Bean，而 `paramType` 只能取 `ApiStrategyType` 枚举里的值，所以不能新增一种「名字」。实际可行的扩展方式是**继承内置实现、覆盖个别方法，再以内置实现的类型声明 Bean**，让自动配置里的默认 Bean 因 `@ConditionalOnMissingBean` 而跳过。

主要方法：

| 方法 | 调用时机 | 返回 `null` 的含义 |
| --- | --- | --- |
| `requestBasicParams` | 处理方法上的简单参数（如 `@RequestParam Long id`） | 没有额外规则 |
| `paramRequestGroup` | 处理请求对象的每个字段 | 该字段不出现在这个接口的请求参数里（除非 `forceReq = true`） |
| `paramResponseGroup` | 处理返回对象的每个字段 | 该字段不出现在这个接口的返回参数里（除非 `forceRep = true`）；返回非 `null` 时还要看 `include` |

示例：注解策略下，返回对象里没有为当前方法配置 `@Group` 的字段默认也展示出来。

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.AnnotationParamGroupStrategy;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.validation.factory.IValidateFactory;

@Configuration
public class ApidocParamStrategyConfig {

    @Bean
    public AnnotationParamGroupStrategy annotationParamGroupStrategy(ObjectProvider<IValidateFactory> validateFactory) {
        return new ShowAllResponseFieldsStrategy(validateFactory.getIfAvailable());
    }

    static class ShowAllResponseFieldsStrategy extends AnnotationParamGroupStrategy {

        ShowAllResponseFieldsStrategy(IValidateFactory validateFactory) {
            super(validateFactory);
        }

        @Override
        public ParamGroupMethodProperty.ParamDataValidate paramResponseGroup(StrategyModel strategyModel,
                                                                             String keyName,
                                                                             CsapDocMethod docMethod,
                                                                             Method method,
                                                                             Field field) {
            ParamGroupMethodProperty.ParamDataValidate rule =
                    super.paramResponseGroup(strategyModel, keyName, docMethod, method, field);
            if (rule != null) {
                return rule;
            }
            return ParamGroupMethodProperty.ParamDataValidate.builder()
                    .required(false)
                    .include(true)
                    .build();
        }
    }
}
```

:::warning
这里的规则同时会登记到 `IValidateFactory`，影响运行时的返回字段过滤，不只是文档展示。修改前请先了解[参数校验](/guide/validation)和[存储策略](/guide/storage-strategies)里的过滤机制。
:::

## IMethodRequest / IMethodResponse

```java
public interface IMethodRequest extends IMethodBaseStrategy {
    List<CsapDocModel> handle(CsapDocMethod docMethod, Type[] classes, Method method,
                              TypeVariableModel typeVariableModel, HandlerMethod handlerMethod,
                              StrategyModel strategyModel);
}

public interface IMethodResponse extends IMethodBaseStrategy {
    List<CsapDocModel> handle(Method method, CsapDocMethod docMethod,
                              TypeVariableModel typeVariableModel, HandlerMethod handlerMethod,
                              StrategyModel strategyModel);
}
```

两者都在 `ai.csap.apidoc.service` 包下，`IMethodBaseStrategy` 只有一个 `String name()`。扫描每个接口时，按 `@EnableApidoc` 的 `requestType`、`responseType` 找到同名的 Bean 来解析请求参数和返回参数。内置实现是 `ai.csap.apidoc.service.method.DefaultMethodRequest`、`DefaultMethodResponse`，名称都是 `default`（`ApiParamStrategyType.DEFAULT_TYPE`）。

两点需要注意：

- `handle` 的返回值没有被使用，解析结果必须直接写进 `docMethod.getRequest()` / `docMethod.getResponse()`。内置实现就是这样做的。
- 名称找不到对应 Bean 时会抛出 `get xxx methodRequest is null` 之类的异常，该接口的文档生成失败。

示例：在默认解析结果的基础上，给没有描述的返回字段补上字段名称。

```java
import java.lang.reflect.Method;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.service.method.DefaultMethodResponse;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.validation.factory.IValidateFactory;

@Configuration
public class ApidocMethodResponseConfig {

    @Bean
    public DescribedMethodResponse describedMethodResponse(ObjectProvider<IValidateFactory> validateFactory,
                                                           ScannerPackageConfig packageConfig) {
        return new DescribedMethodResponse(validateFactory.getIfAvailable(), packageConfig);
    }

    public static class DescribedMethodResponse extends DefaultMethodResponse {

        public static final String NAME = "described";

        public DescribedMethodResponse(IValidateFactory validateFactory, ScannerPackageConfig packageConfig) {
            super(validateFactory, packageConfig);
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public List<CsapDocModel> handle(Method method, CsapDocMethod docMethod, TypeVariableModel typeVariableModel,
                                         HandlerMethod handlerMethod, StrategyModel strategyModel) {
            List<CsapDocModel> models = super.handle(method, docMethod, typeVariableModel, handlerMethod, strategyModel);
            for (CsapDocModel model : docMethod.getResponse()) {
                if (model.getParameters() == null) {
                    continue;
                }
                for (CsapDocParameter parameter : model.getParameters()) {
                    String description = parameter.getDescription();
                    if (description == null || description.isEmpty()) {
                        parameter.setDescription(parameter.getValue());
                    }
                }
            }
            return models;
        }
    }
}
```

然后在启动类上按名称选用：

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

@SpringBootApplication
@EnableApidoc(value = "com.example", responseType = ApidocMethodResponseConfig.DescribedMethodResponse.NAME)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

`requestType` 的用法相同，继承 `DefaultMethodRequest` 即可。

## 不建议依赖的类型

下面这些类型虽然是 public 的，但目前不适合作为扩展点：

| 类型 | 原因 |
| --- | --- |
| `ai.csap.apidoc.GlobalDocModelControllerProvider` | 只有接口定义，框架内没有任何地方使用 |
| `ai.csap.apidoc.DevtoolsClass` | 默认实现的 `devtools()` 返回 `false`；改为返回 `true` 后，隐藏接口的判断会读取一个通常为 `null` 的字段，可能导致扫描出错 |
| `ai.csap.apidoc.service.standard.PostmanConverterService` | 没有注册为 Bean，需要时直接 `new`，见[导出](/guide/export) |
| `ai.csap.apidoc.service.standard.OpenApiConverterService` | 没有注册为 Bean，需要时直接 `new`；1.2.0 起请用 `convertToOpenApi(CsapDocResponse)`，旧的 `CsapDocParentResponse` 重载只处理 `globalApiList`，见[导出](/guide/export) |
