package cn.hamm.airpower.api.annotation;

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
 * <h1>API 控制器注解</h1>
 * 组合了 {@link RestController} 与 {@link RequestMapping}，标注即成为一个 REST 控制器。
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
     * 接口路径
     */
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String value();
}
