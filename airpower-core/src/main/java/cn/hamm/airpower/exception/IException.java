package cn.hamm.airpower.exception;

import org.jetbrains.annotations.Contract;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * <h1>异常接口</h1>
 *
 * @author Hamm.cn
 */
public interface IException<T extends IException<T>> extends Supplier<T> {
    /**
     * 获取错误代码
     *
     * @return 错误代码
     */
    int getCode();

    /**
     * 获取返回信息
     *
     * @return 返回信息
     */
    String getMessage();

    /**
     * 抛出异常
     */
    default void show() {
        show(getMessage());
    }

    /**
     * 抛出异常
     *
     * @param message 返回信息
     */
    default void show(String message) {
        show(message, null);
    }

    /**
     * 获取一个异常实例
     *
     * @return 异常实例
     */
    @Override
    default T get() {
        //noinspection unchecked
        return (T) this;
    }

    /**
     * 抛出异常
     *
     * @param message 返回信息
     * @param data    返回数据
     */
    default void show(String message, Object data) {
        throw new ServiceException(this.getCode(), message, data);
    }

    /**
     * 当 <b>满足条件</b> 时抛出异常
     *
     * @param condition 条件
     */
    default void when(boolean condition) {
        when(condition, getMessage());
    }

    /**
     * 当 <b>满足条件</b> 时抛出异常
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
     * 当 <b>满足条件</b> 时抛出异常
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
     * 当为 {@code null} 时抛出异常
     *
     * @param obj 被验证的数据
     */
    @Contract("null -> fail")
    default void whenNull(Object obj) {
        whenNull(obj, getMessage());
    }

    /**
     * 当为 {@code null} 时抛出异常
     *
     * @param obj     被验证的数据
     * @param message 返回信息
     */
    @Contract("null, _ -> fail")
    default void whenNull(Object obj, String message) {
        when(Objects.isNull(obj), message);
    }

    /**
     * 当 <b>两者相同</b> 时抛出异常
     *
     * @param obj1 被验证的数据
     * @param obj2 被验证的数据
     */
    default void whenEquals(Object obj1, Object obj2) {
        whenEquals(obj1, obj2, getMessage());
    }

    /**
     * 当 <b>两者相同</b> 时抛出异常
     *
     * @param obj1    被验证的数据
     * @param obj2    被验证的数据
     * @param message 返回信息
     */
    default void whenEquals(Object obj1, Object obj2, String message) {
        when(Objects.equals(obj1, obj2), message);
    }

    /**
     * 当 <b>两个字符串相同</b> 时抛出异常
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenEquals(String str1, String str2) {
        whenEquals(str1, str2, getMessage());
    }

    /**
     * 当 <b>两个字符串相同</b> 时抛出异常
     *
     * @param str1    被验证的数据
     * @param str2    被验证的数据
     * @param message 返回信息
     */
    default void whenEquals(String str1, String str2, String message) {
        when(Objects.equals(str1, str2), message);
    }

    /**
     * 当 <b>两个字符串忽略大小写相同</b> 时抛出异常
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenEqualsIgnoreCase(String str1, String str2) {
        whenEqualsIgnoreCase(str1, str2, getMessage());
    }

    /**
     * 当 <b>两个字符串忽略大小写相同</b> 时抛出异常
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
     * 当 <s><b>两者不相同</b></s> 时抛出异常
     *
     * @param obj1 被验证的数据
     * @param obj2 被验证的数据
     */
    default void whenNotEquals(Object obj1, Object obj2) {
        whenNotEquals(obj1, obj2, getMessage());
    }

    /**
     * 当 <s><b>两者不相同</b></s> 时抛出异常
     *
     * @param obj1    被验证的数据
     * @param obj2    被验证的数据
     * @param message 返回信息
     */
    default void whenNotEquals(Object obj1, Object obj2, String message) {
        when(!Objects.equals(obj1, obj2), message);
    }

    /**
     * 当 <s><b>两个字符串不相同</b></s> 时抛出异常
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenNotEquals(String str1, String str2) {
        whenNotEquals(str1, str2, getMessage());
    }

    /**
     * 当 <s><b>两个字符串不相同</b></s> 时抛出异常
     *
     * @param str1    被验证的数据
     * @param str2    被验证的数据
     * @param message 返回信息
     */
    default void whenNotEquals(String str1, String str2, String message) {
        when(!Objects.equals(str1, str2), message);
    }

    /**
     * 当 <s><b>两个字符串忽略大小写还不相同</b></s> 时抛出异常
     *
     * @param str1 被验证的数据
     * @param str2 被验证的数据
     */
    default void whenNotEqualsIgnoreCase(String str1, String str2) {
        whenNotEqualsIgnoreCase(str1, str2, getMessage());
    }

    /**
     * 当 <s><b>两个字符串忽略大小写还不相同</b></s> 时抛出异常
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
     * 当为 <b>null 或 空字符串</b> 时抛出异常
     *
     * @param obj 被验证的数据
     */
    @Contract("null -> fail")
    default void whenEmpty(Object obj) {
        whenEmpty(obj, getMessage());
    }

    /**
     * 当为 <b>null 或 空字符串</b> 时抛出异常
     *
     * @param obj     被验证的数据
     * @param message 返回信息
     */
    @Contract("null, _ -> fail")
    default void whenEmpty(Object obj, String message) {
        when(Objects.isNull(obj) || !StringUtils.hasText(obj.toString()), message);
    }

    /**
     * 当 <s><b>不为 null</b></s> 时抛出异常
     *
     * @param obj 被验证的数据
     */
    default void whenNotNull(Object obj) {
        whenNotNull(obj, getMessage());
    }

    /**
     * 当 <s><b>不为 null</b></s> 时抛出异常
     *
     * @param obj     被验证的数据
     * @param message 返回信息
     */
    default void whenNotNull(Object obj, String message) {
        when(!Objects.isNull(obj), message);
    }
}
