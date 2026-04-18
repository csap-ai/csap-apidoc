package ai.csap.apidoc.core;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * A container object which may or may not contain a non-{@code null} value.
 * If a value is present, {@code isPresent()} returns {@code true}. If no
 * value is present, the object is considered <i>empty</i> and
 * {@code isPresent()} returns {@code false}.
 *
 * <p>Additional methods that depend on the presence or absence of a contained
 * value are provided, such as {@link #orElse(Object) orElse()}
 * (returns a default value if no value is present) and
 * {@link #ifPresent(Consumer) ifPresent()} (performs an
 * action if a value is present).
 *
 * <p>This is a <a href="../lang/doc-files/ValueBased.html">value-based</a>
 * class; use of identity-sensitive operations (including reference equality
 * ({@code ==}), identity hash code, or synchronization) on instances of
 * {@code Optional} may have unpredictable results and should be avoided.
 *
 * @param <T> the type of value
 * @author ycf
 * there is a clear need to represent "no result," and where using {@code null}
 * is likely to cause errors. A variable whose type is {@code Optional} should
 * never itself be {@code null}; it should always point to an {@code Optional}
 * instance.
 * @since 1.8
 * <p>
 */
@Slf4j
public final class ApidocOptional<T> extends ApidocOptionalCondition<T, ApidocOptional<T>> implements Serializable {

    /**
     * Common instance for {@code empty()}.
     */
    private static final ApidocOptional<?> EMPTY = new ApidocOptional<>();
    private static final long serialVersionUID = -4967025498616213168L;

    /**
     * 若不为null，则表示存储的值；若为null，则表示不存在值
     */
    private final T value;

    /**
     * 直接获取值
     *
     * @return 结果
     */
    @Override
    T getValue() {
        return value;
    }


    /**
     * 构造一个空实例。
     *
     * @implNote 通常每个VM中仅存在一个空实例，即{@link ApidocOptional#EMPTY}。
     */
    private ApidocOptional() {
        this.value = null;
        super.runCondition();
    }

    /**
     * 返回一个空的{@code Optional}实例。此{@code Optional}不包含任何值。
     *
     * @param <R> 不存在的值的类型
     * @return 一个空的{@code Optional}
     * @apiNote 尽管可能会有这样的诱惑，但应避免通过与{@code Optional.empty()}返回的实例使用{@code ==}进行比较来检测对象是否为空。
     * 无法保证它是单例的。相反，应使用{@link #isPresent()}方法。
     */
    public static <R> ApidocOptional<R> empty() {
        @SuppressWarnings("unchecked") ApidocOptional<R> t = (ApidocOptional<R>) EMPTY;
        return t;
    }

    /**
     * 构造一个描述指定值的实例。
     *
     * @param value 要描述的非{@code null}值
     * @throws NullPointerException 如果值为{@code null}
     */
    private ApidocOptional(T value) {
        this.value = Objects.requireNonNull(value);
        super.runCondition();
    }

    /**
     * 返回一个描述给定非{@code null}值的{@code Optional}。
     *
     * @param value 要描述的值，必须为非{@code null}
     * @param <T>   值的类型
     * @return 一个值已存在的{@code Optional}
     * @throws NullPointerException 如果值为{@code null}
     */
    public static <T> ApidocOptional<T> of(T value) {
        return new ApidocOptional<>(value);
    }

    /**
     * 返回一个描述给定非{@code null}值的{@code Optional}。
     *
     * @param value 要描述的值，值为Optional,值的value必须为非{@code null}
     * @param <T>   值的类型
     * @return 一个值已存在的{@code Optional}
     * @throws NullPointerException 如果值为{@code null}
     */
    public static <T> ApidocOptional<T> of(ApidocOptional<T> value) {
        return new ApidocOptional<>(value.get());
    }

    /**
     * 如果给定值为非{@code null}，则返回一个描述该值的{@code Optional}；
     * 否则返回一个空的{@code Optional}。
     *
     * @param value 可能为{@code null}的待描述值
     * @param <T>   值的类型
     * @return 如果指定值为非{@code null}，则返回一个包含该值的{@code Optional}；
     * 否则返回一个空的{@code Optional}
     */
    public static <T> ApidocOptional<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    /**
     * 创建Optional对象,参数为java.util.Optional
     *
     * @param values 参数
     * @param <T>    操作的泛型
     * @return Optional操作实例
     */
    public static <T> ApidocOptional<T> ofNullable(java.util.Optional<T> values) {
        return ofNullable(values.orElse(null));
    }

    /**
     * 直接返回该值
     *
     * @return 结果
     */
    public T get() {
        return value;
    }

    /**
     * 如果值存在，则返回{@code true}；否则返回{@code false}。
     *
     * @return 若值存在则返回{@code true}，否则返回{@code false}
     */
    public boolean isPresent() {
        return !isEmpty();
    }

    private static <T> boolean isEmpty2(T value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Object[]) {
            return ArrayUtil.isEmpty((Object[]) value);
        } else if (value instanceof Collection) {
            return CollectionUtil.isEmpty((Collection<?>) value);
        } else if (value instanceof Map) {
            return CollectionUtil.isEmpty((Map<?, ?>) value);
        } else if (value instanceof ApidocOptional) {
            return ((ApidocOptional<?>) value).isEmpty();
        } else if (value instanceof java.util.Optional) {
            return ((java.util.Optional<?>) value).isPresent();
        } else {
            return Objects.isNull(value);
        }
    }

    /**
     * 如果值不存在，则返回{@code true}；否则返回{@code false}。
     *
     * @return 若值不存在则返回{@code true}，否则返回{@code false}
     * @since 11
     */
    @Override
    public boolean isEmpty() {
        return isEmpty2(value);
    }

    /**
     * 如果值存在，则使用该值执行给定的操作；否则不执行任何操作。
     *
     * @param action 当值存在时执行的操作
     * @throws NullPointerException 如果值存在但给定的操作为{@code null}
     */
    public ApidocOptional<T> ifPresent(Consumer<? super T> action) {
        if (!isEmpty()) {
            action.accept(value);
        }
        return this;
    }

    /**
     * 如果值存在，则使用该值执行给定的操作；否则执行给定的基于空值的操作。
     *
     * @param action      当值存在时执行的操作
     * @param emptyAction 当值不存在时执行的基于空值的操作
     * @throws NullPointerException 如果值存在但给定的操作参数为{@code null}，
     *                              或者值不存在但给定的空值操作为{@code null}
     * @since 9
     */
    public ApidocOptional<T> ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (!isEmpty()) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
        return this;
    }

    /**
     * 如果值存在，则使用该值执行给定的操作；否则执行给定的基于空值的操作。
     *
     * @param action      当值存在时执行的操作
     * @param emptyAction 当值不存在时执行的基于空值的操作
     * @throws NullPointerException 如果值存在但给定的操作参数为{@code null}，
     *                              或者值不存在但给定的空值操作为{@code null}
     * @since 9
     */
    public ApidocOptional<T> ifPresentOrElse(Consumer<? super T> action, Consumer<? super T> emptyAction) {
        if (!isEmpty()) {
            action.accept(value);
        } else {
            emptyAction.accept(value);
        }
        return this;
    }

    /**
     * 如果值存在，并且该值满足给定的谓词条件，则返回描述该值的{@code Optional}；
     * 否则返回空的{@code Optional}。
     *
     * @param predicate 用于测试值的谓词条件（若值存在）
     * @return 如果值存在且满足谓词条件，则返回描述该值的{@code Optional}；
     * 否则返回空的{@code Optional}
     * @throws NullPointerException 如果谓词为{@code null}
     */
    public ApidocOptional<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (isEmpty()) {
            return this;
        } else {
            return predicate.test(value) ? this : empty();
        }
    }

    /**
     * 如果值存在，则将该值传递给指定的映射函数，并返回一个描述映射结果的{@code Optional}（如同通过{@link #ofNullable}包装）；
     * 否则返回一个空的{@code Optional}。
     *
     * <p>如果映射函数返回{@code null}，则此方法返回一个空的{@code Optional}。
     *
     * @param mapper 用于处理值的映射函数（若值存在）
     * @param <U>    映射函数返回值的类型
     * @return 如果值存在，则返回描述映射结果的{@code Optional}；否则返回空的{@code Optional}
     * @throws NullPointerException 如果映射函数为{@code null}
     * @apiNote 此方法支持对{@code Optional}值进行后处理，无需显式检查返回状态。例如，以下代码遍历URI流，
     * 选择一个尚未处理的URI，并从该URI创建路径，最终返回一个{@code Optional<Path>}：
     *
     * <pre>{@code
     *     Optional<Path> p =
     *         uris.stream().filter(uri -> !isProcessedYet(uri))
     *                       .findFirst()
     *                       .map(Paths::get);
     * }</pre>
     * <p>
     * 这里，{@code findFirst}返回一个{@code Optional<URI>}，然后{@code map}会为存在的目标URI返回一个{@code Optional<Path>}。
     */
    public <U> ApidocOptional<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        if (isEmpty()) {
            return empty();
        } else {
            return ApidocOptional.ofNullable(mapper.apply(value));
        }
    }

    /**
     * 如果值存在，则将该值传递给指定的返回{@code Optional}的映射函数，并返回其结果；
     * 否则返回一个空的{@code Optional}。
     *
     * <p>此方法与{@link #map(Function)}类似，但映射函数的返回值本身已是{@code Optional}，
     * 因此{@code flatMap}不会再进行额外的包装。
     *
     * @param <U>    映射函数返回的{@code Optional}中的值类型
     * @param mapper 用于处理值的映射函数（若值存在）
     * @return 如果值存在，则返回映射函数处理后的结果；否则返回空的{@code Optional}
     * @throws NullPointerException 如果映射函数为{@code null}或返回{@code null}结果
     */
    public <U> ApidocOptional<U> flatMap(Function<T, ApidocOptional<U>> mapper) {
        Objects.requireNonNull(mapper);
        if (isEmpty()) {
            return empty();
        } else {
            return Objects.requireNonNull(mapper.apply(value));
        }
    }

    /**
     * 如果值存在，则返回一个描述该值的{@code Optional}；
     * 否则返回一个由供应函数产生的{@code Optional}。
     *
     * @param supplier 用于产生返回值{@code Optional}的供应函数
     * @return 如果当前{@code Optional}中存在值，则返回描述该值的{@code Optional}；
     * 否则返回供应函数所产生的{@code Optional}
     * @throws NullPointerException 如果供应函数为{@code null}或产生{@code null}结果
     * @since 9
     */
    public ApidocOptional<T> or(Supplier<? extends ApidocOptional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        if (isPresent()) {
            return this;
        } else {
            @SuppressWarnings("unchecked") ApidocOptional<T> r = (ApidocOptional<T>) supplier.get();
            return Objects.requireNonNull(r);
        }
    }

    /**
     * 如果值存在，则返回一个仅包含该值的顺序流{@link Stream}；否则返回一个空的{@code Stream}。
     *
     * @return 将可选值转换为{@code Stream}流
     * @apiNote 此方法可用于将包含可选元素的{@code Stream}转换为仅包含存在值的元素流：
     * <pre>{@code
     *     Stream<Optional<T>> os = ..
     *     Stream<T> s = os.flatMap(Optional::stream)
     * }</pre>
     * @since 9
     */
    public Stream<T> stream() {
        if (isEmpty()) {
            return Stream.empty();
        } else {
            return Stream.of(value);
        }
    }

    /**
     * 断言这个值如果是null就执行自定义函数,否则就返回当前值实例
     *
     * @param other 满足条件的实例对象
     * @return 当前实例
     */
    public T orElse(T other) {
        return !isEmpty() ? value : other;
    }

    /**
     * 断言这个值如果是null就执行自定义函数,否则就返回当前值实例
     *
     * @param supplier 满足条件执行的函数,返回当前实例对象
     * @return 当前实例
     */
    public T orElseGet(Supplier<? extends T> supplier) {
        return !isEmpty() ? value : supplier.get();
    }

    /**
     * 断言这个值如果是null就抛出异常 NoSuchElementException
     *
     * @return 返回当前实例
     */
    public T orElseThrow() {
        if (isEmpty()) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /**
     * 断言这个值如果是null就抛出异常,否则返回当前操作类
     *
     * @param exceptionSupplier 满足条件执行的函数,返回异常实例
     * @return 返回当前实例
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (!isEmpty()) {
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }

    /**
     * 断言这个值如果是null就抛出异常,否则返回当前操作类
     *
     * @param exceptionSupplier 满足条件的异常实例
     * @return 返回当前实例
     */
    @SneakyThrows
    public T orElseThrow(Throwable exceptionSupplier) {
        if (!isEmpty()) {
            return value;
        } else {
            throw exceptionSupplier;
        }
    }

    /**
     * 断言这个值如果是null就执行函数,返回的类型可以自定义
     * 如果不是null就返回空的操作实例
     * 《抛出异常》
     *
     * @param <R>      目标泛型
     * @param supplier 满足条件执行的函数,返回目标类型
     * @return 返回目标操作实例
     */
    public <R> ApidocOptional<R> isNullMap(Supplier<R> supplier) {
        if (isEmpty()) {
            return ApidocOptional.ofNullable(Objects.requireNonNull(supplier).get());
        } else {
            return empty();
        }
    }

    /**
     * 断言这个值如果是null就执行函数,否则什么都不做返回当前类
     * 《抛出异常》
     *
     * @param supplier 满足条件执行的函数,且返回当前类型
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNullElseMap(Supplier<T> supplier) {
        if (isEmpty()) {
            return ApidocOptional.ofNullable(Objects.requireNonNull(supplier).get());
        } else {
            return this;
        }
    }

    /**
     * 断言这个值必须是null
     * 《抛出异常》
     *
     * @param runnable 满足条件执行的函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNull(Runnable runnable) {
        if (isEmpty()) {
            Objects.requireNonNull(runnable).run();
        }
        return this;
    }

    /**
     * 断言这个值必须是null
     * 《抛出异常》
     *
     * @param exceptionSupplier 满足条件执行的类型,返回异常操作类
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNullThrow(Supplier<Throwable> exceptionSupplier) {
        if (isEmpty()) {
            throw exceptionSupplier.get();
        }
        return this;
    }

    /**
     * 断言这个值必须是null
     * 《抛出异常》
     *
     * @param exceptionSupplier 满足条件执行的函数,返回异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNullThrow(Function<T, Throwable> exceptionSupplier) {
        if (isEmpty()) {
            throw exceptionSupplier.apply(value);
        }
        return this;
    }


    /**
     * 断言这个值必须是null
     * 《抛出异常》
     *
     * @param exceptionSupplier 满足条件的异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNullThrow(Throwable exceptionSupplier) {
        if (isEmpty()) {
            throw exceptionSupplier;
        }
        return this;
    }

    /**
     * 满足条件抛异常
     *
     * @param predicate         返回的boolean条件
     * @param exceptionSupplier 满足条件执行的函数,返回异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNullConditionThrow(Predicate<T> predicate, Supplier<Throwable> exceptionSupplier) {
        if (isEmpty()) {
            if (predicate.test(value)) {
                throw exceptionSupplier.get();
            }
        }
        return this;
    }

    /**
     * 满足条件抛异常
     *
     * @param predicate         返回的boolean条件
     * @param exceptionSupplier 抛出的异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNullConditionThrow(Predicate<T> predicate, Function<T, Throwable> exceptionSupplier) {
        if (isEmpty() && Objects.requireNonNull(predicate).test(value)) {
            throw exceptionSupplier.apply(value);
        }
        return this;
    }

    /**
     * 满足条件抛异常
     *
     * @param predicate 返回的boolean条件
     * @param exception 抛出的异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNullConditionThrow(Predicate<T> predicate, Throwable exception) {
        if (isEmpty() && Objects.requireNonNull(predicate).test(value)) {
            throw exception;
        }
        return this;
    }

    /**
     * 是空并且满足条件执行
     *
     * @param predicate 返回的boolean条件
     * @param runnable  满足条件执行的函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNullCondition(Predicate<T> predicate, Runnable runnable) {
        if (isEmpty() && predicate.test(value)) {
            Objects.requireNonNull(runnable).run();
        }
        return this;
    }

    /**
     * 是空并且满足条件执行
     *
     * @param predicate 返回的boolean条件
     * @param consumer  满足条件执行的函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNullCondition(Predicate<T> predicate, Consumer<T> consumer) {
        if (isEmpty() && Objects.requireNonNull(predicate).test(value)) {
            Objects.requireNonNull(consumer).accept(getValue());
        }
        return this;
    }

    /**
     * 是null并且满足条件执行
     *
     * @param predicate 返回的boolean条件
     */
    public <R> ApidocOptional<R> isNullConditionMap(Predicate<T> predicate, Supplier<R> supplier) {
        if (isEmpty() && Objects.requireNonNull(predicate).test(value)) {
            return ApidocOptional.ofNullable(Objects.requireNonNull(supplier).get());
        } else {
            return empty();
        }
    }

    /**
     * 断言这个值必须不是null 抛出异常
     * 《抛出异常》
     *
     * @param exceptionSupplier 满足条件执行的函数,返回异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNotNullThrow(Supplier<Throwable> exceptionSupplier) {
        if (!isEmpty()) {
            throw exceptionSupplier.get();
        }
        return this;
    }

    /**
     * 断言这个值必须不是null 抛出异常
     * 《抛出异常》
     *
     * @param exception 满足条件抛出的异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNotNullThrow(Throwable exception) {
        if (!isEmpty()) {
            throw exception;
        }
        return this;
    }

    /**
     * 断言这个值必须不是null 抛出异常
     * 《抛出异常》
     *
     * @param exceptionSupplier 满足条件执行的函数 函数返回异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNotNullThrow(Function<T, Throwable> exceptionSupplier) {
        if (!isEmpty()) {
            throw exceptionSupplier.apply(value);
        }
        return this;
    }


    /**
     * 断言这个值必须不是null
     * 《抛出异常》
     *
     * @param consumer 满足条件执行的函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNotNull(Consumer<T> consumer) {
        if (!isEmpty()) {
            Objects.requireNonNull(consumer).accept(value);
        }
        return this;
    }

    /**
     * 断言这个值必须不是null
     *
     * @param function 满足条件后执行的函数
     * @param <R>      目标泛型
     * @return 返回目标操作实例, 可以自定义
     */
    public <R> ApidocOptional<R> isNotNullMap(Function<T, R> function) {
        if (!isEmpty()) {
            return ApidocOptional.ofNullable(Objects.requireNonNull(function).apply(getValue()));
        } else {
            return empty();
        }
    }

    /**
     * 断言这个值不是null
     *
     * @param function 满足条件后执行的函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNotNullElseMap(Function<T, T> function) {
        if (!isEmpty()) {
            return ApidocOptional.ofNullable(Objects.requireNonNull(function).apply(getValue()));
        } else {
            return this;
        }
    }

    /**
     * 不是空并且满足条件抛异常
     *
     * @param Predicate         返回的boolean条件
     * @param exceptionSupplier 函数式 返回异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNotNullConditionThrow(Predicate<T> predicate, Supplier<? extends Throwable> exceptionSupplier) {
        if (!isEmpty()) {
            if (predicate.test(value)) {
                throw exceptionSupplier.get();
            }
        }
        return this;
    }


    /**
     * 满足条件抛异常
     *
     * @param Predicate         返回的boolean条件
     * @param exceptionSupplier 函数式 返回异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNotNullConditionThrow(Predicate<T> predicate, Function<T, Throwable> exceptionSupplier) {
        if (!isEmpty()) {
            if (predicate.test(value)) {
                throw exceptionSupplier.apply(value);
            }
        }
        return this;
    }

    /**
     * 满足条件抛异常
     *
     * @param predicate         返回的boolean条件
     * @param exceptionSupplier 抛出的异常实例
     * @return 返回当前操作类
     */
    @SneakyThrows
    public ApidocOptional<T> isNotNullConditionThrow(Predicate<T> predicate, Throwable exceptionSupplier) {
        if (!isEmpty()) {
            if (predicate.test(value)) {
                throw exceptionSupplier;
            }
        }
        return this;
    }

    /**
     * 不是空并且满足条件抛异常
     *
     * @param Predicate 返回的boolean条件
     * @param runnable  满足条件执行的函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNotNullCondition(Predicate<T> predicate, Runnable runnable) {
        if (!isEmpty()) {
            if (predicate.test(value)) {
                Objects.requireNonNull(runnable).run();
            }
        }
        return this;
    }

    /**
     * 满足条件执行
     *
     * @param predicate 返回的boolean条件
     * @param consumer  满足条件执行的回调函数
     * @return 返回当前操作类
     */
    public ApidocOptional<T> isNotNullCondition(Predicate<T> predicate, Consumer<T> consumer) {
        if (!isEmpty()) {
            if (predicate.test(value)) {
                Objects.requireNonNull(consumer).accept(value);
            }
        }
        return this;
    }

    /**
     * 满足条件执行函数
     *
     * @param predicate 条件函数返回true表示满足条件,执行function
     * @param function  满足条件后执行的回调函数
     * @return 返回当前操作类
     */
    public <R> ApidocOptional<R> isNotNullConditionMap(Predicate<T> predicate, Function<T, R> function) {
        if (!isEmpty() && Objects.requireNonNull(predicate).test(value)) {
            return ApidocOptional.ofNullable(Objects.requireNonNull(function).apply(value));
        } else {
            return empty();
        }
    }

    /**
     * 切换数据类型
     *
     * @param field 字段处理函数
     * @param <R>   目标泛型
     * @return 切换列表结果
     */
    public <F, R> List<R> change(Function<F, ?> field, Class<R> type) {
        if (value instanceof Collection && !isEmpty()) {
            Collection c = (Collection) value;
            return (List<R>) c.stream().map(field).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 转换为统一格式的 success
     *
     * @return Result结果
     */
    public ApidocResult<T> toSuccess() {
        return ApidocResult.success(value);
    }

    /**
     * 转换为统一格式的 error结果
     *
     * @param code 自定义的code
     * @param desc 自定义的desc
     * @return Result结果
     */
    public ApidocResult<T> toError(String code, String desc) {
        return ApidocResult.error(code, desc);
    }

    /**
     * 转换为统一格式的 error结果
     *
     * @param code 自定义的code
     * @return Result结果
     */
    public ApidocResult<T> toError(String code) {
        return ApidocResult.error(code, ApidocResultEnum.DATA_ERROR.getDesc());
    }

    /**
     * 判断此{@code Optional}是否与另一个对象"相等"。
     * 当满足以下条件时，两个对象被视为相等：
     * <ul>
     * <li>另一个对象同样是{@code Optional}实例；
     * <li>两个实例均未包含值，或者；
     * <li>两个实例均包含值，且通过{@code equals()}方法比较时，这两个值相等。
     * </ul>
     *
     * @param obj 用于进行相等性测试的对象
     * @return 若两个对象"相等"，则返回{@code true}；否则返回{@code false}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ApidocOptional)) {
            return false;
        }

        ApidocOptional<?> other = (ApidocOptional<?>) obj;
        return Objects.equals(value, other.value);
    }

    /**
     * 返回当前值的哈希码（若值存在）；若值不存在，则返回{@code 0}。
     *
     * @return 若值存在，则返回该值的哈希码；若值不存在，则返回{@code 0}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /**
     * 返回当前{@code Optional}实例的非空字符串表示形式，适用于调试场景。
     * 具体的格式未作规定，可能会随实现版本变化。
     *
     * @return 当前实例的字符串表示
     * @implSpec 若值存在，结果必须包含该值的字符串表示；
     * 空值与非空值的{@code Optional}必须通过返回值明确区分。
     */
    @Override
    public String toString() {
        return value != null ? String.format("Optional[%s]", value) : "Optional.empty";
    }
}
