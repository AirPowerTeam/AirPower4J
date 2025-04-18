package cn.hamm.airpower.mcp.method;

import cn.hamm.airpower.interfaces.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>McpMethods</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum McpMethods implements IDictionary {
    /**
     * <h3>初始化</h3>
     */
    INITIALIZE(1, "initialize"),

    /**
     * <h3>工具列表</h3>
     */
    TOOLS_LIST(2, "tools/list"),

    /**
     * <h3>工具调用</h3>
     */
    TOOLS_CALL(3, "tools/call"),
    ;

    /**
     * <h2>key</h2>
     */
    private final int key;

    /**
     * <h2>方法名称</h2>
     */
    private final String label;
}
