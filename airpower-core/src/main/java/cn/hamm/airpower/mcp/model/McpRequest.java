package cn.hamm.airpower.mcp.model;

import cn.hamm.airpower.mcp.common.McpJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>MCP请求</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class McpRequest extends McpJson {
    /**
     * <h3>请求参数</h3>
     */
    private Object params;

    /**
     * <h3>请求方法</h3>
     */
    private String method;
}
