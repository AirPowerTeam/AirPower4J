package cn.hamm.airpower.websocket;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.mqtt.MqttHelper;
import cn.hamm.airpower.redis.RedisHelper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static cn.hamm.airpower.websocket.WebSocketHandler.CHANNEL_ALL;
import static cn.hamm.airpower.websocket.WebSocketHandler.CHANNEL_USER_PREFIX;

/**
 * <h1>WebSocketHelper</h1>
 *
 * @author Hamm
 */
@Slf4j
@Component
public class WebSocketHelper {
    @Autowired
    private WebSocketConfig websocketConfig;

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private MqttHelper mqttHelper;

    /**
     * 发布事件负载
     *
     * @param payload 事件负载
     */
    public final void publish(WebSocketPayload payload) {
        publishToChannel(CHANNEL_ALL, payload);
    }

    /**
     * 发布事件负载到指定的用户
     *
     * @param userId  目标用户 {@code ID}
     * @param payload 事件负载
     */
    public final void publishToUser(long userId, WebSocketPayload payload) {
        publishToChannel(CHANNEL_USER_PREFIX + userId, payload);
    }

    /**
     * 发布事件负载到指定的频道
     *
     * @param channel 频道
     * @param payload 负载
     */
    public final void publishToChannel(String channel, WebSocketPayload payload) {
        final String channelPrefix = websocketConfig.getChannelPrefix();
        if (!StringUtils.hasText(channelPrefix)) {
            throw new ServiceException("没有配置 airpower.websocket.channelPrefix, 无法启动WebSocket服务");
        }
        final WebSocketEvent event = WebSocketEvent.create(payload);
        final String targetChannel = channelPrefix + "_" + channel;
        log.info("发布消息到频道 {} : {}", targetChannel, Json.toString(event));
        try {
            switch (websocketConfig.getSupport()) {
                case REDIS -> redisHelper.publish(targetChannel, Json.toString(event));
                case MQTT -> mqttHelper.publish(targetChannel, Json.toString(event));
                default -> throw new ServiceException("WebSocket 暂不支持");
            }
        } catch (MqttException e) {
            log.error("发布消息失败", e);
            throw new ServiceException("发布消息失败，" + e.getMessage());
        }
    }
}
