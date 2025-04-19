package cn.hamm.airpower.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>MCP 服务器信息</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpServerInfo {
    /**
     * 服务器名称
     */
    private String name = "Mcp Server";

    /**
     * 服务器版本
     */
    private String version = "1.0.0";
}
