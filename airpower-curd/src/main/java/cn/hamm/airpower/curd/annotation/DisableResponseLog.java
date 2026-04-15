package cn.hamm.airpower.curd.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记不输出请求日志</h1>
 *
 * @author Hamm.cn
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface DisableResponseLog {
    /**
     * 是否禁止请求日志
     */
    boolean value() default true;
}
