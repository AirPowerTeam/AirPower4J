package cn.hamm.airpower.web.access;

import cn.hamm.airpower.core.DateTimeUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * <h1>授权相关配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.access")
public class AccessConfig {
    /**
     * {@code AccessToken} 的密钥
     */
    private String accessTokenSecret;

    /**
     * 身份令牌 header 的 key
     */
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;

    /**
     * 身份令牌有效期
     */
    private long authorizeExpireSecond = DateTimeUtil.SECOND_PER_DAY;
}
