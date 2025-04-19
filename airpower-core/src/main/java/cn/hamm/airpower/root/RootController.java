package cn.hamm.airpower.root;

import cn.hamm.airpower.annotation.Permission;
import cn.hamm.airpower.config.ServiceConfig;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.util.AccessTokenUtil;
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
public class RootController {
    @Autowired
    protected ServiceConfig serviceConfig;

    @Autowired
    protected HttpServletRequest request;

    /**
     * 获取当前登录用户的信息
     *
     * @return 用户 ID
     */
    protected final long getCurrentUserId() {
        try {
            String accessToken = request.getHeader(serviceConfig.getAuthorizeHeader());
            AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(
                    accessToken,
                    serviceConfig.getAccessTokenSecret()
            );
            return verifiedToken.getPayloadId();
        } catch (Exception exception) {
            throw new ServiceException(UNAUTHORIZED);
        }
    }
}
