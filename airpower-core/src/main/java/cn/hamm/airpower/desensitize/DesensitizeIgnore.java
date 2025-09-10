package cn.hamm.airpower.desensitize;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>此接口返回数据不脱敏</h1>
 *
 * @author Hamm.cn
 * @see Desensitize
 */
@Target(METHOD)
@Retention(RUNTIME)
@Inherited
public @interface DesensitizeIgnore {
}
