package ai.csap.apidoc;

import java.util.List;

import com.google.common.base.Supplier;

import ai.csap.apidoc.model.CsapDocMethodHeaders;

/**
 * 全局头部文件接口
 *
 * @author yangchengfu
 * @description 全局头部文件接口
 * @dataTime 2020年-03月-01日 17:41:00
 **/
public interface CsapHeadersProvider extends Supplier<List<CsapDocMethodHeaders>> {
}
