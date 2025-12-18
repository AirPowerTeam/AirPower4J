package cn.hamm.airpower.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记字段在控制器中保持输出</h1>
 *
 * @author Hamm.cn
 * @apiNote 其他未标记 {@code @Meta} 的字段在控制器方法没有标记 {@link ExposeAll} 时将被移除
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Documented
public @interface Meta {

}
