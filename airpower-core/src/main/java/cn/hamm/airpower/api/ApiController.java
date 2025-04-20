package cn.hamm.airpower.api;

import cn.hamm.airpower.access.AccessConfig;
import cn.hamm.airpower.access.AccessTokenUtil;
import cn.hamm.airpower.access.Permission;
import cn.hamm.airpower.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static cn.hamm.airpower.exception.ServiceError.UNAUTHORIZED;

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
        try {
            String accessToken = request.getHeader(accessConfig.getAuthorizeHeader());
            AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(
                    accessToken,
                    accessConfig.getAccessTokenSecret()
            );
            return verifiedToken.getPayloadId();
        } catch (Exception exception) {
            throw new ServiceException(UNAUTHORIZED);
        }
    }
}
