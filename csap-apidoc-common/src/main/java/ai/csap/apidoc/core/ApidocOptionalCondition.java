package ai.csap.apidoc.core;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 条件操作扩展
 *
 * @author yangchengfu
 * @description Optional 条件操作扩展
 * @dataTime 2020年-06月-18日 16:53:00
 **/
abstract class ApidocOptionalCondition<T, R> implements ApidocChildren<R> {
    /**
     * 是否能运行
     */
    protected boolean run = true;
    /**
     * when是否能运行
     */
    protected boolean whenRun = true;

    /**
     * 链式条件内部是否空添加判断，否则直接执行
     * <p>针对then 和所有操作使用<p/>
     *
     * @param consumer 回调函数
     * @return 当前对象
     */
    public R runCondition(Predicate<T> consumer) {
        this.run = Objects.requireNonNull(consumer).test(getValue());
        return children();
    }

    /**
     * 默认数据条件处理
     *
     * @return 当前链式对象
     */
    public R runCondition() {
        this.run = !isEmpty();
        return children();
    }

    /**
     * 链式条件内部是否空添加判断，否则直接执行
     * 针对when使用
     *
     * @param predicate 回调函数
     * @return 当前链式对象
     */
    public R whenCondition(Predicate<T> predicate) {
        this.whenRun = Objects.requireNonNull(predicate).test(getValue());
        return children();
    }

    /**
     * 默认数据条件处理
     * 只针对when使用
     *
     * @return 当前链式对象
     */
    public R whenCondition() {
        this.whenRun = !isEmpty();
        return children();
    }

    /**
     * 结束执行条件
     *
     * @return 当前链式对象
     */
    public R endRunCondition() {
        this.run = true;
        return children();
    }

    /**
     * 结束执行条件
     *
     * @return 当前链式对象
     */
    public R endWhenCondition() {
        this.whenRun = true;
        return children();
    }

    /**
     * 判断是否空
     *
     * @return 当前链式对象
     */
    abstract boolean isEmpty();

    /**
     * 获取当前值
     *
     * @return 当前链式对象
     */
    abstract T getValue();


    /**
     * 内部使用
     *
     * @param runnable 执行函数
     * @return 当前链式对象
     */
    protected R run(Runnable runnable) {
        return run(runnable, false);
    }

    /**
     * 内部使用
     *
     * @param runnable 执行函数
     * @return 当前链式对象
     */
    protected R run(Runnable runnable, boolean when) {
        if (when && whenRun) {
            runnable.run();
        } else if (!when && this.run) {
            runnable.run();
        }
        return children();
    }

    /**
     * 需要返回数据 内部使用
     *
     * @param runnable 执行的函数
     * @param <R2>     结果泛型
     * @return 当前链式对象
     */
    protected <R2> R2 run(Supplier<R2> runnable) {
        return runnable.get();
    }

    /**
     * 链式条件then
     *
     * @param consumer 回调函数
     *                 默认判断空
     * @return 当前链式对象
     */
    public R then(Consumer<T> consumer) {
        return run(() -> Objects.requireNonNull(consumer).accept(getValue()));
    }

    /**
     * 链式条件then
     * 默认不判断空
     *
     * @param when 回调表达式
     * @return 当前链式对象
     */
    public R when(Consumer<T> when) {
        return run(() -> Objects.requireNonNull(when).accept(getValue()), true);
    }

    /**
     * 链式条件then
     * 默认不判断空
     *
     * @param when 回调表达式
     * @return 当前链式对象
     */
    public R when(Runnable when) {
        return run(() -> Objects.requireNonNull(when).run(), true);
    }

    /**
     * 链式条件then
     * <p>满足 Predicate get is true 执行回调表达式<p/>
     *
     * @param condition 条件表达式
     * @param when      回调表达式
     * @return 当前链式对象
     */
    public R when(Predicate<T> condition, Consumer<T> when) {
        return run(() -> {
            if (Objects.requireNonNull(condition).test(getValue())) {
                Objects.requireNonNull(when).accept(getValue());
            }
        }, true);
    }

