package cn.hamm.airpower.api;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>API配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.api")
public class ApiConfig {
    /**
     * 输出请求包体日志
     */
    private Boolean requestLog = false;

    /**
     * 输出响应包体日志
     */
    private Boolean responseLog = false;

    /**
     * 响应 Trace 到返回包体
     */
    private Boolean bodyTraceId = true;
}
