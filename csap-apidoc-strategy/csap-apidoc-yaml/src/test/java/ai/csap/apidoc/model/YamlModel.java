package ai.csap.apidoc.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author ycf
 * @Date 2021/9/9 4:21 下午
 * @Version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class YamlModel {
    /**
     * 名称
     */
    private String name;
    /**
     * 年龄
     */
    private Integer age;
    /**
     * 性别
     */
    private Integer sex;
    /**
     * 子信息
     */
    private YamlModel yaml;

    /**
     * 子信息List
     */
    private List<YamlModel> yamls;
}
