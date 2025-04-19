package cn.hamm.airpower.config;

import cn.hamm.airpower.helper.CookieHelper;
import cn.hamm.airpower.util.DateTimeUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * <h1>{@code Cookie} 相关配置</h1>
 *
 * @author Hamm.cn
 * @see CookieHelper
 */
@Component
@Data
@Accessors(chain = true)
@Configuration
@ConfigurationProperties("airpower.cookie")
public class CookieConfig {
    /**
     * <h3>{@code Cookie} 的路径</h3>
     */
    private String cookiePath = CookieHelper.DEFAULT_PATH;

    /**
     * <h3>身份验证的 {@code Cookie} 名称</h3>
     */
    private String authCookieName = "authorization-key";

    /**
     * <h3>{@code Cookie} 的 {@code HttpOnly} 配置</h3>
     */
    private boolean cookieHttpOnly = true;

    /**
     * <h3>{@code Cookie} 有效期</h3>
     */
    private int cookieMaxAge = DateTimeUtil.SECOND_PER_DAY;

    /**
     * <h3>使用 {@code Https} 方式的安全 {@code Cookie}</h3>
     */
    private boolean cookieSecurity = true;
}
