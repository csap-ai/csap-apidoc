package ai.csap.apidoc;

import java.util.List;

import com.google.common.base.Supplier;

import ai.csap.apidoc.model.CsapDocResource;

/**
 * 资源数据接口
 *
 * @author yangchengfu
 * @description 资源数据接口
 * @dataTime 2020年-03月-01日 17:41:00
 **/
public interface CsapResourcesProvider extends Supplier<List<CsapDocResource>> {
}
