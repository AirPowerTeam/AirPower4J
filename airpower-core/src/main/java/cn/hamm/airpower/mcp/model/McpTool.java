package cn.hamm.airpower.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>McpTool</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Data
@Accessors(chain = true)
public class McpTool {
    /**
     * <h3>工具名称</h3>
     */
    private String name;

    /**
     * <h3>工具描述</h3>
     */
    private String description;

    /**
     * <h3>输入参数</h3>
     */
    private InputSchema inputSchema;

    /**
     * <h3>输入参数</h3>
     *
     * @author Hamm.cn
     */
    @Data
    @Accessors(chain = true)
    public static class InputSchema {
        /**
         * <h3>类型</h3>
         */
        private String type = "object";

        /**
         * <h3>属性</h3>
         */
        private Map<String, Property> properties = new HashMap<>();

        /**
         * <h3>必填</h3>
         */
        private List<String> required = new ArrayList<>();

        /**
         * <h3>属性</h3>
         */
        @Data
        @Accessors(chain = true)
        public static class Property {
            /**
             * <h3>类型</h3>
             */
            private String type = "string";

            /**
             * <h3>描述</h3>
             */
            private String description;
        }
    }
}
