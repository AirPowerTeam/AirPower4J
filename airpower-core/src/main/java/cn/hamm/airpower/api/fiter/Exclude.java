package cn.hamm.airpower.api.fiter;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>排除字段属性</h1>
 *
 * @author Hamm.cn
 * @apiNote 控制器标记了 {@link Filter} 并指定使用的过滤器后，使用此注解标记了相同装饰器的属性将不会被接口输出。
 * 也可类标记此注解,然后通过 {@link Expose} 输出部分的属性。
 */
@Target({FIELD, METHOD, TYPE})
@Retention(RUNTIME)
@Documented
public @interface Exclude {
    /**
     * 过滤器列表
     */
    Class<?>[] filters() default {};
}
