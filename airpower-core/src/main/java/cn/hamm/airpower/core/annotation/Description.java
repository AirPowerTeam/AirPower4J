package cn.hamm.airpower.core.annotation;

import cn.hamm.airpower.core.reflect.ReflectUtil;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>类或属性的文案</h1>
 *
 * @author Hamm.cn
 * @apiNote 配置后可通过 {@link ReflectUtil } 获取
 * @see ReflectUtil#getDescription(Method)
 * @see ReflectUtil#getDescription(Field)
 * @see ReflectUtil#getDescription(Class)
 */
@Target({FIELD, METHOD, TYPE, PARAMETER})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface Description {
    /**
     * <h3>描述文案</h3>
     *
     * @apiNote 将显示在错误信息、验证信息、文档等处
     */
    String value();
}
