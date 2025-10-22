package cn.hamm.airpower.ai;

import lombok.Data;
import lombok.experimental.Accessors;

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
}
