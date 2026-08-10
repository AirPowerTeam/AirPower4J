# airpower-websocket 使用文档

> AirPower WebSocket 模块 - 通过 Redis Pub/Sub 或 MQTT 把 WebSocket 消息广播到所有节点，内置握手 Token
> 校验、统一通道前缀、连接 / 断开前后置钩子。

## 一、模块定位

`airpower-websocket` 基于 Spring `WebSocket` 实现，同时支持两种「跨实例广播」后端：

- **`REDIS`**：通过 `RedisMessageListenerContainer` 订阅频道。
- **`MQTT`**：通过 Paho MQTT 订阅主题。

业务侧使用 `WebSocketHelper` 发布即可，无需关心订阅细节。

| 组件                | 路径                                           | 作用                                              |
|---------------------|------------------------------------------------|---------------------------------------------------|
| `WebSocketConfig`   | `cn.hamm.airpower.websocket.WebSocketConfig`   | `airpower.websocket.*` 配置                       |
| `WebSocketSupport`  | `cn.hamm.airpower.websocket.WebSocketSupport`  | 广播后端枚举（REDIS / MQTT / NO）                 |
| `WebSocketHandler`  | `cn.hamm.airpower.websocket.WebSocketHandler`  | 握手 / 消息 / 订阅 / 关闭的完整生命周期           |
| `WebSocketHelper`   | `cn.hamm.airpower.websocket.WebSocketHelper`   | 业务层发送入口                                    |
| `WebSocketPayload`  | `cn.hamm.airpower.websocket.WebSocketPayload`  | 业务负载 `type + data`                            |
| `WebSocketEvent`    | `cn.hamm.airpower.websocket.WebSocketEvent`    | 实际下发的消息包，含 `id / from / time / payload` |
| `RedisPubSubConfig` | `cn.hamm.airpower.websocket.RedisPubSubConfig` | 提供 `RedisMessageListenerContainer` Bean         |

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-websocket</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

> 模块会自动带入 `airpower-core` / `airpower-api` / `airpower-mqtt` / `airpower-redis` / `airpower-exception` /
> `spring-websocket`。

## 三、应用配置

```yaml
airpower:
  websocket:
    path: /websocket
    ping: PING
    pong: PONG
    # REDIS / MQTT / NO（不启用 WebSocket 时设为 NO）
    support: REDIS
    channel-prefix:
      airpower:
    allowed-origins: "*"
```

> 当 `support=REDIS` 时，需要确保 `spring.data.redis.*` 可用；`support=MQTT` 时需要确保 `airpower.mqtt.*` 已配置。

## 四、注册 WebSocket 端点

继承 `WebSocketHandler` 并通过 `WebSocketConfigurer` 暴露路径：

```java
import cn.hamm.airpower.websocket.WebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketEndpoint implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;

    public WebSocketEndpoint(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/websocket")
                .setAllowedOriginPatterns("*");
    }
}
```

握手要求：客户端 URL 需携带 `?authorization=<accessToken>`，模块会用 `ApiConfig.accessTokenSecret` 校验。

## 五、订阅 / 接收消息

```java

@Component
public class MyHandler extends WebSocketHandler {

    @Override
    protected void afterConnectSuccess(WebSocketSession session) {
        // 连接成功后置（可以推送欢迎消息等）
        sendWebSocketPayload(session, new WebSocketPayload()
                .setType("welcome")
                .setData("hi"));
    }

    @Override
    protected void onWebSocketPayload(WebSocketPayload payload, WebSocketSession session) {
        // 收到前端发的业务消息
        if ("ping-from-client".equals(payload.getType())) {
            sendWebSocketPayload(session, payload.setData("pong"));
        }
    }

    @Override
    protected void afterDisconnect(WebSocketSession session, Long userId) {
        // 连接断开后置
    }
}
```

模块内建心跳：客户端发送 `PING`，框架会立即回 `PONG`。

## 六、发布消息

```java

@Autowired
private WebSocketHelper webSocketHelper;

// 广播到全部在线用户（基于 WEBSOCKET_ALL 频道）
webSocketHelper.

publish(new WebSocketPayload().

setType("system").

setData("升级公告"));

// 推送给指定用户（基于 WEBSOCKET_USER_<userId> 频道）
        webSocketHelper.

publishToUser(1024L,new WebSocketPayload().

setType("order").

setData("订单创建成功"));

// 推送到自定义频道
        webSocketHelper.

publishToChannel("airpower:WEBSOCKET_ROOM_A",payload);

// 带发送方 ID（写入 WebSocketEvent.from）
webSocketHelper.

publish(payload, getCurrentUserId());
```

实际订阅的频道名 = `{channelPrefix}_{CHANNEL}`，例如 `airpower:_WEBSOCKET_ALL`。

## 七、跨实例广播

模块依赖 `RedisHelper.publish` 或 `MqttHelper.publish` 将消息发布到广播后端，其他节点上的 `WebSocketHandler`
会自动把消息回推到本地客户端。这意味着：

- 任意节点调用 `publish` → 所有节点的连接客户端都能收到。
- 不需要业务代码自己处理「本节点 / 跨节点」判断。

## 八、关键类速查

| 类 / 枚举           | 路径                                           | 说明                          |
|---------------------|------------------------------------------------|-------------------------------|
| `WebSocketConfig`   | `cn.hamm.airpower.websocket.WebSocketConfig`   | 配置                          |
| `WebSocketSupport`  | `cn.hamm.airpower.websocket.WebSocketSupport`  | 后端枚举                      |
| `WebSocketHandler`  | `cn.hamm.airpower.websocket.WebSocketHandler`  | 处理器                        |
| `WebSocketHelper`   | `cn.hamm.airpower.websocket.WebSocketHelper`   | 发布入口                      |
| `WebSocketPayload`  | `cn.hamm.airpower.websocket.WebSocketPayload`  | 业务负载                      |
| `WebSocketEvent`    | `cn.hamm.airpower.websocket.WebSocketEvent`    | 下发包                        |
| `RedisPubSubConfig` | `cn.hamm.airpower.websocket.RedisPubSubConfig` | Redis 容器配置                |
| `Auto`              | `cn.hamm.airpower.websocket.Auto`              | `@AutoConfiguration` 装配入口 |

## 九、常见问题

1. **未启用 WebSocket 时启动报错？** 把 `airpower.websocket.support` 显式设为 `NO`，否则
   `WebSocketHelper.publishToChannel` 会要求配置 `channelPrefix`。
2. **跨域握手失败？** 调整 `allowed-origins` 或 `setAllowedOriginPatterns`。
3. **集群部署时只收到本节点消息？** 确认 `support` 配置正确，且 Redis / MQTT 服务可用。
4. **握手失败 `没有传入 AccessToken`？** 客户端必须在 URL 中携带 `?authorization=...` 或改用 Header（WebSocket 不支持自定义
   Header 握手）。
5. **如何主动踢人？** 当前实现未提供 API，可通过 `WebSocketHandler.userIdHashMap` + Redis Pub/Sub 自定义实现。