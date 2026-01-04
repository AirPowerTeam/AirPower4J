package cn.hamm.airpower.web.open;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>Open API</h1>
 *
 * @author Hamm.cn
 */
@Target({FIELD, METHOD, TYPE})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface OpenApi {
}
