package cn.hamm.airpower.api.curd;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h2>显式声明本控制器开放哪些接口</h2>
 *
 * @author Hamm.cn
 * @see Curd
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface Extends {

    /**
     * 本控制器开放哪些接口
     *
     * @apiNote 必须写全。留空等价于「不开放任何接口」
     */
    Curd[] value() default {};
}
