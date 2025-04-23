package cn.hamm.airpower.curd.export;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static cn.hamm.airpower.curd.export.ExportColumnType.TEXT;
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
    ExportColumnType value() default TEXT;

}
