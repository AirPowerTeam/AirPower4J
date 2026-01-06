package cn.hamm.airpower.web.mcp.common;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>MCP JSON</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpJson {
    /**
     * <b>JSONRPC</b> 版本
     */
    private String jsonrpc = "2.0";

    /**
     * ID
     */
    private Long id = 0L;
}
