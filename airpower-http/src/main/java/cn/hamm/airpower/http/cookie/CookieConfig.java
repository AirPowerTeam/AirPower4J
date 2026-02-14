package cn.hamm.airpower.http.cookie;

import cn.hamm.airpower.core.DateTimeUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1> Cookie 相关配置</h1>
 *
 * @author Hamm.cn
 * @see CookieHelper
 */
@Data
@Configuration
@ConfigurationProperties("airpower.cookie")
public class CookieConfig {
    /**
     * Cookie 的路径
     */
    private String cookiePath = CookieHelper.DEFAULT_PATH;

    /**
     * 身份验证的 Cookie 名称
     */
    private String authCookieName = "authorization-key";

    /**
     * Cookie 的 HttpOnly 配置
     */
    private boolean cookieHttpOnly = true;

    /**
     * Cookie 有效期
     */
    private int cookieMaxAge = DateTimeUtil.SECOND_PER_DAY;

    /**
     * 使用 Https 方式的安全  Cookie
     */
    private boolean cookieSecurity = true;
}
