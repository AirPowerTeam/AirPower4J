package cn.hamm.airpower.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static cn.hamm.airpower.annotation.Search.Mode.LIKE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记为搜索字段</h1>
 *
 * @author Hamm.cn
 * @apiNote 默认为 {@link Mode#LIKE}，支持 {@link Mode#LIKE}, {@link Mode#JOIN}, {@link Mode#EQUALS}
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface Search {
    /**
     * 搜索方式
     */
    Mode value() default LIKE;

    /**
     * 搜索类型
     */
    enum Mode {
        /**
         * 相等
         */
        EQUALS,

        /**
         * 字符串模糊匹配
         */
        LIKE,

        /**
         * {@code JOIN} 查询
         */
        JOIN,
    }
}

