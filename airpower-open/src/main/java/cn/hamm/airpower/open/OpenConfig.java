package cn.hamm.airpower.open;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>全局默认配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.open")
public class OpenConfig {
    /**
     * 私钥
     */
    public static String PRIVATE_KEY = "";

    /**
     * <h1>私钥</h1>
     */
    private String privateKey = "";

    /**
     * 同步配置
     */
    @PostConstruct
    public void apply() {
        PRIVATE_KEY = privateKey;
    }
}
