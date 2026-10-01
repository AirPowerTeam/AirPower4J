package cn.hamm.airpower.curd.annotation;

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
 * @apiNote 如不标记，则默认按内置规则进行全匹配或者 Join 匹配。
 * 只在 {@code filter/query} 这类模糊查询中生效，全匹配查询（{@code filter/filterPage}）仍然走 {@code =}
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface Search {
    /**
     * 是否全模糊查询，默认只左模糊
     *
     * @apiNote {@code false} 生成 {@code LIKE '值%'}，{@code true} 生成 {@code LIKE '%值%'}；
     * 通配符会被转义，查询值中的 {@code %} 和 {@code _} 按字面量处理
     */
    boolean fullLike() default false;
}

