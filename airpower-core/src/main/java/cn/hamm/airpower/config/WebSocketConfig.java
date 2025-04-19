package cn.hamm.airpower.config;

import cn.hamm.airpower.websocket.WebSocketSupport;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static cn.hamm.airpower.websocket.WebSocketSupport.NO;

/**
 * <h1>WebSocket 配置</h1>
 *
 * @author Hamm
 */
@Component
@Data
@Accessors(chain = true)
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
    private String channelPrefix;

    /**
     * WebSocket 允许的跨域
     */
    private String allowedOrigins = "*";
}
