package cn.hamm.airpower.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>暴露字段属性</h1>
 *
 * @author Hamm.cn
 * @apiNote 此注解用于类标记了全部忽略后, 需要对部分字段进行暴露的场景。可参考 {@link Exclude}
 */
@Target({FIELD, METHOD, TYPE})
@Retention(RUNTIME)
@Documented
public @interface Expose {
    /**
     * 过滤器列表
     */
    Class<?>[] filters() default {};
}
