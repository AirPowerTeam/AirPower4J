package cn.hamm.airpower.api;

import cn.hamm.airpower.core.AccessTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
        String accessToken = request.getHeader(apiConfig.getAuthorizeHeader());
        AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(
                accessToken,
                apiConfig.getAccessTokenSecret()
        );
        return verifiedToken.getPayloadId();
    }
}
