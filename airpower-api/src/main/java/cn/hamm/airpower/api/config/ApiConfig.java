package cn.hamm.airpower.api.config;

import cn.hamm.airpower.api.RequestUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * <h1>API 模块配置</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.api")
public class ApiConfig {
    /**
     * 输出请求包体日志
     *
     * @apiNote 如配置为 <code>false</code>, 则全局不会输出请求包体日志
     */
    private Boolean requestLog = true;

    /**
     * 输入到日志的请求头列表
     */
    private String[] requestLogHeaders = {HttpHeaders.AUTHORIZATION, HttpHeaders.REFERER, HttpHeaders.USER_AGENT};

    /**
     * 输出响应包体日志
     *
     * @apiNote 如配置为 <code>false</code>, 则全局不会输出响应包体日志
     */
    private Boolean responseLog = true;

    /**
     * {@code AccessToken} 的密钥
     */
    private String accessTokenSecret;

    /**
     * 身份令牌所在的请求参数名或请求头名
     */
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;

    /**
     * 可信代理头
     *
     * @apiNote 留空表示不信任任何代理头，来源 IP 一律取 TCP 对端地址。
     * 该头可被客户端伪造，代理侧必须强制覆盖客户端传入的同名头，否则 IP 白名单、限流都可被绕过
     */
    private String trustProxyHeader = "";

    /**
     * 将可信代理头配置同步到 {@link RequestUtil}
     */
    @PostConstruct
    public void apply() {
        RequestUtil.setTrustProxyHeader(trustProxyHeader);
    }
}
