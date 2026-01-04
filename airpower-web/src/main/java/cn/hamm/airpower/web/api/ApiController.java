package cn.hamm.airpower.web.api;

import cn.hamm.airpower.web.access.AccessConfig;
import cn.hamm.airpower.web.access.AccessTokenUtil;
import cn.hamm.airpower.web.access.Permission;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * <h1>控制器根类</h1>
 *
 * @author Hamm.cn
 */
@Permission(login = false)
@Slf4j
public class ApiController {
    @Autowired
    protected AccessConfig accessConfig;

    @Autowired
    protected HttpServletRequest request;

    /**
     * 获取当前登录用户的信息
     *
     * @return 用户 ID
     */
    protected final long getCurrentUserId() {
        String accessToken = request.getHeader(accessConfig.getAuthorizeHeader());
        AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(
                accessToken,
                accessConfig.getAccessTokenSecret()
        );
        return verifiedToken.getPayloadId();
    }
}
