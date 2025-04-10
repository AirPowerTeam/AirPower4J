package cn.hamm.airpower.mcp.method;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记是MCP必要属性</h1>
 *
 * @author Hamm.cn
 * @apiNote 如不标记此项，则默认为 {@code 必要}
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface McpRequired {
    /**
     * <h2>是否必须</h2>
     */
    boolean value() default true;
}
