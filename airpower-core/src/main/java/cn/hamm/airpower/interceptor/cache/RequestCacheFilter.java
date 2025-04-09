package cn.hamm.airpower.interceptor.cache;

import cn.hamm.airpower.config.Constant;
import cn.hamm.airpower.util.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * <h1>缓存请求的过滤器</h1>
 *
 * @author Hamm.cn
 */
@WebFilter
@Slf4j
public class RequestCacheFilter implements Filter {
    /**
     * <h3>过滤器</h3>
     *
     * @param servletRequest  请求
     * @param servletResponse 响应
     * @param filterChain     过滤器链
     */
    @Override
    public final void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain
    ) {
        try {
            MDC.put(Constant.REQUEST_ID, String.valueOf(UUID.randomUUID()));
            HttpServletRequest request = ((HttpServletRequest) servletRequest);
            // 如果是上传 不做任何缓存
            if (!requestCacheRequired(request)) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            RequestBodyCacheWrapper wrapper = new RequestBodyCacheWrapper(request);
            filterChain.doFilter(wrapper, servletResponse);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
        } finally {
            MDC.remove(Constant.REQUEST_ID);
        }
    }

    /**
     * <h3>判断是否需要缓存</h3>
     *
     * @param request 请求
     * @return 是否需要缓存
     */
    private boolean requestCacheRequired(@NotNull HttpServletRequest request) {
        // GET请求不缓存
        if (HttpMethod.GET.name().equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        // 空ContentType或者非JSON不缓存
        String contentType = request.getContentType();
        if (Objects.isNull(contentType) || !contentType.contains(APPLICATION_JSON_VALUE)) {
            return false;
        }
        // 上传请求不缓存
        return !RequestUtil.isUploadRequest(request);
    }
}
