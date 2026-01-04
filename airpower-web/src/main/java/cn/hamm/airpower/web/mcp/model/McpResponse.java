package cn.hamm.airpower.web.mcp.model;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.web.mcp.common.McpJson;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * <h1>MCP 响应</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@JsonInclude(NON_NULL)
public class McpResponse extends McpJson {
    /**
     * 结果
     */
    private Object result;

    /**
     * 错误
     */
    private McpError error;

    /**
     * 错误
     */
    @Data
    @Accessors(chain = true)
    public static class McpError {
        /**
         * 错误码
         */
        private Integer code;

        /**
         * 错误信息
         */
        private String message;

        /**
         * 构造函数
         *
         * @param exception 异常
         */
        public McpError(@NotNull ServiceException exception) {
            this.code = exception.getCode();
            this.message = exception.getMessage();
        }
    }
}
