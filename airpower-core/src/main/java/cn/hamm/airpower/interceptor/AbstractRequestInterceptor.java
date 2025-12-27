package cn.hamm.airpower.interceptor;

import cn.hamm.airpower.access.Access;
import cn.hamm.airpower.access.AccessConfig;
import cn.hamm.airpower.access.AccessTokenUtil;
import cn.hamm.airpower.access.PermissionUtil;
import cn.hamm.airpower.request.HttpConstant;
import cn.hamm.airpower.util.TraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Objects;

import static cn.hamm.airpower.exception.ServiceError.UNAUTHORIZED;

/**
 * <h1>全局权限拦截器抽象类</h1>
 *
 * @author Hamm.cn
 * @see #checkUserPermission(AccessTokenUtil.VerifiedToken, String, HttpServletRequest)
 * @see #interceptRequest(HttpServletRequest, HttpServletResponse, Class, Method)
 */
@Component
@Slf4j
public abstract class AbstractRequestInterceptor implements HandlerInterceptor {
    /**
     * 缓存的 {@code REQUEST_METHOD_KEY}
     */
    protected static final String REQUEST_METHOD_KEY = "REQUEST_METHOD_KEY";

    /**
     * 缓存的 {@code REQUEST_METHOD_KEY}
     */
    protected static final String REQUEST_CONTROLLER_KEY = "REQUEST_CONTROLLER_KEY";

    @Autowired
    protected AccessConfig accessConfig;

    /**
     * 拦截器
     *
     * @param request  请求
     * @param response 响应
     * @param object   请求对象
     * @return 拦截结果
     */
    @Override
    public final boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object object
    ) {
        String traceId = request.getHeader(HttpConstant.Header.TRACE_ID);
        TraceUtil.setTraceId(traceId);
        log.info("请求地址 {}", request.getRequestURI());
        HandlerMethod handlerMethod = (HandlerMethod) object;
        //取出控制器和方法
        Class<?> clazz = handlerMethod.getBeanType();
        Method method = handlerMethod.getMethod();
        setShareData(REQUEST_METHOD_KEY, method);
        setShareData(REQUEST_CONTROLLER_KEY, handlerMethod.getBean());
        handleRequest(request, response, clazz, method);
        return true;
    }

    /**
     * <h2>设置共享数据</h2>
     *
     * @param key   KEY
     * @param value VALUE
     */
    protected final void setShareData(String key, Object value) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (Objects.nonNull(requestAttributes)) {
            requestAttributes.setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
        }
    }

    /**
     * 请求拦截器
     *
     * @param request  请求
     * @param response 响应
     * @param clazz    控制器
     * @param method   方法
     */
    private void handleRequest(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            Class<?> clazz, Method method
    ) {
        interceptRequest(request, response, clazz, method);
        Access access = PermissionUtil.getWhatNeedAccess(clazz, method);
        if (!access.isLogin()) {
            // 不需要登录 直接返回有权限
            return;
        }
        //需要登录
        String accessToken = request.getHeader(accessConfig.getAuthorizeHeader());

        // 优先使用 Get 参数传入的身份
        String accessTokenFromParam = request.getParameter(accessConfig.getAuthorizeHeader());
        if (StringUtils.hasText(accessTokenFromParam)) {
            accessToken = accessTokenFromParam;
        }
        UNAUTHORIZED.whenEmpty(accessToken);
        AccessTokenUtil.VerifiedToken verifiedToken = getVerifiedToken(accessToken);

        //需要RBAC
        if (access.isAuthorize()) {
            //验证用户是否有接口的访问权限
            checkUserPermission(verifiedToken, PermissionUtil.getPermissionIdentity(clazz, method), request);
        }
    }

    /**
     * 获取验证后的令牌
     *
     * @param accessToken 访问令牌
     * @return 验证后的令牌
     * @apiNote 如需前置验证令牌，可重写此方法
     */
    public AccessTokenUtil.VerifiedToken getVerifiedToken(String accessToken) {
        return AccessTokenUtil.create().verify(accessToken, accessConfig.getAccessTokenSecret());
    }

    /**
     * 验证指定的用户是否有指定权限标识的权限
     *
     * @param verifiedToken      合法令牌
     * @param permissionIdentity 权限标识
     * @param request            请求对象
     * @apiNote 抛出异常则为拦截
     */
    public void checkUserPermission(
            AccessTokenUtil.@NotNull VerifiedToken verifiedToken, String permissionIdentity, HttpServletRequest request
    ) {
    }

    /**
     * 拦截请求
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param clazz    控制器类
     * @param method   执行方法
     * @apiNote 抛出异常则为拦截
     */
    @SuppressWarnings({"EmptyMethod", "unused"})
    protected void interceptRequest(
            HttpServletRequest request, HttpServletResponse response, Class<?> clazz, Method method
    ) {
    }
}
