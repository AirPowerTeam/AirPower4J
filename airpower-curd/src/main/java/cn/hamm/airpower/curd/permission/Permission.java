package cn.hamm.airpower.curd.permission;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>是否需要登录和授权</h1>
 *
 * @author Hamm.cn
 */
@Target({METHOD, TYPE})
@Retention(RUNTIME)
@Inherited
public @interface Permission {
    /**
     * 需要登录
     */
    boolean login() default true;

    /**
     * 需要授权
     */
    boolean authorize() default true;
}
