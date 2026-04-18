package ai.csap.apidoc.type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.function.Function;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

/**
 * 字段方法类型处理
 *
 * @Author ycf
 * @Date 2023/2/23 16:19
 * @Version 1.0
 */
@AllArgsConstructor
public enum FieldMethodHandleType {
    /**
     * 获取
     */
    GET(FieldMethodHandleType::handleTypeGet),
    /**
     * 赋值
     */
    SET(FieldMethodHandleType::handleTypeSet);

    private final Function<Field, MethodHandle> handleFunction;

    public MethodHandle handle(Field field) {
        return handleFunction.apply(field);
    }


    @SneakyThrows
    private static MethodHandle handleTypeSet(Field field) {
        return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectSetter(field);
    }

    @SneakyThrows
    private static MethodHandle handleTypeGet(Field field) {
        return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectGetter(field);
    }

}
