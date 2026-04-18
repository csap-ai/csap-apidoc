package ai.csap.validation.factory;

import ai.csap.apidoc.type.ModelType;
import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static ai.csap.apidoc.util.IValidate.DOT;
import static ai.csap.apidoc.util.IValidate.EMPTY;


/**
 * 数据验证基础模型.
 * <p>Created on 2021/1/22
 *
 * @author yangchengfu
 * @since 1.0
 */
@Data
@NoArgsConstructor
public class BaseModel implements Cloneable {
    /**
     * 当前参数泛型列表
     */
    private List<Type> types;
    /**
     * 当前key累加名称
     */
    private StringBuilder keyName = new StringBuilder();
    /**
     * 累加的名称
     */
    private String appendName = "";
    /**
     * 当前参数名称
     */
    private String paramName;
    /**
     * 键名称
     */
    private String key;
    /**
     * 当前模型class
     */
    private Class<?> modelClass;
    /**
     * 当前验证字段对象
     */
    private Map<String, Validate.ValidateField> validateField;

    private Class<?> field;

    /**
     * 对象类型
     */
    private ModelType modelType;

    private Method method;
    public static BaseModel build() {
        return new BaseModel();
    }

    public BaseModel field(Class<?> field) {
        this.field = field;
        return this;
    }

    public BaseModel appendName(String name) {
        this.appendName = this.appendName + name + DOT;
        return this;
    }

    public void clearAppendName() {
        this.appendName = EMPTY;
    }

    public String getSplitStartAppendName() {
        List<String> list = Lists.newArrayList(this.appendName.split("\\."));
        list.remove(0);
        if (CollectionUtil.isEmpty(list)) {
            return EMPTY;
        }
        return String.join(".", list) + DOT;
    }

    public BaseModel modelType(ModelType modelType) {
        this.modelType = modelType;
        return this;
    }

    public BaseModel types(List<Type> types) {
        this.types = types;
        return this;
    }

    public BaseModel addTypes(List<Type> types) {
        if (this.types == null) {
            this.types = Lists.newArrayList(types);
        } else {
            this.types.addAll(types);
        }
        return this;
    }

    public BaseModel validateField(Map<String, Validate.ValidateField> validateField) {
        this.validateField = validateField;
        return this;
    }

    public BaseModel appendKeyName(String name) {
        keyName.append(name).append(DOT);
        return this;
    }

    public BaseModel keyName(String name) {
        if (keyName.length() > 0) {
            keyName.setLength(0);
        }
        keyName.append(name).append(DOT);
        return this;
    }

    public BaseModel key(String name) {
        key = name;
        return this;
    }
    public BaseModel method(Method method) {
        this.method = method;
        return this;
    }
    public BaseModel modelClass(Class<?> modelClass) {
        this.modelClass = modelClass;
        return this;
    }

    public BaseModel addKey(String name) {
        key = key == null ? name : key + DOT + name;
        return this;
    }

    public String toStringKeyName() {
        return keyName.substring(0, keyName.length() - 1);
    }

    // 重写clone()方法
    @Override
    public BaseModel clone() {
        try {
            return (BaseModel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
