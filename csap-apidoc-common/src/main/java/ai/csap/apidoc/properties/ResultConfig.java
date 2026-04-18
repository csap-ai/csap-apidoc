package ai.csap.apidoc.properties;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

import ai.csap.apidoc.core.ApidocResult;

import lombok.Data;

/**
 * @author yangchengfu
 * @description 分页相关参数配置
 * @dataTime 2020年-03月-11日 15:06:00
 **/
@Data
public class ResultConfig {
    /**
     * 请求（默认有页数和排序字段，匹配的方法）
     */
    private List<String> requestPageMethod = Lists.newArrayList("Page");
    /**
     * 返回 带有ResponseManagerModel 总条数，总页数 等数据
     */
    private List<String> responsePageMethod = Lists.newArrayList("Page");
    /**
     * 指定返回的类名称
     */
    private List<String> responseClassName = Arrays.asList("com.csap.framework.core.Page", "com.csap.mybatisplus.page.Page", ApidocResult.class.getName());
    /**
     * 请求包含的忽略字段【必须存在的字段】
     */
    private List<String> requestField = Arrays.asList("size", "current", "column", "asc");
    /**
     * 返回包含的字段【必须存在的字段】
     */
    private List<String> responseField = Arrays.asList("rows", "total", "size", "current", "records", "total", "totalPage", "currentPage", "pages", "pageSize");
    /**
     * 返回公用过滤字段【必须不存在的字段】
     */
    private List<String> responseExcludes = Arrays.asList("pages", "records", "searchCount", "total", "orders");

    public boolean containsPageMethod(String method) {
        return requestPageMethod.stream().anyMatch(method::contains);
    }

    public boolean containsResultPageMethod(String method) {
        return responsePageMethod.stream().anyMatch(method::contains);
    }
}
