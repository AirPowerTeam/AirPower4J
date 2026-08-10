# airpower-mqtt 使用文档

> AirPower MQTT 模块 - 基于 Eclipse Paho 的 MQTT 客户端封装，提供客户端创建、连接选项、消息发布能力，与 `airpower-websocket`
> 协同作为发布后端。

## 一、模块定位

`airpower-mqtt` 通过 `MqttHelper` 暴露三个核心方法：

| 方法                                  | 行为                                                             |
|---------------------------------------|------------------------------------------------------------------|
| `createClient()` / `createClient(id)` | 创建内存持久化的 `MqttClient`                                    |
| `createOption()`                      | 生成 `MqttConnectOptions`（用户名 / 密码 / 30s 超时 / 10s 心跳） |
| `publish(topic, message)`             | 以 QoS 0 同步发布一条消息                                        |

> 模块默认连接 `tcp://<host>:<port>`，使用 `MemoryPersistence`，即每次调用 `publish(...)` 都会建立短连接后立即断开。
> **不建议在生产高频场景直接调用 `publish`，推荐复用 `createClient()` 与 `createOption()` 自己维护连接。**

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-mqtt</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

模块已自带 `spring-integration-mqtt`、`spring-boot-autoconfigure` 等依赖。

## 三、应用配置

```yaml
airpower:
  mqtt:
    user: ${MQTT_USER}
    pass: ${MQTT_PASS}
    host: 127.0.0.1
    port: 1883
```

源码：[MqttConfig.java](src/main/java/cn/hamm/airpower/mqtt/MqttConfig.java)。

## 四、发布消息（短连接示例）

```java
import cn.hamm.airpower.mqtt.MqttHelper;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;

@Service
public class NotifyService {

    private final MqttHelper mqttHelper;

    public NotifyService(MqttHelper mqttHelper) {
        this.mqttHelper = mqttHelper;
    }

    public void publish(String topic, String payload) throws MqttException {
        mqttHelper.publish(topic, payload);
    }
}
```

## 五、长连接订阅（推荐写法）

```java
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Component;

@Component
public class MqttConsumer {

    private final MqttHelper mqttHelper;
    private MqttClient client;

    public MqttConsumer(MqttHelper mqttHelper) {
        this.mqttHelper = mqttHelper;
    }

    @PostConstruct
    public void init() throws MqttException {
        client = mqttHelper.createClient("app-1");
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable t) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage msg) {
                System.out.println(topic + " -> " + new String(msg.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        client.connect(mqttHelper.createOption());
        client.subscribe("airpower/event/#");
    }

    @PreDestroy
    public void close() throws MqttException {
        if (client != null) client.disconnect();
    }
}
```

## 六、与 `airpower-websocket` 协同

`airpower-websocket` 在 `WebSocketSupport.MQTT` 模式下会调用 `MqttHelper.createClient()` / `createOption()` 订阅
`WEBSOCKET_USER_<id>` 和 `WEBSOCKET_ALL` 两个频道，并把 `WebSocketHelper.publishToChannel(...)` 的消息桥接出去，业务侧通常不需要关心细节。

## 七、关键类速查

| 类           | 路径                               | 说明                          |
|--------------|------------------------------------|-------------------------------|
| `MqttHelper` | `cn.hamm.airpower.mqtt.MqttHelper` | 客户端创建、连接选项、发布    |
| `MqttConfig` | `cn.hamm.airpower.mqtt.MqttConfig` | `airpower.mqtt.*` 配置        |
| `Auto`       | `cn.hamm.airpower.mqtt.Auto`       | `@AutoConfiguration` 装配入口 |

## 八、常见问题

1. **使用 `tcp://` 还是 `ssl://`？** 当前实现硬编码 `tcp://`，如需 TLS 可在自定义代码里修改连接 URL。
2. **生产环境短连接发布丢消息？** 把 `publish(...)` 替换为长连接复用，或直接使用 `RabbitMQ` / `Redis Pub/Sub`。
3. **遇到 `MqttException (4) MQTT Client is not connected`？** `publish(...)` 内部已 `try-with-resources` 但若 broker
   端抖动频繁，建议改为长连接 + 重连机制。