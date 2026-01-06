package cn.hamm.airpower.web.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>MCP 初始化数据</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpInitializeData {
    /**
     * 服务器信息
     */
    private McpServerInfo serverInfo;

    /**
     * 协议版本
     */
    private String protocolVersion = "2024-11-05";

    /**
     * 服务能力
     */
    private McpCapability capabilities = new McpCapability();

    /**
     * 构造函数
     *
     * @param mcpServerInfo 服务器信息
     */
    public McpInitializeData(McpServerInfo mcpServerInfo) {
        this.serverInfo = mcpServerInfo;
    }

    /**
     * 服务能力
     */
    @Data
    @Accessors(chain = true)
    static class McpCapability {
        /**
         * 工具能力
         */
        private McpToolCapability tools = new McpToolCapability();

        /**
         * 工具能力
         */
        @Data
        @Accessors(chain = true)
        static class McpToolCapability {
            /**
             * 是否通知客户端更新
             */
            private Boolean listChanged = false;
        }
    }
}
