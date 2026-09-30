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
 * @apiNote 类上的标记作为默认值，被方法上的标记整体覆盖（不是逐属性覆盖）。
 * 标在基类控制器上则所有子接口默认需要登录
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
     *
     * @apiNote {@code login} 为 {@code false} 时本项不生效
     */
    boolean authorize() default true;
}
