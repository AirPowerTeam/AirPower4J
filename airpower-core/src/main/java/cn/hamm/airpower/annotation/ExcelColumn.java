package cn.hamm.airpower.annotation;

import cn.hamm.airpower.validate.Dictionary;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static cn.hamm.airpower.annotation.ExcelColumn.Type.TEXT;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>Excel 导出列</h1>
 *
 * @author Hamm.cn
 */
@Target(FIELD)
@Retention(RUNTIME)
@Inherited
@Documented
public @interface ExcelColumn {
    /**
     * 列数据类型
     */
    Type value() default TEXT;

    /**
     * 列数据类型
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
