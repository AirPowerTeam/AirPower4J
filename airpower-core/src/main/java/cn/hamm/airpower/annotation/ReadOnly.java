package cn.hamm.airpower.annotation;

import cn.hamm.airpower.root.RootModel;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>只读字段</h1>
 *
 * @author Hamm.cn
 * @apiNote 可在控制器调用 Service 前先调用 {@link RootModel#ignoreReadOnlyFields()} 将标记了此注解的字段移除
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface ReadOnly {
}
