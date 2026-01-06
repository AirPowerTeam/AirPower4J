package cn.hamm.airpower.web.websocket;

import cn.hamm.airpower.core.RandomUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <h1>WebSocket 事件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class WebSocketEvent {
    /**
     * 事件 ID
     */
    private String id;

    /**
     * 发送方 ID
     */
    private Long from;

    /**
     * 接收方 ID
     */
    private Long to;

    /**
     * 事件时间戳
     */
    private Long time;

    /**
     * 事件负载
     */
    private WebSocketPayload payload;

    /**
     * 创建 WebSocket 事件
     *
     * @param payload 负载
     * @return 事件
     */
    public static @NotNull WebSocketEvent create(WebSocketPayload payload) {
        return create().setPayload(payload);
    }

    /**
     * 创建 WebSocket 事件
     *
     * @return 事件
     */
    @Contract(" -> new")
    private static @NotNull WebSocketEvent create() {
        return new WebSocketEvent().resetEvent();
    }

    /**
     * 重置事件的 ID 和事件
     */
    @Contract(" -> this")
    protected final WebSocketEvent resetEvent() {
        time = System.currentTimeMillis();
        id = Base64.getEncoder().encodeToString((String.format(
                "%s-%s",
                time,
                RandomUtil.randomString(6)
        )).getBytes(StandardCharsets.UTF_8));
        return this;
    }
}