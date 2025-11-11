package cn.hamm.airpower.export;

import cn.hamm.airpower.dictionary.Dictionary;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static cn.hamm.airpower.export.Export.Type.TEXT;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>Excel 导出列</h1>
 *
 * @author Hamm.cn
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface Export {
    /**
     * 列数据类型
     */
    Type value() default TEXT;

    /**
     * 导出列排序
     */
    int sort() default 0;

    /**
     * 是否移除
     */
    boolean remove() default false;

    /**
     * <h1>列数据类型</h1>
     *
     * @author Hamm.cn
     */
    enum Type {
        /**
         * 普通文本
         */
        TEXT,

        /**
         * 时间日期
         */
        DATETIME,

        /**
         * 数字
         */
        NUMBER,

        /**
         * 字典
         *
         * @apiNote 请确保同时标记了 {@link Dictionary}
         */
        DICTIONARY,

        /**
         * 布尔值
         */
        BOOLEAN
    }
}
