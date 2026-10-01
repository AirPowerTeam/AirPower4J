package cn.hamm.airpower.curd.interceptor.filter;

import cn.hamm.airpower.api.RequestUtil;
import cn.hamm.airpower.core.enums.HttpMethod;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * <h1>缓存请求体的过滤器</h1>
 *
 * @author Hamm.cn
 * @apiNote 请求体被 {@code ContentCachingRequestWrapper} 缓存，供响应拦截器打印请求日志。
 * 未包装的请求读到的请求体是空的，因此「打印请求包体」的日志功能依赖本过滤器
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
            // 仅包装 POST，文件上传流很大且不需要打日志，包装只会白白占用内存
            HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
            String method = httpRequest.getMethod();
            if (HttpMethod.POST.name().equals(method) && !RequestUtil.isUploadRequest(servletRequest)) {
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                filterChain.doFilter(wrappedRequest, servletResponse);
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception e) {
            // 过滤器抛出的异常无法交给 @ControllerAdvice 统一处理，只能记日志
            log.error(e.getMessage(), e);
        }
    }
}
