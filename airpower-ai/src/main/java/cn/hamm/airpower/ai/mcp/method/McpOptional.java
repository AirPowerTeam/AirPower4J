package cn.hamm.airpower.ai.mcp.method;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记是 MCP 可选属性</h1>
 *
 * @author Hamm.cn
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface McpOptional {
}
