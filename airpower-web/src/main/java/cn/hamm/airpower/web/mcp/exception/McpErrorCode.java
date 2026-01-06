package cn.hamm.airpower.web.mcp.exception;

import cn.hamm.airpower.core.interfaces.IException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>MCP 错误码</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum McpErrorCode implements IException<McpErrorCode> {
    /**
     * 转换错误
     */
    ParseError(-32700, "Parse error"),

    /**
     * 请求错误
     */
    InvalidRequest(-32600, "Invalid Request"),

    /**
     * 方法未找到
     */
    MethodNotFound(-32601, "Method not found"),

    /**
     * 参数错误
     */
    InvalidParams(-32602, "Invalid params"),

    /**
     * 内部错误
     */
    InternalError(-32603, "Internal error");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误信息
     */
    private final String message;
}
