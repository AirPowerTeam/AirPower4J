package cn.hamm.airpower.web.mcp.model;

import cn.hamm.airpower.web.mcp.common.McpJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>MCP 请求</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class McpRequest extends McpJson {
    /**
     * 请求参数
     */
    private Object params;

    /**
     * 请求方法
     */
    private String method;
}
