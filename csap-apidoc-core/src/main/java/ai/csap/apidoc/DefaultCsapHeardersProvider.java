package ai.csap.apidoc;

import java.util.Collections;
import java.util.List;

import ai.csap.apidoc.model.CsapDocMethodHeaders;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 默认的全局头部文件实现类
 *
 * @Author ycf
 * @Date 2023/3/21 18:12
 */
@Data
@Accessors(chain = true)
public final class DefaultCsapHeardersProvider implements CsapHeadersProvider {


    @Override
    public List<CsapDocMethodHeaders> get() {
        return Collections.emptyList();
    }
}
