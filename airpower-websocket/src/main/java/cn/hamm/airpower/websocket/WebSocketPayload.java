package cn.hamm.airpower.websocket;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>WebSocket 事件负载</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class WebSocketPayload {
    /**
     * 负载类型
     */
    private String type = "system";

    /**
     * 负载数据
     */
    private String data;
}