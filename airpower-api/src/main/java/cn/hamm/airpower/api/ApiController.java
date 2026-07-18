package cn.hamm.airpower.api;

import cn.hamm.airpower.api.config.ApiConfig;
import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.airpower.core.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * <h1>控制器根类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Controller
public class ApiController {
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
        String s = MDC.get(CURRENT_USER_ID);
        if (StringUtil.hasText(s)) {
            return Long.parseLong(s);
        }
        return getCurrentUserVerifiedToken().getPayloadId();
    }

    /**
     * 获取当前登录用户的TOKEN
     *
     * @return TOKEN
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
