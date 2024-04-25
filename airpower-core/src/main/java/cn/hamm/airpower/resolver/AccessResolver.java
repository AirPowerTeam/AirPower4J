package cn.hamm.airpower.resolver;

import cn.hamm.airpower.config.AirConfig;
import cn.hamm.airpower.util.AirUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Objects;


/**
 * <h1>用户授权处理类</h1>
 *
 * @author Hamm.cn
 */
@Component
public class AccessResolver implements HandlerMethodArgumentResolver {
    @Override
    public final boolean supportsParameter(@NotNull MethodParameter parameter) {
        Class<?> clazz = parameter.getParameterType();
        return clazz == Long.class;
    }

    /**
     * <h2>ACCESS_TOKEN换用户ID</h2>
     */
    @Override
    public final @NotNull @Unmodifiable Object resolveArgument(
            @NotNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NotNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String accessToken = Objects.nonNull(request) ?
                request.getHeader(AirConfig.getGlobalConfig().getAuthorizeHeader()) :
                null;
        return AirUtil.getSecurityUtil().getUserIdFromAccessToken(accessToken);
    }
}
