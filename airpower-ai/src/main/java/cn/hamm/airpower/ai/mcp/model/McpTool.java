package cn.hamm.airpower.ai.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>Mcp 工具</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Data
@Accessors(chain = true)
public class McpTool {
    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入参数
     */
    private InputSchema inputSchema;

    /**
     * 输入参数
     */
    @Data
    @Accessors(chain = true)
    public static class InputSchema {
        /**
         * 类型
         */
        private String type = "object";

        /**
         * 属性
         */
        private Map<String, Property> properties = new HashMap<>();

        /**
         * 必填
         */
        private List<String> required = new ArrayList<>();

        /**
         * 属性
         */
        @Data
        @Accessors(chain = true)
        public static class Property {
            /**
             * 类型
             */
            private String type = "string";

            /**
             * 描述
             */
            private String description;
        }
    }
}
