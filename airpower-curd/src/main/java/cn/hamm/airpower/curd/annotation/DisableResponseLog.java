package cn.hamm.airpower.curd.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记不输出响应日志</h1>
 *
 * @author Hamm.cn
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface DisableResponseLog {
    /**
     * 是否禁止响应日志
     *
     * @apiNote 需配合全局开关 {@code airpower.api.response-log} 使用，全局关闭时本注解无意义。
     * 与 {@link DisableRequestLog} 相互独立，可只屏蔽一侧
     */
    boolean value() default true;
}
