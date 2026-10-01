package cn.hamm.airpower.websocket;

import cn.hamm.airpower.api.config.ApiConfig;
import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.mqtt.MqttHelper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
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

import static cn.hamm.airpower.exception.Errors.WEBSOCKET_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <h1>WebSocket 会话处理器</h1>
 *
 * @author Hamm
 */
@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler implements MessageListener {
    /**
     * 用户私有频道的逻辑名前缀，实际频道为 {@code 前缀_WEBSOCKET_USER_用户ID}
     *
     * @apiNote 逻辑频道名，真实频道还要拼上 {@link WebSocketConfig#getChannelPrefix()}，见 {@link #getRealChannel(String)}
     */
    public static final String CHANNEL_USER_PREFIX = "WEBSOCKET_USER_";

    /**
     * 广播给所有在线会话的逻辑频道名
     *
     * @apiNote 逻辑频道名，真实频道还要拼上 {@link WebSocketConfig#getChannelPrefix()}，见 {@link #getRealChannel(String)}
     */
    public static final String CHANNEL_ALL = "WEBSOCKET_ALL";

    /**
     * 会话 ID 到 Redis 订阅连接的映射，断开时需逐个关闭，否则连接池被占满
     */
    protected final ConcurrentHashMap<String, RedisConnection> redisConnectionHashMap = new ConcurrentHashMap<>();

    /**
     * 会话 ID 到 MQTT 客户端的映射，断开时需逐个关闭
     */
    protected final ConcurrentHashMap<String, MqttClient> mqttClientHashMap = new ConcurrentHashMap<>();

    /**
     * 会话 ID 到登录用户 ID 的映射
     */
    protected final ConcurrentHashMap<String, Long> userIdHashMap = new ConcurrentHashMap<>();

    @Autowired
    protected WebSocketConfig webSocketConfig;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    @Autowired
    protected MqttHelper mqttHelper;

    @Autowired
    private ApiConfig apiConfig;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    /**
     * 收到 WebSocket 文本消息时
     *
     * @param session     会话
     * @param textMessage 文本消息
     * @apiNote 先比对心跳报文（{@link WebSocketConfig#getPing()}），命中就直接回 {@code PONG} 并结束，
     * 不走 JSON 反序列化。解析失败只记日志不断开连接，避免客户端一条脏数据就掉线
     */
    @Override
    protected final void handleTextMessage(@NonNull WebSocketSession session, @NotNull TextMessage textMessage) {
        final String message = textMessage.getPayload();
        if (webSocketConfig.getPing().equalsIgnoreCase(message)) {
            try {
                session.sendMessage(new TextMessage(webSocketConfig.getPong()));
            } catch (Exception e) {
                log.error("发送 WebSocket PONG 失败: {}", e.getMessage());
            }
            return;
        }
        try {
            WebSocketPayload webSocketPayload = Json.parse(message, WebSocketPayload.class);
            onWebSocketPayload(webSocketPayload, session);
        } catch (Exception e) {
            log.info("解析 WebSocket 负载失败: {} {}", message, e.getMessage());
        }
    }

    /**
     * 向下推送事件负载
     *
     * @param session          会话
     * @param webSocketPayload 事件负载
     * @apiNote 下行消息会补上事件 ID 与时间戳，客户端应以此判断是否为同一次事件的重复推送
     */
    protected final void sendWebSocketPayload(@NotNull WebSocketSession session,
                                              @NotNull WebSocketPayload webSocketPayload) {
        try {
            session.sendMessage(new TextMessage(Json.toString(WebSocketEvent.create(webSocketPayload))));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败: {}", e.getMessage());
            throw new ServiceException("发送 WebSocket 消息失败，" + e.getMessage());
        }
    }

    /**
     * 收到客户端上行负载时的扩展点
     *
     * @param webSocketPayload 负载对象
     * @param session          会话
     * @apiNote 基类只记日志，业务消息的处理交由子类重写此方法，不要改基类的解析逻辑
     */
    protected void onWebSocketPayload(@NotNull WebSocketPayload webSocketPayload, @NotNull WebSocketSession session) {
        log.info("负载类型: {}, 负载内容: {}", webSocketPayload.getType(), webSocketPayload.getData());
    }

    /**
     * 建立连接：鉴权后按配置订阅消息源
     *
     * @param session 会话
     * @apiNote 令牌取自 query string（整段当作令牌，不解析键值对），详见类注释。
     * 令牌无效会抛异常，被这里的 catch 吞掉并只记日志——连接不会关闭，
     * 客户端能连上但收不到任何消息
     */
    @Override
    public final void afterConnectionEstablished(@NonNull WebSocketSession session) {
        if (Objects.isNull(session.getUri())) {
            return;
        }
        String accessToken = session.getUri().getQuery();
        if (Objects.isNull(accessToken)) {
            log.error("没有传入AccessToken 即将关闭连接");
            closeConnection(session);
            return;
        }
        AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create()
                .verify(accessToken, apiConfig.getAccessTokenSecret());
        long userId = verifiedToken.getPayloadId();
        log.info("Websocket连接成功 {}", userId);
        try {
            switch (webSocketConfig.getSupport()) {
                case REDIS -> startRedisListener(session, userId);
                case MQTT -> startMqttListener(session, userId);
                case NO -> {
                }
                default -> throw new ServiceException("WebSocket 暂不支持");
            }
            userIdHashMap.put(session.getId(), userId);
            log.info("Websocket连接成功 {}", userId);
            afterConnectSuccess(session);
        } catch (Exception exception) {
            log.info("连接失败 {}", exception.getMessage());
        }
    }

    /**
     * 连接成功后的扩展点
     *
     * @param session 会话
     * @apiNote 订阅已就绪，可以直接往 {@link #subscribe(String, WebSocketSession)} 里加业务频道
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

        redisMessageListenerContainer.addMessageListener(
                (message, pattern) -> {
                    synchronized (session) {
                        onChannelMessage(new String(message.getBody(), UTF_8), session);
                    }
                },
                ChannelTopic.of(getRealChannel(CHANNEL_ALL))
        );
        redisMessageListenerContainer.addMessageListener(
                (message, pattern) -> {
                    synchronized (session) {
                        onChannelMessage(new String(message.getBody(), UTF_8), session);
                    }
                },
                ChannelTopic.of(personalChannel)
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
        } catch (MqttException ignored) {
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
            log.error("关闭 WebSocket 失败");
        }
    }

    /**
     * 连接断开：释放该会话占用的连接与订阅
     *
     * @param session 会话
     * @param status  关闭状态
     * @apiNote 方法是 {@code final} 的，子类改不了；释放动作全部由基类完成，
     * 业务清理请写在 {@link #afterDisconnect(WebSocketSession, Long)} 里
     */
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
            afterDisconnect(session, userId);
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
        log.info("MQTT 开始订阅频道: {}", getRealChannel(channel));
        try {
            getMqttClient(session).subscribe(getRealChannel(channel));
        } catch (MqttException e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("订阅 MQTT 频道失败，" + e.getMessage());
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
            log.error(e.getMessage(), e);
            throw new ServiceException("取消订阅 MQTT 频道失败，" + e.getMessage());
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
