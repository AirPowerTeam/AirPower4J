package cn.hamm.airpower.http.cookie;

import jakarta.servlet.http.Cookie;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <h1> Cookie 助手</h1>
 *
 * @author Hamm.cn
 */
@Component
public class CookieHelper {
    /**
     * 默认路径
     */
    public static final String DEFAULT_PATH = "/";

    @Autowired
    private CookieConfig cookieConfig;

    /**
     * 获取一个  Cookie
     *
     * @param key   Cookie 键
     * @param value Cookie 值
     * @return Cookie
     * @see CookieConfig
     */
    public final @NotNull Cookie getCookie(String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setHttpOnly(cookieConfig.isCookieHttpOnly());
        cookie.setMaxAge(cookieConfig.getCookieMaxAge());
        cookie.setSecure(cookieConfig.isCookieSecurity());
        cookie.setPath(cookieConfig.getCookiePath());
        return cookie;
    }

    /**
     * 获取一个身份验证的  Cookie
     *
     * @param value 身份串的值
     * @return Cookie
     * @see CookieConfig
     */
    public final @NotNull Cookie getAuthorizeCookie(String value) {
        return getCookie(cookieConfig.getAuthCookieName(), value);
    }
}
