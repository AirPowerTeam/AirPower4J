package cn.hamm.airpower.reflect;

import java.io.Serializable;
import java.util.function.Function;

/**
 * <h1>带序列化的 {@code Function}</h1>
 *
 * @author Hamm.cn
 */
@FunctionalInterface
public interface IFunction<T, R> extends Function<T, R>, Serializable {
}