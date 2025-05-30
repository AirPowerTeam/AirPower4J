package cn.hamm.airpower.api;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>API 控制器</h1>
 *
 * @author Hamm.cn
 */
@Target(TYPE)
@Retention(RUNTIME)
@Inherited
@Documented
@RestController
@RequestMapping
public @interface Api {
    /**
     * API 的路径
     *
     * @see RequestMapping#path()
     */
    @SuppressWarnings("UnusedReturnValue")
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String value();
}
