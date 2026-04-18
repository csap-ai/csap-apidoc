package ai.csap.validation.factory;


import java.util.function.Function;

/**
 * Represents a function that accepts two arguments and produces a result.
 * This is the two-arity specialization of {@link Function}.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <R> the type of the result of the function
 * @author ycf
 * @see Function
 * @since 1.8
 */
@FunctionalInterface
public interface BiFunctionValidation<T, U, P, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @param p the second function argument
     * @return the function result
     */
    R apply(T t, U u, P p);

}
