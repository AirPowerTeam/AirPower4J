package cn.hamm.airpower.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>为 {@code null} 时依然存储</h1>
 *
 * @author Hamm.cn
 * @apiNote 即使字段为 {@code null} ，依然保持更新到数据库
 */
@Target(FIELD)
@Retention(RUNTIME)
@Inherited
@Documented
public @interface NullEnable {
    /**
     * {@code true} 时，则将保存 {@code null} 到数据库
     */
    boolean value() default true;
}