package cn.hamm.airpower.mcp.model;

import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.mcp.common.McpJson;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * <h1>MCP响应</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@JsonInclude(NON_NULL)
public class McpResponse extends McpJson {
    /**
     * <h3>结果</h3>
     */
    private Object result;

    /**
     * <h3>错误</h3>
     */
    private McpError error;

    /**
     * <h3>错误</h3>
     */
    @Data
    @Accessors(chain = true)
    public static class McpError {
        /**
         * <h3>错误码</h3>
         */
        private Integer code;

        /**
         * <h3>错误信息</h3>
         */
        private String message;

        /**
         * <h3>构造函数</h3>
         *
         * @param exception 异常
         */
        public McpError(@NotNull ServiceException exception) {
            this.code = exception.getCode();
            this.message = exception.getMessage();
        }
    }
}
