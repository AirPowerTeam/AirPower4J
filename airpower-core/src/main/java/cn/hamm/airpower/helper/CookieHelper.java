package cn.hamm.airpower.helper;

import cn.hamm.airpower.config.CookieConfig;
import jakarta.servlet.http.Cookie;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <h1>{@code Cookie} 助手</h1>
 *
 * @author Hamm.cn
 */
@Component
public class CookieHelper {
    /**
     * <h3>{@code Cookie}</h3>
     */
    public static final String COOKIE = "Cookie";

    /**
     * <h3>默认路径</h3>
     */
    public static final String DEFAULT_PATH = "/";

    @Autowired
    private CookieConfig cookieConfig;

    /**
     * <h3>获取一个 {@code Cookie}</h3>
     *
     * @param key   {@code Cookie} 键
     * @param value {@code Cookie} 值
     * @return {@code Cookie}
     * @see CookieConfig
     */
    public final @NotNull Cookie getCookie(String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setHttpOnly(cookieConfig.isCookieHttpOnly());
        cookie.setMaxAge(cookieConfig.getCookieMaxAge());
        cookie.setSecure(cookieConfig.isCookieSecurity());
        cookie.setPath(cookie.getPath());
        return cookie;
    }

    /**
     * <h3>获取一个身份验证的 {@code Cookie}</h3>
     *
     * @param value 身份串的值
     * @return {@code Cookie}
     * @see CookieConfig
     */
    public final @NotNull Cookie getAuthorizeCookie(String value) {
        return getCookie(cookieConfig.getAuthCookieName(), value);
    }
}
