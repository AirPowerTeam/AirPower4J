package cn.hamm.airpower.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>MCP初始化数据</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpInitializeData {
    /**
     * <h3>服务器信息</h3>
     */
    private McpServerInfo serverInfo = new McpServerInfo();

    /**
     * <h3>协议版本</h3>
     */
    private String protocolVersion = "2024-11-05";

    /**
     * <h3>服务能力</h3>
     */
    private McpCapability capabilities = new McpCapability();

    /**
     * <h3>服务能力</h3>
     */
    @Data
    @Accessors
    static class McpCapability {
        /**
         * <h3>工具能力</h3>
         */
        private McpToolCapability tools = new McpToolCapability();

        /**
         * <h3>工具能力</h3>
         */
        @Data
        @Accessors
        static class McpToolCapability {
            /**
             * <h3>是否通知客户端更新</h3>
             */
            private Boolean listChanged = false;
        }
    }
}
