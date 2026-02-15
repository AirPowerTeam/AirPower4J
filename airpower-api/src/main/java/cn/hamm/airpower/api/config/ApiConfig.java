package cn.hamm.airpower.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * <h1>API 配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.api")
public class ApiConfig {
    /**
     * 服务是否运行
     */
    public static boolean isServerRunning = true;

    /**
     * 输出请求包体日志
     */
    private Boolean requestLog = true;

    /**
     * 输出响应包体日志
     */
    private Boolean responseLog = true;

    /**
     * 响应 Trace 到返回包体
     */
    private Boolean bodyTraceId = true;

    /**
     * {@code AccessToken} 的密钥
     */
    private String accessTokenSecret;

    /**
     * 身份令牌 header 的 key
     */
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;
}
