package cn.hamm.airpower.curd.annotation;

import cn.hamm.airpower.curd.base.Curd;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>继承的接口 {@code 白名单优先}</h1>
 *
 * <li>如不标记此注解,则默认将所有基类接口继承</li>
 * <li>如标记白名单,则需写全需要继承的接口</li>
 * <li>如标记黑名单,则只需要写不继承的接口</li>
 *
 * @author Hamm.cn
 * @see Curd
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Extends {
    /**
     * 排除父类接口但需要继承这些接口
     */
    Curd[] value() default {};

    /**
     * 继承父类接口但排除这些接口
     */
    Curd[] exclude() default {};
}
