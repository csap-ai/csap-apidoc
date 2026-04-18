package ai.csap.apidoc;

import java.util.List;

import ai.csap.apidoc.model.CsapDocResource;
import ai.csap.apidoc.properties.CsapDocConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author yangchengfu
 * @description 默认资源信息实现类
 * @dataTime 2020年-03月-01日 18:00:00
 **/
@Data
@Accessors(chain = true)
public final class BaseCsapResourcesProvider implements CsapResourcesProvider {
    private CsapDocConfig csapDocConfig;

    @Override
    public List<CsapDocResource> get() {
        return csapDocConfig.getResources();
    }
}
