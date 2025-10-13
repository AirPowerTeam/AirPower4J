package cn.hamm.airpower.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记为模糊搜索字段</h1>
 *
 * @author Hamm.cn
 * @apiNote 如不标记，则默认按内置规则进行全匹配或者 Join 匹配
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface Search {
    /**
     * 是否全模糊查询，默认只左模糊
     */
    boolean fullLike() default false;
}

