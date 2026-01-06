package cn.hamm.airpower.web.interceptor.filter;

import cn.hamm.airpower.core.enums.HttpMethod;
import cn.hamm.airpower.web.util.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * <h1>缓存请求的过滤器</h1>
 *
 * @author Hamm.cn
 */
@WebFilter
@Slf4j
public class RequestFilter implements Filter {
    /**
     * 过滤器
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
            // 仅对POST、PUT等有body的请求做缓存，可根据需求调整
            HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
            String method = httpRequest.getMethod();
            if (HttpMethod.POST.name().equals(method) && !RequestUtil.isUploadRequest(servletRequest)) {
                // 包装请求，缓存请求体
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                filterChain.doFilter(wrappedRequest, servletResponse);
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
        }
    }
}
