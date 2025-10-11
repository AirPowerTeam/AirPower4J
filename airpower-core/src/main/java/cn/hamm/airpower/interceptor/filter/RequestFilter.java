package cn.hamm.airpower.interceptor.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import lombok.extern.slf4j.Slf4j;

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
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
        }
    }
}
