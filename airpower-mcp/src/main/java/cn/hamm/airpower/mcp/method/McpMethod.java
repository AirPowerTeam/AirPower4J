package cn.hamm.airpower.mcp.method;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记为一个MCP方法</h1>
 *
 * @author Hamm.cn
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface McpMethod {
    /**
     * <h2>方法名</h2>
     */
    String value() default "";
}
