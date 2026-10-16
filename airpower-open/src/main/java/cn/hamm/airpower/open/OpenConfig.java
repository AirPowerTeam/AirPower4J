package cn.hamm.airpower.open;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h2>全局默认配置文件</h2>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.open")
public class OpenConfig {
    /**
     * Redis 前缀
     */
    private static final String REDIS_PREFIX = "open:";

    /**
     * 开放接口基础错误码
     */
    public static int OPEN_BASE_CODE;

    /**
     * 私钥
     */
    public static String PRIVATE_KEY;

    /**
     * <h2>私钥</h2>
     */
    private String privateKey = "";

    /**
     * <h2>开放接口基础错误码</h2>
     */
    private Integer openBaseCode = 100000;

    /**
     * 同步配置
     */
    @PostConstruct
    public void apply() {
        PRIVATE_KEY = privateKey;
        OPEN_BASE_CODE = openBaseCode;
    }
}
