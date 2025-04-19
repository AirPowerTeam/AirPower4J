package cn.hamm.airpower.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>MCP服务器信息</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpServerInfo {
    /**
     * <h3>服务器名称</h3>
     */
    private String name = "Mcp Server";

    /**
     * <h3>服务器版本</h3>
     */
    private String version = "1.0.0";
}
