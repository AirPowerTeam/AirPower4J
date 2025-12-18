package cn.hamm.airpower.meta;

import cn.hamm.airpower.root.RootModel;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>此接口需要整体暴露的类名</h1>
 *
 * @author Hamm.cn
 * @see Meta
 */
@Target(METHOD)
@Retention(RUNTIME)
@Inherited
public @interface ExposeAll {
    Class<? extends RootModel<?>>[] value();
}
