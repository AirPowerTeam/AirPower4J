package cn.hamm.airpower.mcp.exception;

import cn.hamm.airpower.exception.IException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>MCP错误码</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum McpErrorCode implements IException<McpErrorCode> {
    /**
     * <h3>转换错误</h3>
     */
    ParseError(-32700, "Parse error"),

    /**
     * <h3>请求错误</h3>
     */
    InvalidRequest(-32600, "Invalid Request"),

    /**
     * <h3>方法未找到</h3>
     */
    MethodNotFound(-32601, "Method not found"),

    /**
     * <h3>参数错误</h3>
     */
    InvalidParams(-32602, "Invalid params"),

    /**
     * <h3>内部错误</h3>
     */
    InternalError(-32603, "Internal error");

    /**
     * <h3>错误码</h3>
     */
    private final int code;

    /**
     * <h3>错误信息</h3>
     */
    private final String message;
}
