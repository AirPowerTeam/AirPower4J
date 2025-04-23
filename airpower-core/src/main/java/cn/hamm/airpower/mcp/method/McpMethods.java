package cn.hamm.airpower.mcp.method;

import cn.hamm.airpower.dictionary.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>Mcp 内置方法</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum McpMethods implements IDictionary {
    /**
     * 初始化
     */
    INITIALIZE(1, "initialize"),

    /**
     * 工具列表
     */
    TOOLS_LIST(2, "tools/list"),

    /**
     * 工具调用
     */
    TOOLS_CALL(3, "tools/call"),
    ;

    /**
     * key
     */
    private final int key;

    /**
     * 方法名称
     */
    private final String label;
}
