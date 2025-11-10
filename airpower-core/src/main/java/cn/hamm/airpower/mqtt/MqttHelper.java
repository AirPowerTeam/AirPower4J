package cn.hamm.airpower.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * <h1>MQTT 助手类</h1>
 *
 * @author Hamm.cn
 */
@Configuration
@Slf4j
public class MqttHelper {
    @Autowired
    private MqttConfig mqttConfig;

    /**
     * 创建 MQTT 客户端
     *
     * @return 配置
     * @throws MqttException 异常
     */
    public @NotNull MqttClient createClient() throws MqttException {
        return createClient(UUID.randomUUID().toString());
    }

    /**
     * 创建 MQTT 客户端
     *
     * @param id 客户端 {@code ID}
     * @return 配置
     * @throws MqttException 异常
     */
    public @NotNull MqttClient createClient(String id) throws MqttException {
        return new MqttClient(
                "tcp://" + mqttConfig.getHost() + ":" + mqttConfig.getPort(),
                id,
                new MemoryPersistence()
        );
    }

    /**
     * 创建配置
     *
     * @return 配置
     */
    public MqttConnectOptions createOption() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setUserName(mqttConfig.getUser());
        options.setPassword(mqttConfig.getPass().toCharArray());
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(10);
        return options;
    }

    /**
     * 发送消息
     *
     * @param topic   主题
     * @param message 消息内容
     */
    public void publish(@NotNull String topic, @NotNull String message) throws MqttException {
        log.info("MQTT 发送消息：{} {}", topic, message);
        try (MqttClient client = createClient()) {
            client.connect(createOption());
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(message.getBytes());
            mqttMessage.setQos(0);
            MqttTopic mqttTopic = client.getTopic(topic);
            MqttDeliveryToken token;
            token = mqttTopic.publish(mqttMessage);
            token.waitForCompletion();
            client.disconnect();
        }
    }
}
