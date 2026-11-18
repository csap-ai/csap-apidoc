package ai.csap.apidoc.devtools.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.devtools.ApidocRuleHistoryController;
import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.IMethodRequest;
import ai.csap.apidoc.service.IMethodResponse;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.apidoc.strategy.RuleHistoryProvider;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

/**
 * DevTools 的规则修改记录 / 回滚接口:只在数据库模式可用,其他模式返回明确提示,只读模式回滚返回 1007。
 */
class ApidocRuleHistoryControllerTest {

    private RuleHistoryProvider provider;
    private RuleHistoryProvider redisProvider;

    @BeforeEach
    void setUp() {
        ParamGroupStrategy strategy = mock(ParamGroupStrategy.class, withSettings().extraInterfaces(RuleHistoryProvider.class));
        provider = (RuleHistoryProvider) strategy;
        when(strategy.strategyType()).thenReturn(ApiStrategyType.DATABASE);
        ParamGroupStrategy redis = mock(ParamGroupStrategy.class, withSettings().extraInterfaces(RuleHistoryProvider.class));
        redisProvider = (RuleHistoryProvider) redis;
        when(redis.strategyType()).thenReturn(ApiStrategyType.REDIS);
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansOfType(ApidocStrategy.class)).thenReturn(Collections.emptyMap());
        when(ctx.getBeansOfType(ParamGroupStrategy.class)).thenReturn(Map.of("db", strategy, "redis", redis));
        when(ctx.getBeansOfType(IMethodRequest.class)).thenReturn(Collections.emptyMap());
        when(ctx.getBeansOfType(IMethodResponse.class)).thenReturn(Collections.emptyMap());
        CsapDocConfig docConfig = new CsapDocConfig();
        ApidocContext context = new ApidocContext(null, new ScannerPackageConfig(docConfig), docConfig);
        context.setApplicationContext(ctx);
        context.afterSingletonsInstantiated();
    }

    private static MockMvc mvc(ApiStrategyType paramType) {
        EnableApidocConfig config = new EnableApidocConfig();
        config.setParamType(paramType);
        return MockMvcBuilders.standaloneSetup(new ApidocRuleHistoryController(config)).build();
    }

    @Test
    void listsChangesWithBeforeAfterVersionUserAndTime() throws Exception {
        when(provider.history(eq("a.B.m"), anyInt())).thenReturn(List.of(
                new RuleChange(7, "a.B.m", "{\"request\":{}}", "{\"request\":{\"x\":{}}}", 3, "devtools", 1_700_000_000_000L)));
        mvc(ApiStrategyType.DATABASE).perform(get("/devtools/rules/history").param("ruleKey", "a.B.m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].ruleKey").value("a.B.m"))
                .andExpect(jsonPath("$.data[0].before").value("{\"request\":{}}"))
                .andExpect(jsonPath("$.data[0].after").value("{\"request\":{\"x\":{}}}"))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[0].updatedBy").value("devtools"))
                .andExpect(jsonPath("$.data[0].updatedAt").value(1_700_000_000_000L));
    }

    @Test
    void redisModeUsesTheRedisStrategy() throws Exception {
        when(redisProvider.history(isNull(), anyInt())).thenReturn(List.of(
                new RuleChange(1, "a.B.m", null, "{}", 1, "devtools", 1_700_000_000_000L)));
        MockMvc mvc = mvc(ApiStrategyType.REDIS);
        mvc.perform(get("/devtools/rules/status")).andExpect(jsonPath("$.data.supported").value(true))
           .andExpect(jsonPath("$.data.paramType").value("redis"));
        mvc.perform(get("/devtools/rules/history")).andExpect(jsonPath("$.data[0].ruleKey").value("a.B.m"));
        when(redisProvider.rollback(eq(1L), eq("devtools-rollback"))).thenReturn(2L);
        mvc.perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON).content("{\"changeId\":1}"))
           .andExpect(jsonPath("$.data.version").value(2));
        verify(redisProvider).rollback(1L, "devtools-rollback");
    }

    @Test
    void rollbackUsesTheRollbackUser() throws Exception {
        when(provider.rollback(eq(7L), eq("devtools-rollback"))).thenReturn(9L);
        mvc(ApiStrategyType.DATABASE).perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeId\":7}"))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.rolledBack").value(true))
                .andExpect(jsonPath("$.data.version").value(9));
        verify(provider).rollback(7L, "devtools-rollback");

        when(provider.rollback(eq(8L), eq("devtools-rollback"))).thenReturn(-1L);
        mvc(ApiStrategyType.DATABASE).perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeId\":\"8\"}"))
                .andExpect(jsonPath("$.data.rolledBack").value(false));
    }

    @Test
    void rollbackOfUnknownChangeAndMissingIdAreClearErrors() throws Exception {
        when(provider.rollback(anyLong(), eq("devtools-rollback"))).thenThrow(new IllegalArgumentException("修改记录不存在: 5"));
        mvc(ApiStrategyType.DATABASE).perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeId\":5}"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("修改记录不存在: 5"));
        mvc(ApiStrategyType.DATABASE).perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void readOnlyRollbackReturns1007() throws Exception {
        when(provider.isReadOnly()).thenReturn(true);
        when(provider.rollback(anyLong(), eq("devtools-rollback"))).thenThrow(new RuleStorageNotWritableException("只读"));
        mvc(ApiStrategyType.DATABASE).perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1007"))
                .andExpect(jsonPath("$.success").value(false));
        mvc(ApiStrategyType.DATABASE).perform(get("/devtools/rules/status"))
                .andExpect(jsonPath("$.data.supported").value(true))
                .andExpect(jsonPath("$.data.readOnly").value(true));
    }

    @Test
    void otherModesGetAClearHintInsteadOfAnError() throws Exception {
        for (ApiStrategyType type : new ApiStrategyType[]{ApiStrategyType.YAML, ApiStrategyType.SQL_LITE, ApiStrategyType.ANNOTATION}) {
            MockMvc mvc = mvc(type);
            mvc.perform(get("/devtools/rules/status"))
               .andExpect(jsonPath("$.data.supported").value(false));
            String history = mvc.perform(get("/devtools/rules/history")).andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(false))
                                .andReturn().getResponse().getContentAsString();
            assertThat(history).contains("ApiStrategyType.DATABASE");
            mvc.perform(post("/devtools/rules/rollback").contentType(MediaType.APPLICATION_JSON).content("{\"changeId\":1}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(false));
        }
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.never()).rollback(anyLong(), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.never()).history(isNull(), anyInt());
    }
}
