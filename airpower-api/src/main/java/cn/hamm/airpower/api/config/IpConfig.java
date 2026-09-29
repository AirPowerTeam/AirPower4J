package cn.hamm.airpower.api.config;

import cn.hamm.airpower.api.RequestUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>IP 工具配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.ip")
public class IpConfig {
    /**
     * 可信反向代理地址（IP 或 CIDR 网段）
     *
     * @apiNote 只有来自这些地址的请求才会解析 {@code X-Forwarded-For} 等代理头；
     * 留空表示不信任任何代理头，仅使用 TCP 连接对端地址
     */
    private List<String> trustedProxies = new ArrayList<>(RequestUtil.DEFAULT_TRUSTED_PROXIES);

    /**
     * 将配置同步到 {@link RequestUtil}
     */
    @PostConstruct
    public void apply() {
        RequestUtil.setTrustedProxies(trustedProxies);
    }
}