    /**
     * 链式条件then
     * <p>满足 Predicate get is true 执行回调表达式<p/>
     *
     * @param condition 条件表达式
     * @param when      回调表达式
     * @return 当前链式对象
     */
    public R when(Predicate<T> condition, Runnable when) {
        return run(() -> {
            if (Objects.requireNonNull(condition).test(getValue())) {
                Objects.requireNonNull(when).run();
            }
        }, true);
    }

    /**
     * 链式条件then
     * 满足 Predicate get is true 执行回调表达式
     *
     * @param condition 条件表达式
     * @param when      IF回调函数
     * @param otherwise ELSE 回调函数
     * @return 当前链式对象
     */
    public R whenCondition(Predicate<T> condition, Consumer<T> when, Function<T, R> otherwise) {
        return run(() -> {
            if (Objects.requireNonNull(condition).test(getValue())) {
                Objects.requireNonNull(when).accept(getValue());
            }
            return Objects.requireNonNull(otherwise).apply(getValue());
        });
    }

    /**
     * 链式条件when
     * 满足 Predicate get is true 执行回调表达式
     * 该方法具有返回具体数据作用
     *
     * @param condition 条件表达式
     * @param when      回调表达式
     * @return 当前链式对象
     */
    public <R2> R2 when(Predicate<T> condition, Function<T, R2> when, Function<T, R2> otherwise) {
        return run(() -> {
            if (Objects.requireNonNull(condition).test(getValue())) {
                return Objects.requireNonNull(when).apply(getValue());
            }
            return Objects.requireNonNull(otherwise).apply(getValue());
        });
    }

    /**
     * 链式条件when
     * 满足 Predicate get is true 执行回调表达式
     * 该方法具有返回具体数据作用
     *
     * @param condition 条件表达式
     * @param when      回调表达式
     * @return 当前链式对象
     */
    public <R2> ApidocOptional<R2> whenOptional(Predicate<T> condition, Function<T, R2> when, Function<T, R2> otherwise) {
        return run(() -> {
            if (Objects.requireNonNull(condition).test(getValue())) {
                return ApidocOptional.ofNullable(Objects.requireNonNull(when).apply(getValue()));
            }
            return ApidocOptional.ofNullable(Objects.requireNonNull(otherwise).apply(getValue()));
        });
    }

    /**
     * 链式条件then
     * 满足 Predicate get is true 执行回调表达式
     *
     * @param condition 条件表达式
     * @param when      满足回调表达式
     * @param otherwise 否则回调表达式
     * @return 当前链式对象
     */
    public R when(Predicate<T> condition, Consumer<T> when, Consumer<T> otherwise) {
        return run(() -> {
            if (Objects.requireNonNull(condition).test(getValue())) {
                Objects.requireNonNull(when).accept(getValue());
            } else {
                Objects.requireNonNull(otherwise).accept(getValue());
            }
        }, true);
    }

    /**
     * 返回指定{@code <R>}类型
     *
     * @param supplier 表达式
     * @param <R2>     返回的类型
     * @return 当前链式对象
     */
    public <R2> R2 value(Function<T, R2> supplier) {
        return Objects.requireNonNull(supplier).apply(getValue());
    }

    /**
     * 返回指定{@code <R>}类型
     *
     * @param supplier 表达式
     * @param <R2>     返回的类型
     * @return 当前链式对象
     */
    public <R2> R2 value(Supplier<R2> supplier) {
        return Objects.requireNonNull(supplier).get();
    }

    /**
     * 返回指定{@code <R>}类型
     *
     * @param supplier 表达式
     * @param <R2>     返回的类型
     * @return 当前链式对象
     */
    public <R2> ApidocOptional<R2> valueOptional(Function<T, R2> supplier) {
        return ApidocOptional.ofNullable(Objects.requireNonNull(supplier).apply(getValue()));
    }

    /**
     * 返回指定{@code <R>}类型
     *
     * @param supplier 表达式
     * @param <R2>     返回的类型
     * @return 当前链式对象
     */
    public <R2> ApidocOptional<R2> valueOptional(Supplier<R2> supplier) {
        return ApidocOptional.ofNullable(Objects.requireNonNull(supplier).get());
    }
}
