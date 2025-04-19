package cn.hamm.airpower.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static cn.hamm.airpower.util.RequestUtil.LOCAL_IP_ADDRESS;

/**
 * <h1>MQTT 配置类</h1>
 *
 * @author Hamm.cn
 */
@Component
@Data
@Accessors(chain = true)
@Configuration
@ConfigurationProperties("airpower.mqtt")
public class MqttConfig {
    /**
     * 用户
     */
    private String user = "";

    /**
     * 密码
     */
    private String pass = "";

    /**
     * 地址
     */
    private String host = LOCAL_IP_ADDRESS;

    /**
     * 端口
     */
    private String port = "1883";
}
