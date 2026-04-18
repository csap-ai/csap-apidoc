package ai.csap.apidoc.model;

import com.google.common.collect.ComparisonChain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 文档资源
 * @dataTime 2020年-03月-01日 17:33:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocResource implements Comparable<CsapDocResource> {
    /**
     * 标题
     */
    private String name;
    /**
     * 路径
     */
    private String url;
    /**
     * 版本
     */
    private String version;

    @Override
    public int compareTo(CsapDocResource o) {
        return ComparisonChain.start()
                .compare(this.version, o.version)
                .compare(this.name, o.name)
                .result();
    }
}
