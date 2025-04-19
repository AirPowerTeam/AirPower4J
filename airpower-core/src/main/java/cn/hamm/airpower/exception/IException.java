package cn.hamm.airpower.exception;

import cn.hamm.airpower.config.Constant;
import org.jetbrains.annotations.Contract;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * <h1>异常接口</h1>
 *
 * @author Hamm.cn
 */
public interface IException<T extends IException<T>> extends Supplier<T> {
    /**
     * <h3>获取错误代码</h3>
     *
     * @return 错误代码
     */
    int getCode();

    /**
     * <h3>获取返回信息</h3>
     *
     * @return 返回信息
     */
    String getMessage();

    /**
     * <h3>抛出异常</h3>
     */
    default void show() {
        show(getMessage());
    }

    /**
     * <h3>抛出异常</h3>
     *
     * @param message 返回信息
     */
    default void show(String message) {
        show(message, null);
    }

    /**
     * <h3>获取一个异常实例</h3>
     *
     * @return 异常实例
     */
    @Override
    default T get() {
        //noinspection unchecked
        return (T) this;
    }

    /**
     * <h3>抛出异常</h3>
     *
     * @param message 返回信息
     * @param data    返回数据
     */
    default void show(String message, Object data) {
        throw new ServiceException(message, data);
    }

    /**
     * <h3>当 {@code 满足条件} 时抛出异常</h3>
     *
     * @param condition 条件
     */
    default void when(boolean condition) {
        when(condition, getMessage());
    }

    /**
     * <h3>当 {@code 满足条件} 时抛出异常</h3>
     *
     * @param condition 条件
     * @param message   返回信息
     */
    default void when(boolean condition, String message) {
        if (condition) {
            show(message);
        }
    }

    /**
     * <h3>当 {@code 满足条件} 时抛出异常</h3>
     *
     * @param condition 条件
     * @param message   返回信息
     * @param data      数据
     */
    default void when(boolean condition, String message, Object data) {
        if (condition) {
            show(message, data);
        }
    }

    /**
     * <h3>当为 {@code null} 时抛出异常</h3>
     *
     * @param obj 被验证的数据
     */
    @Contract("null -> fail")
    default void whenNull(Object obj) {
        whenNull(obj, getMessage());
    }

    /**
     * <h3>当为 {@code null} 时抛出异常</h3>
     *
     * @param obj     被验证的数据
     * @param message 返回信息
     */
    @Contract("null, _ -> fail")
    default void whenNull(Object obj, String message) {
        when(Objects.isNull(obj), message);
    }

    /**
     * <h3>当 {@code 两者相同} 时抛出异常</h3>
     *
     * @param obj1 被验证的数据
     * @param obj2 被验证的数据
     */
    default void whenEquals(Object obj1, Object obj2) {
        whenEquals(obj1, obj2, getMessage());
    }

    /**
     * <h3>当 {@code 两者相同} 时抛出异常</h3>
     *
     * @param obj1    被验证的数据
     * @param obj2    被验证的数据
     * @param message 返回信息
     */
    default void whenEquals(Object obj1, Object obj2, String message) {
        when(Objects.equals(obj1, obj2), message);
    }

    /**
     * <h3>当 {@code 两个字符串相同} 时抛出异常</h3>
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenEquals(String str1, String str2) {
        whenEquals(str1, str2, getMessage());
    }

    /**
     * <h3>当 {@code 两个字符串相同} 时抛出异常</h3>
     *
     * @param str1    被验证的数据
     * @param str2    被验证的数据
     * @param message 返回信息
     */
    default void whenEquals(String str1, String str2, String message) {
        when(Objects.equals(str1, str2), message);
    }

    /**
     * <h3>当 {@code 两个字符串忽略大小写相同} 时抛出异常</h3>
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenEqualsIgnoreCase(String str1, String str2) {
        whenEqualsIgnoreCase(str1, str2, getMessage());
    }

    /**
     * <h3>当 {@code 两个字符串忽略大小写相同} 时抛出异常</h3>
     *
     * @param str1    被验证的数据
     * @param str2    被验证的数据
     * @param message 返回信息
     */
    default void whenEqualsIgnoreCase(String str1, String str2, String message) {
        if (Objects.isNull(str1) || Objects.isNull(str2)) {
            show(message);
        }
        when(Objects.equals(str1.toLowerCase(), str2.toLowerCase()), message);
    }

    /**
     * <h3>当 <s>{@code 两者不相同}</s> 时抛出异常</h3>
     *
     * @param obj1 被验证的数据
     * @param obj2 被验证的数据
     */
    default void whenNotEquals(Object obj1, Object obj2) {
        whenNotEquals(obj1, obj2, getMessage());
    }

    /**
     * <h3>当 <s>{@code 两者不相同}</s> 时抛出异常</h3>
     *
     * @param obj1    被验证的数据
     * @param obj2    被验证的数据
     * @param message 返回信息
     */
    default void whenNotEquals(Object obj1, Object obj2, String message) {
        when(!Objects.equals(obj1, obj2), message);
    }

    /**
     * <h3>当 <s>{@code 两个字符串不相同}</s> 时抛出异常</h3>
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenNotEquals(String str1, String str2) {
        whenNotEquals(str1, str2, getMessage());
    }

    /**
     * <h3>当 <s>{@code 两个字符串不相同}</s> 时抛出异常</h3>
     *
     * @param str1    被验证的数据
     * @param str2    被验证的数据
     * @param message 返回信息
     */
    default void whenNotEquals(String str1, String str2, String message) {
        when(!Objects.equals(str1, str2), message);
    }

    /**
     * <h3>当 <s>{@code 两个字符串忽略大小写还不相同}</s> 时抛出异常</h3>
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenNotEqualsIgnoreCase(String str1, String str2) {
        whenNotEqualsIgnoreCase(str1, str2, getMessage());
    }

    /**
     * <h3>当 <s>{@code 两个字符串忽略大小写还不相同}</s> 时抛出异常</h3>
     *
     * @param str1    被验证的数据
     * @param str2    被验证的数据
     * @param message 返回信息
     */
    default void whenNotEqualsIgnoreCase(String str1, String str2, String message) {
        if (Objects.isNull(str1) || Objects.isNull(str2)) {
            show(message);
        }
        when(!Objects.equals(str1.toLowerCase(), str2.toLowerCase()), message);
    }

    /**
     * <h3>当为 {@code null或空字符串} 时抛出异常</h3>
     *
     * @param obj 被验证的数据
     */
    @Contract("null -> fail")
    default void whenEmpty(Object obj) {
        whenEmpty(obj, getMessage());
    }

    /**
     * <h3>当为 {@code null或空字符串} 时抛出异常</h3>
     *
     * @param obj     被验证的数据
     * @param message 返回信息
     */
    @Contract("null, _ -> fail")
    default void whenEmpty(Object obj, String message) {
        when(Objects.isNull(obj) || Constant.STRING_EMPTY.equalsIgnoreCase(obj.toString()), message);
    }

    /**
     * <h3>当 <s>{@code 不为null}</s> 时抛出异常</h3>
     *
     * @param obj 被验证的数据
     */
    default void whenNotNull(Object obj) {
        whenNotNull(obj, getMessage());
    }

    /**
     * <h3>当 <s>{@code 不为null}</s> 时抛出异常</h3>
     *
     * @param obj     被验证的数据
     * @param message 返回信息
     */
    default void whenNotNull(Object obj, String message) {
        when(!Objects.isNull(obj), message);
    }
}
