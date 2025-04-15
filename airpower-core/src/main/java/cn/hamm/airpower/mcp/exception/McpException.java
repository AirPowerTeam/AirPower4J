package cn.hamm.airpower.mcp.exception;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>MCP错误</h1>
 *
 * @author Hamm.cn
 */
@Getter
public class McpException extends Exception {
    /**
     * <h3>错误代码</h3>
     */
    private Integer code = McpErrorCode.InternalError.getKey();

    /**
     * <h3>构造函数</h3>
     *
     * @param message      错误信息
     * @param mcpErrorCode MCP错误代码
     */
    public McpException(String message, @NotNull McpErrorCode mcpErrorCode) {
        super(message);
        this.code = mcpErrorCode.getKey();
    }

    /**
     * <h3>构造函数</h3>
     *
     * @param message 错误信息
     */
    public McpException(String message) {
        super(message);
    }

    /**
     * <h3>构造函数</h3>
     *
     * @param mcpErrorCode MCP错误代码
     */
    public McpException(@NotNull McpErrorCode mcpErrorCode) {
        super(mcpErrorCode.getLabel());
        this.code = mcpErrorCode.getKey();
    }
}
