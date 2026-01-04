package cn.hamm.airpower.web.websocket;

/**
 * <h1>WebSocket 支持</h1>
 *
 * @author Hamm.cn
 */
public enum WebSocketSupport {
    /**
     * Redis
     */
    REDIS,

    /**
     * MQTT
     */
    MQTT,

    /**
     * 不支持
     */
    NO,
}
