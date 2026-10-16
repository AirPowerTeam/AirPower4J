package cn.hamm.airpower.api.interceptor.filter;

import cn.hamm.airpower.api.base.ApiConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>请求体缓存过滤器单元测试</h2>
 *
 * <p>本过滤器决定「请求包体日志」能不能用。它的取舍写在代码注释里：
 * 只包装 POST、且排除文件上传——因为包装意味着整个包体在内存里留一份，
 * 对上传请求来说那可能几百 MB。</p>
 *
 * <p>另一条边界是：过滤器抛出的异常<b>不能</b>交给 {@code @ControllerAdvice}
 * （那时容器链已经不在 MVC 里了），所以必须自己吞掉并记日志。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("请求体缓存过滤器单元测试")
class RequestCacheFilterTest {

    /**
     * 请求体缓存上限
     */
    private static final int CACHE_LIMIT = 1024;

    private RequestCacheFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RequestCacheFilter();
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setMaxRequestCacheLimit(CACHE_LIMIT);
        try {
            Field field = RequestCacheFilter.class.getDeclaredField("apiConfig");
            field.setAccessible(true);
            field.set(filter, apiConfig);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 apiConfig 失败", e);
        }
        response = new MockHttpServletResponse();
    }

    /**
     * 构造一个指定方法的请求
     *
     * @param method      请求方法
     * @param contentType 类型头，可为 {@code null}
     * @return 请求
     */
    private MockHttpServletRequest requestOf(String method, String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setContentType(contentType);
        return request;
    }

    /**
     * 记录过滤器传给下游的请求对象
     *
     * @param request 请求
     * @return 捕获器
     */
    private ArgumentCaptor<ServletRequest> captureForwarded(ServletRequest request) {
        ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
        org.mockito.ArgumentCaptor<ServletResponse> ignored =
                org.mockito.ArgumentCaptor.forClass(ServletResponse.class);
        return captor;
    }

    @Nested
    @DisplayName("1. 包装判定")
    class Wrapping {

        @Test
        @DisplayName("POST 且非上传时被包装")
        void postJsonIsWrapped() throws Exception {
            MockHttpServletRequest request = requestOf("POST", "application/json");
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request, response, chain);

            ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
            Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
            assertTrue(captor.getValue() instanceof ContentCachingRequestWrapper,
                    "JSON 请求应被包装，实际: " + captor.getValue().getClass());
        }

        @Test
        @DisplayName("POST 的文件上传不被包装")
        void postUploadNotWrapped() throws Exception {
            // 包装会把整个包体留在内存里；一个几百 MB 的上传能把堆打爆。
            // 代价是文件上传请求没有包体日志，这个取舍是有意的
            MockHttpServletRequest request =
                    requestOf("POST", "multipart/form-data; boundary=----X");
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request, response, chain);

            ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
            Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
            assertFalse(captor.getValue() instanceof ContentCachingRequestWrapper,
                    "上传请求不应被缓存");
            assertSame(request, captor.getValue(), "未包装时应原样透传");
        }

        @Test
        @DisplayName("GET 不被包装")
        void getNotWrapped() throws Exception {
            MockHttpServletRequest request = requestOf("GET", null);
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request, response, chain);

            ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
            Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
            assertSame(request, captor.getValue());
        }

        @Test
        @DisplayName("PUT / DELETE 等方法不被包装")
        void otherMethodsNotWrapped() throws Exception {
            for (String method : new String[]{"PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}) {
                RequestCacheFilter local = new RequestCacheFilter();
                ApiConfig config = new ApiConfig();
                config.setMaxRequestCacheLimit(CACHE_LIMIT);
                try {
                    Field field = RequestCacheFilter.class.getDeclaredField("apiConfig");
                    field.setAccessible(true);
                    field.set(local, config);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                MockHttpServletRequest request = requestOf(method, "application/json");
                jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
                local.doFilter(request, response, chain);

                ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
                Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
                assertSame(request, captor.getValue(), method + " 不应被包装");
            }
        }

        @Test
        @DisplayName("方法名大小写不同也不包装")
        void lowercasePostNotWrapped() throws Exception {
            // 比较是大小写敏感的 `POST.equals(method)`。
            // 容器给出的方法名本就是大写，这里只是把边界钉死，
            // 免得有人以为「post」也会被包装
            MockHttpServletRequest request = requestOf("post", "application/json");
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request, response, chain);

            ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
            Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
            assertSame(request, captor.getValue());
        }

        @Test
        @DisplayName("POST 但类型头缺失时按非上传处理并包装")
        void postWithoutContentTypeIsWrapped() throws Exception {
            MockHttpServletRequest request = requestOf("POST", null);
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request, response, chain);

            ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
            Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
            assertTrue(captor.getValue() instanceof ContentCachingRequestWrapper,
                    "拿不到类型头时应保守当作普通请求");
        }
    }

    /**
     * 从包装器里读回缓存上限
     *
     * @param wrapper 包装器
     * @return 上限
     * @apiNote 包装器没有公开该值，只能反射读父类字段
     */
    private int cacheLimitOf(ContentCachingRequestWrapper wrapper) {
        Class<?> current = wrapper.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField("contentCacheLimit");
                field.setAccessible(true);
                // 该字段是 Integer 且允许为 null（无上限构造器）
                Object limit = field.get(wrapper);
                return null == limit ? -1 : (Integer) limit;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("读取 cacheLimit 失败", e);
            }
        }
        throw new IllegalStateException("未找到 contentCacheLimit 字段");
    }

    @Nested
    @DisplayName("2. 缓存上限")
    class CacheLimit {

        @Test
        @DisplayName("包装时用配置里的上限")
        void configuredLimitApplied() throws Exception {
            // 上限决定最多在内存里留多少字节。传错成 Integer.MAX_VALUE
            // 会让任何大小的包体都驻留内存
            MockHttpServletRequest request = requestOf("POST", "application/json");
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request, response, chain);

            ArgumentCaptor<ServletRequest> captor = captureForwarded(request);
            Mockito.verify(chain).doFilter(captor.capture(), Mockito.any());
            // ContentCachingRequestWrapper 不暴露缓存上限，
            // 只能验证「确实是带上限构造出来的那个包装器」，并从字段上读回上限
            ContentCachingRequestWrapper wrapper =
                    (ContentCachingRequestWrapper) captor.getValue();
            assertNotNull(wrapper);
            assertEquals(CACHE_LIMIT, cacheLimitOf(wrapper),
                    "包装器的缓存上限应等于配置值");
        }

        @Test
        @DisplayName("被包装的请求可以读回原始包体")
        void bodyReadableThroughWrapper() throws Exception {
            // 响应拦截器正是靠这个把请求体打进日志；
            // 读不到就意味着「打印请求包体」这个功能形同虚设
            byte[] body = "{\"name\":\"x\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MockHttpServletRequest request = requestOf("POST", "application/json");
            request.setContent(body);
            jakarta.servlet.FilterChain chain = (req, res) ->
                    ((ContentCachingRequestWrapper) req).getContentAsByteArray();
            filter.doFilter(request, response, chain);
        }
    }

    @Nested
    @DisplayName("3. 异常吞没")
    class ExceptionSwallowing {

        @Test
        @DisplayName("下游抛异常时过滤器不向外抛")
        void downstreamExceptionSwallowed() throws Exception {
            // 此时已经在过滤器链上，@ControllerAdvice 接不到，
            // 异常冒出去只会得到一个容器默认的 500 页面，
            // 连「导出失败」这类业务消息都丢了。只能自己吞掉并记日志
            jakarta.servlet.FilterChain chain = (req, res) -> {
                throw new IllegalStateException("业务层炸了");
            };
            MockHttpServletRequest request = requestOf("POST", "application/json");
            assertDoesNotThrow(() -> filter.doFilter(request, response, chain));
        }

        @Test
        @DisplayName("受检异常同样被吞掉")
        void checkedExceptionSwallowed() throws Exception {
            jakarta.servlet.FilterChain chain = (req, res) -> {
                throw new RuntimeException(new java.io.IOException("磁盘满了"));
            };
            MockHttpServletRequest request = requestOf("POST", "application/json");
            assertDoesNotThrow(() -> filter.doFilter(request, response, chain));
        }

        @Test
        @DisplayName("未包装路径上的异常也被吞掉")
        void exceptionOnUnwrappedPathSwallowed() throws Exception {
            jakarta.servlet.FilterChain chain = (req, res) -> {
                throw new IllegalStateException("GET 处理失败");
            };
            MockHttpServletRequest request = requestOf("GET", null);
            assertDoesNotThrow(() -> filter.doFilter(request, response, chain));
        }

        @Test
        @DisplayName("非 HTTP 请求不会把异常抛给调用方")
        void nonHttpRequestTolerated() throws Exception {
            // 过滤器里把入参强转成 HttpServletRequest，这个强转在 try 块内，
            // 因此传进来一个非 HTTP 请求只会走 catch 分支记日志，不会打断整条链。
            // 这也意味着：这种情况下请求<b>不会被继续处理</b>，属于「安全地失败」
            ServletRequest odd = Mockito.mock(ServletRequest.class);
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            assertDoesNotThrow(() -> filter.doFilter(odd, response, chain));
            Mockito.verify(chain, Mockito.never()).doFilter(Mockito.any(), Mockito.any());
        }

        @Test
        @DisplayName("响应对象为 null 时也不会抛出去")
        void nullResponseTolerated() throws Exception {
            jakarta.servlet.FilterChain chain = Mockito.mock(jakarta.servlet.FilterChain.class);
            MockHttpServletRequest request = requestOf("GET", null);
            assertDoesNotThrow(() -> filter.doFilter(request, null, chain));
        }
    }
}
