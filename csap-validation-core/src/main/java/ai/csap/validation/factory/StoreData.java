package ai.csap.validation.factory;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段验证运行时需要存储的数据.
 * <p>Created on 2021/3/18
 *
 * @author yangchengfu
 * @since 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StoreData {
    /**
     * map数据
     */
    private Map.Entry<String, Validate.ValidateField> mapData;
    /**
     * 当前数据
     */
    private Object value;

}
