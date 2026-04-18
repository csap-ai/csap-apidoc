package ai.csap.apidoc;

import java.util.List;

import com.google.common.base.Supplier;

import ai.csap.apidoc.model.CsapDocModelController;

/**
 * @author yangchengfu
 * @description 全局接口
 * @dataTime 2020年-03月-01日 17:41:00
 **/
public interface GlobalDocModelControllerProvider extends Supplier<List<CsapDocModelController>> {
}
