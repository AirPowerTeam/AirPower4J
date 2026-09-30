package cn.hamm.airpower.api;

import cn.hamm.airpower.api.config.ApiConfig;
import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.airpower.core.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * <h1>API 控制器基类</h1>
 * 供控制器继承，提供当前登录用户的身份获取能力。
 *
 * @author Hamm.cn
 */
@Slf4j
@Controller
public class ApiController {
    /**
     * 当前登录用户 ID 的 request 属性名
     *
     * @apiNote 拦截器或业务代码预先写入该属性后，可跳过再次解析 {@code AccessToken}
     */
    public static final String CURRENT_USER_ID = "CURRENT_USER_ID";
    @Autowired
    protected ApiConfig apiConfig;
    @Autowired
    protected HttpServletRequest request;

    /**
     * 获取当前登录用户的信息
     *
     * @return 用户 ID
     */
    protected final long getCurrentUserId() {
        Object attr = request.getAttribute(CURRENT_USER_ID);
        if (attr != null) {
            return Long.parseLong(attr.toString());
        }
        return getCurrentUserVerifiedToken().getPayloadId();
    }

    /**
     * 获取当前登录用户已验证的令牌
     *
     * @return 已验证的令牌
     * @apiNote 先取请求参数再取请求头，两处都取不到时 {@code AccessTokenUtil} 会直接抛异常
     */
    protected final AccessTokenUtil.VerifiedToken getCurrentUserVerifiedToken() {
        String accessToken = request.getParameter(apiConfig.getAuthorizeHeader());
        if (StringUtil.isEmpty(accessToken)) {
            accessToken = request.getHeader(apiConfig.getAuthorizeHeader());
        }
        return AccessTokenUtil.create().verify(
                accessToken,
                apiConfig.getAccessTokenSecret()
        );
    }
}
