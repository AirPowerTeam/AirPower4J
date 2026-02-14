package cn.hamm.airpower.websocket;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.websocket.WebSocketSupport.NO;

/**
 * <h1>WebSocket 配置</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.websocket")
public class WebSocketConfig {
    /**
     * PING
     */
    private String ping = "PING";

    /**
     * PONG
     */
    private String pong = "PONG";

    /**
     * WebSocket 路径
     */
    private String path = "/websocket";

    /**
     * WebSocket 支持方式
     */
    private WebSocketSupport support = NO;

    /**
     * 发布订阅的频道前缀
     */
    private String channelPrefix = "airpower:";

    /**
     * WebSocket 允许的跨域
     */
    private String allowedOrigins = "*";
}
