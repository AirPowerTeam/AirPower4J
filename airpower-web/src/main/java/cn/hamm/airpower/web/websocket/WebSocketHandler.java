package cn.hamm.airpower.web.websocket;

import cn.hamm.airpower.util.exception.ServiceException;
import cn.hamm.airpower.web.access.AccessConfig;
import cn.hamm.airpower.web.access.AccessTokenUtil;
import cn.hamm.airpower.web.api.Json;
import cn.hamm.airpower.web.mqtt.MqttHelper;
import cn.hamm.airpower.web.util.TaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static cn.hamm.airpower.web.exception.ServiceError.WEBSOCKET_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <h1>WebSocket Handler</h1>
 *
 * @author Hamm
 */
@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler implements MessageListener {
    /**
     * 订阅用户频道前缀
     */
    public static final String CHANNEL_USER_PREFIX = "WEBSOCKET_USER_";

    /**
     * 订阅全频道
     */
    public static final String CHANNEL_ALL = "WEBSOCKET_ALL";

    /**
     * Redis 连接列表
     */
    protected final ConcurrentHashMap<String, RedisConnection> redisConnectionHashMap = new ConcurrentHashMap<>();

    /**
     * MQTT 客户端列表
     */
    protected final ConcurrentHashMap<String, MqttClient> mqttClientHashMap = new ConcurrentHashMap<>();

    /**
     * 用户 ID 列表
     */
    protected final ConcurrentHashMap<String, Long> userIdHashMap = new ConcurrentHashMap<>();

    @Autowired
    protected WebSocketConfig webSocketConfig;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    @Autowired
    protected MqttHelper mqttHelper;

    @Autowired
    private AccessConfig accessConfig;

    /**
     * 收到 Websocket 消息时
     *
     * @param session     会话
     * @param textMessage 文本消息
     */
    @Override
    protected final void handleTextMessage(@NonNull WebSocketSession session, @NotNull TextMessage textMessage) {
        final String message = textMessage.getPayload();
        if (webSocketConfig.getPing().equalsIgnoreCase(message)) {
            try {
                session.sendMessage(new TextMessage(webSocketConfig.getPong()));
            } catch (Exception e) {
                log.error("发送 Websocket 消息失败: {}", e.getMessage());
            }
            return;
        }
        WebSocketPayload webSocketPayload = Json.parse(message, WebSocketPayload.class);
        onWebSocketPayload(webSocketPayload, session);
    }

    /**
     * 发送 {@code } 事件负载
     *
     * @param session          会话
     * @param webSocketPayload 事件负载
     */
    protected final void sendWebSocketPayload(@NotNull WebSocketSession session,
                                              @NotNull WebSocketPayload webSocketPayload) {
        try {
            session.sendMessage(new TextMessage(Json.toString(WebSocketEvent.create(webSocketPayload))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 当 WebSocket 负载到达时
     *
     * @param webSocketPayload 负载对象
     */
    protected void onWebSocketPayload(@NotNull WebSocketPayload webSocketPayload, @NotNull WebSocketSession session) {
        log.info("负载类型: {}, 负载内容: {}", webSocketPayload.getType(), webSocketPayload.getData());
    }

    /**
     * 连接就绪后监听队列
     *
     * @param session 会话
     */
    @Override
    public final void afterConnectionEstablished(@NonNull WebSocketSession session) {
        if (Objects.isNull(session.getUri())) {
            return;
        }
        String accessToken = session.getUri().getQuery();
        if (Objects.isNull(accessToken)) {
            log.warn("没有传入AccessToken 即将关闭连接");
            closeConnection(session);
            return;
        }
        AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create()
                .verify(accessToken, accessConfig.getAccessTokenSecret());
        long userId = verifiedToken.getPayloadId();
        switch (webSocketConfig.getSupport()) {
            case REDIS -> startRedisListener(session, userId);
            case MQTT -> startMqttListener(session, userId);
            case NO -> {
            }
            default -> throw new RuntimeException("WebSocket 暂不支持");
        }
        userIdHashMap.put(session.getId(), userId);
        TaskUtil.run(() -> afterConnectSuccess(session));
    }

    /**
     * 连接成功后置方法
     *
     * @param session 会话
     */
    protected void afterConnectSuccess(@NonNull WebSocketSession session) {
        log.info("连接成功 会话ID: {}", session.getId());
    }

    /**
     * 处理监听到的频道消息
     *
     * @param message 消息
     * @param session 连接
     */
    private void onChannelMessage(@NotNull String message, @NonNull WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception exception) {
            log.error("消息发送失败", exception);
        }
    }

    /**
     * 开始监听 Redis 消息
     *
     * @param session WebSocket 会话
     * @param userId  用户 ID
     */
    private void startRedisListener(@NotNull WebSocketSession session, long userId) {
        final String personalChannel = getRealChannel(CHANNEL_USER_PREFIX + userId);
        RedisConnection redisConnection = redisConnectionFactory.getConnection();
        redisConnectionHashMap.put(session.getId(), redisConnection);
        redisConnection.subscribe((message, pattern) -> {
                    synchronized (session) {
                        onChannelMessage(new String(message.getBody(), UTF_8), session);
                    }
                },
                getRealChannel(CHANNEL_ALL).getBytes(UTF_8),
                personalChannel.getBytes(UTF_8)
        );
    }

    /**
     * 开始监听 MQTT 消息
     *
     * @param session WebSocket 会话
     * @param userId  用户 ID
     */
    private void startMqttListener(@NotNull WebSocketSession session, long userId) {
        try (MqttClient mqttClient = mqttHelper.createClient()) {
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) {
                    synchronized (session) {
                        onChannelMessage(new String(mqttMessage.getPayload(), UTF_8), session);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

                }
            });
            mqttClient.connect(mqttHelper.createOption());
            final String personalChannel = CHANNEL_USER_PREFIX + userId;
            String[] topics = {CHANNEL_ALL, personalChannel};
            mqttClient.subscribe(topics);
            mqttClientHashMap.put(session.getId(), mqttClient);
        } catch (MqttException e) {
            throw new ServiceException(e);
        }
    }

    /**
     * 关闭连接
     *
     * @param session 会话
     */
    private void closeConnection(@NotNull WebSocketSession session) {
        try {
            session.close();
        } catch (IOException e) {
            log.error("关闭 Websocket 失败");
        }
    }

    @Contract(pure = true)
    @Override
    public final void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        try {
            String sessionId = session.getId();
            Long userId = userIdHashMap.get(sessionId);
            if (Objects.nonNull(userId)) {
                userIdHashMap.remove(sessionId);
            }
            if (Objects.nonNull(redisConnectionHashMap.get(sessionId))) {
                redisConnectionHashMap.remove(sessionId).close();
            }
            if (Objects.nonNull(mqttClientHashMap.get(sessionId))) {
                mqttClientHashMap.remove(sessionId).close();
            }
            TaskUtil.run(() -> afterDisconnect(session, userId));
        } catch (Exception exception) {
            log.error(exception.getMessage());
        }
    }

    /**
     * 断开连接后置方法
     *
     * @param session 会话
     * @param userId  用户 ID
     */
    protected void afterDisconnect(@NonNull WebSocketSession session, @Nullable Long userId) {

    }

    @Contract(pure = true)
    @Override
    public final void onMessage(@NotNull Message message, byte[] pattern) {
    }

    /**
     * Redis 订阅
     *
     * @param channel 传入的频道
     * @param session WebSocket 会话
     */
    protected final void redisSubscribe(@NotNull String channel, WebSocketSession session) {
        log.info("REDIS开始订阅频道: {}", getRealChannel(channel));
        getRedisSubscription(session).subscribe(getRealChannel(channel).getBytes(UTF_8));
    }

    /**
     * MQTT 订阅
     *
     * @param channel 传入的频道
     * @param session WebSocket 会话
     */
    protected final void mqttSubscribe(String channel, WebSocketSession session) {
        log.info("MQTT开始订阅频道: {}", getRealChannel(channel));
        try {
            getMqttClient(session).subscribe(getRealChannel(channel));
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取真实的频道
     *
     * @param channel 传入的频道
     * @return 带前缀的真实频道
     */
    @Contract(pure = true)
    protected final @NotNull String getRealChannel(String channel) {
        return webSocketConfig.getChannelPrefix() + "_" + channel;
    }

    /**
     * Redis 取消订阅
     *
     * @param channel 传入的频道
     * @param session WebSocket 会话
     */
    protected final void redisUnSubscribe(@NotNull String channel, WebSocketSession session) {
        log.info("REDIS取消订阅频道: {}", getRealChannel(channel));
        getRedisSubscription(session).unsubscribe(getRealChannel(channel).getBytes(UTF_8));
    }

    /**
     * MQTT 取消订阅
     *
     * @param channel 传入的频道
     * @param session WebSocket 会话
     */
    protected final void mqttUnSubscribe(String channel, WebSocketSession session) {
        log.info("MQTT取消订阅频道: {}", getRealChannel(channel));
        try {
            getMqttClient(session).unsubscribe(getRealChannel(channel));
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 MQTT 客户端
     *
     * @param session WebSocket 会话
     * @return MQTT 客户端
     */
    protected final MqttClient getMqttClient(@NotNull WebSocketSession session) {
        MqttClient mqttClient = mqttClientHashMap.get(session.getId());
        WEBSOCKET_ERROR.whenNull(mqttClient, "mqttClient is null");
        return mqttClient;
    }

    /**
     * 获取 Redis 订阅
     *
     * @param session WebSocket 会话
     * @return Redis 订阅
     */
    protected final Subscription getRedisSubscription(@NotNull WebSocketSession session) {
        RedisConnection redisConnection = redisConnectionHashMap.get(session.getId());
        WEBSOCKET_ERROR.whenNull(redisConnection, "redisConnection is null");
        Subscription subscription = redisConnection.getSubscription();
        WEBSOCKET_ERROR.whenNull(subscription, "subscription is null");
        return subscription;
    }

    /**
     * 订阅
     *
     * @param channel 频道
     * @param session WebSocket 会话
     */
    protected final void subscribe(String channel, WebSocketSession session) {
        switch (webSocketConfig.getSupport()) {
            case REDIS:
                redisSubscribe(channel, session);
                break;
            case MQTT:
                mqttSubscribe(channel, session);
                break;
            default:
                break;
        }
    }

    /**
     * 取消订阅
     *
     * @param channel 频道
     * @param session WebSocket 会话
     */
    protected final void unsubscribe(String channel, WebSocketSession session) {
        switch (webSocketConfig.getSupport()) {
            case REDIS:
                redisUnSubscribe(channel, session);
                break;
            case MQTT:
                mqttUnSubscribe(channel, session);
                break;
            default:
                break;
        }
    }
}
