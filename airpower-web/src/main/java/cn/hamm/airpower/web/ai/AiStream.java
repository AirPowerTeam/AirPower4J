package cn.hamm.airpower.web.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Objects;

/**
 * <h1>流式返回对象</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class AiStream {
    /**
     * 是否完成
     */
    private Boolean isDone = false;

    /**
     * 响应对象
     */
    private AiResponse response;

    /**
     * 获取响应消息
     *
     * @return 响应消息
     */
    @JsonIgnore
    public String getResponseMessage() {
        if (Objects.isNull(response)) {
            return "";
        }
        return response.getResponseMessage();
    }

    /**
     * 获取流式消息
     *
     * @return 响应消息
     */
    @JsonIgnore
    public String getStreamMessage() {
        if (Objects.isNull(response)) {
            return "";
        }
        return response.getStreamMessage();
    }
}
