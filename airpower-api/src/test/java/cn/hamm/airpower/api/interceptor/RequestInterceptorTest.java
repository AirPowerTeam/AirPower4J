package cn.hamm.airpower.api.interceptor;

import cn.hamm.airpower.api.base.ApiConfig;
import cn.hamm.airpower.api.fixture.DemoController;
import cn.hamm.airpower.api.fixture.PublicDemoController;
import cn.hamm.airpower.core.access.AccessTokenUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.http.HttpConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static cn.hamm.airpower.exception.Errors.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>请求权限拦截器单元测试</h1>
 *
 * <p>盯的是鉴权链路上最容易出事的一段——<b>令牌从哪来</b>。</p>
 *
 * <p>同一个配置名既能是请求头也能是请求参数（浏览器下载类场景只能走参数）。
 * 两处都有值时若取错来源，就会出现「URL 里带一个别人的 token 就能顶掉
 * 浏览器自己带的 Authorization 头」这类问题，所以取值优先级必须钉死。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("请求权限拦截器单元测试")
class RequestInterceptorTest {

    /**
     * 令牌密钥，长度必须达到 AccessTokenUtil 的下限
     */
    private static final String SECRET = "test-access-token-secret-at-least-32-chars";

    /**
     * 请求头名
     */
    private static final String HEADER = "Authorization";

    /**
     * 用户 ID
     */
    private static final long USER_ID = 9527L;

    /**
     * 令牌有效期：1 天
     */
    private static final long EXPIRE = 24 * 60 * 60 * 1000L;

    private TestableInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /**
     * 可观测的拦截器：记录下 RBAC 校验被调用时的入参
     */
    private static class TestableInterceptor extends RequestInterceptor {

        /**
         * 被调用时的权限标识，未被调用为 {@code null}
         */
        private String checkedIdentity;

        /**
         * 被调用时的用户 ID，未被调用为 {@code null}
         */
        private Long checkedUserId;

        /**
         * 令牌校验次数
         */
        private int verifyCount;

        @Override
        public AccessTokenUtil.VerifiedToken getVerifiedToken(String accessToken) {
            verifyCount++;
            return AccessTokenUtil.create().verify(accessToken, SECRET);
        }

        @Override
        public void checkUserPermission(AccessTokenUtil.VerifiedToken verifiedToken,
                                        String permissionIdentity, HttpServletRequest request) {
            checkedIdentity = permissionIdentity;
            checkedUserId = verifiedToken.getPayloadId();
        }
    }

    @BeforeEach
    void setUp() {
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setAccessTokenSecret(SECRET);
        apiConfig.setAuthorizeHeader(HEADER);

        interceptor = new TestableInterceptor();
        inject(interceptor, apiConfig);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    /**
     * 反射注入字段
     *
     * @param interceptor 拦截器
     * @param apiConfig   配置
     */
    private void inject(RequestInterceptor interceptor, ApiConfig apiConfig) {
        try {
            Field field = RequestInterceptor.class.getDeclaredField("apiConfig");
            field.setAccessible(true);
            field.set(interceptor, apiConfig);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 apiConfig 失败", e);
        }
    }

    /**
     * 生成一个合法令牌
     *
     * @param userId 用户 ID
     * @return 令牌
     */
    private static String tokenOf(long userId) {
        return AccessTokenUtil.create()
                .setPayloadId(userId)
                .setExpireMillisecond(EXPIRE)
                .build(SECRET);
    }

    /**
     * 构造一个已过期的令牌
     *
     * @return 令牌
     * @apiNote {@code setExpireMillisecond} 只接受正数（它算的是「从现在起多久」），
     * 造不出已过期的令牌，只能直接改 {@code expireTimestamps}
     */
    private static String expiredToken() {
        AccessTokenUtil util = AccessTokenUtil.create().setPayloadId(USER_ID);
        try {
            Field holder = AccessTokenUtil.class.getDeclaredField("verifiedToken");
            holder.setAccessible(true);
            AccessTokenUtil.VerifiedToken token =
                    (AccessTokenUtil.VerifiedToken) holder.get(util);
            Field field = AccessTokenUtil.VerifiedToken.class.getDeclaredField("expireTimestamps");
            field.setAccessible(true);
            field.set(token, System.currentTimeMillis() - 1000L);
            return util.build(SECRET);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("构造过期令牌失败", e);
        }
    }

    /**
     * 构造一个指向控制器方法的处理器
     *
     * @param controller 控制器
     * @param methodName 方法名
     * @return 处理器
     */
    private HandlerMethod handlerMethod(Object controller, String methodName) {
        Method method = java.util.Arrays.stream(controller.getClass().getMethods())
                .filter(item -> item.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到方法: " + methodName));
        return new HandlerMethod(controller, method);
    }

    @Nested
    @DisplayName("1. 免登录接口")
    class PublicEndpoints {

        @Test
        @DisplayName("无需登录的接口不校验令牌")
        void publicEndpointSkipsTokenCheck() {
            // 一个没带任何令牌的请求也必须能通，否则这个免登录标记毫无意义
            assertTrue(interceptor.preHandle(request, response, handlerMethod(new PublicDemoController(), "getDetail")));
            assertEquals(0, interceptor.verifyCount);
        }

        @Test
        @DisplayName("免登录时也不调用 RBAC 校验")
        void publicEndpointSkipsRbac() {
            interceptor.preHandle(request, response, handlerMethod(new PublicDemoController(), "getDetail"));
            assertNull(interceptor.checkedIdentity);
        }

        @Test
        @DisplayName("免登录接口即便带了令牌也不做校验")
        void publicEndpointIgnoresToken() {
            // 少一次无谓的令牌验签，是这条链路上最省的一处优化
            request.addHeader(HEADER, tokenOf(USER_ID));
            assertDoesNotThrow(() -> interceptor.preHandle(request, response,
                    handlerMethod(new PublicDemoController(), "getDetail")));
            assertEquals(0, interceptor.verifyCount);
        }
    }

    @Nested
    @DisplayName("2. 令牌来源优先级")
    class TokenSource {

        @Test
        @DisplayName("请求头里的令牌生效")
        void tokenFromHeader() {
            request.addHeader(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            assertEquals(USER_ID, interceptor.checkedUserId);
        }

        @Test
        @DisplayName("请求参数里的令牌生效")
        void tokenFromParameter() {
            // 浏览器下载场景无法自定义请求头，只能走参数
            request.setParameter(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            assertEquals(USER_ID, interceptor.checkedUserId);
        }

        @Test
        @DisplayName("两处都有值时以请求参数为准")
        void parameterWinsOverHeader() {
            // 这是刻意的设计（下载场景优先），但也意味着：
            // 一个能被构造出 URL 的场景下，URL 上的 token 会顶掉请求头里的 token。
            // 因此 token 一旦出现在 URL 里就必须视为已泄漏，日志与跳转链接都要脱敏
            request.addHeader(HEADER, tokenOf(1L));
            request.setParameter(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            assertEquals(USER_ID, interceptor.checkedUserId, "请求参数应覆盖请求头");
        }

        @Test
        @DisplayName("参数为空串时回落到请求头")
        void blankParameterFallsBackToHeader() {
            // 空串不算「传了」，否则浏览器在地址栏留下 accessToken= 时会把好使的请求头顶掉
            request.addHeader(HEADER, tokenOf(USER_ID));
            request.setParameter(HEADER, "");
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            assertEquals(USER_ID, interceptor.checkedUserId);
        }

        @Test
        @DisplayName("参数为纯空白时同样回落到请求头")
        void blankParameterWithSpacesFallsBack() {
            request.addHeader(HEADER, tokenOf(USER_ID));
            request.setParameter(HEADER, "   ");
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            assertEquals(USER_ID, interceptor.checkedUserId);
        }

        @Test
        @DisplayName("参数里的令牌无效时报错而不回退到请求头")
        void invalidParameterTokenDoesNotFallBack() {
            // 参数一旦有值就优先用它，坏令牌直接拒绝——
            // 「参数坏了就悄悄用头里的」会让攻击者用一个坏的 URL 参数去探测
            request.addHeader(HEADER, tokenOf(USER_ID));
            request.setParameter(HEADER, "not-a-valid-token");
            assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
        }
    }

    @Nested
    @DisplayName("3. 缺失与非法令牌")
    class MissingOrInvalidToken {

        @Test
        @DisplayName("两处都没有令牌时抛 UNAUTHORIZED")
        void noTokenAtAllRejected() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
            assertEquals(UNAUTHORIZED.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("空串令牌被拒绝")
        void emptyTokenRejected() {
            request.addHeader(HEADER, "");
            assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
        }

        @Test
        @DisplayName("空白令牌被拒绝")
        void blankTokenRejected() {
            request.addHeader(HEADER, "   ");
            assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
        }

        @Test
        @DisplayName("伪造的令牌被拒绝")
        void forgedTokenRejected() {
            request.addHeader(HEADER, tokenOf(USER_ID) + "x");
            assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
        }

        @Test
        @DisplayName("用别的密钥签发的令牌被拒绝")
        void tokenSignedWithOtherSecretRejected() {
            String foreign = AccessTokenUtil.create()
                    .setPayloadId(USER_ID)
                    .setExpireMillisecond(EXPIRE)
                    .build("another-secret-that-is-long-enough");
            request.addHeader(HEADER, foreign);
            assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
        }

        @Test
        @DisplayName("过期的令牌被拒绝")
        void expiredTokenRejected() {
            String expired = expiredToken();
            request.addHeader(HEADER, expired);
            assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
        }
    }

    @Nested
    @DisplayName("4. 共享数据与追踪号")
    class SharedData {

        @Test
        @DisplayName("把控制器方法与实例放进请求域")
        void sharesMethodAndController() {
            // 响应拦截器读不到请求拦截器的局部变量，只能靠请求域传递。
            // 少了这一步，响应日志里的出参就不会被裁剪
            request.addHeader(HEADER, tokenOf(USER_ID));
            DemoController controller = new DemoController();
            interceptor.preHandle(request, response, handlerMethod(controller, "getDetail"));

            Object method = RequestContextHolder.getRequestAttributes()
                    .getAttribute(RequestInterceptor.REQUEST_METHOD_KEY, 0);
            Object bean = RequestContextHolder.getRequestAttributes()
                    .getAttribute(RequestInterceptor.REQUEST_CONTROLLER_KEY, 0);
            assertNotNull(method);
            assertEquals(controller, bean);
        }

        @Test
        @DisplayName("没有请求上下文时共享数据被静默跳过")
        void noRequestContextIsTolerated() {
            // 非 Web 线程（如异步任务）里调用不应 NPE
            RequestContextHolder.resetRequestAttributes();
            DemoController controller = new DemoController();
            request.addHeader(HEADER, tokenOf(USER_ID));
            assertDoesNotThrow(() -> interceptor.preHandle(request, response,
                    handlerMethod(controller, "getDetail")));
        }

        @Test
        @DisplayName("每次请求生成新的 traceId")
        void newTraceIdPerRequest() {
            request.addHeader(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            String first = MDC.get(HttpConstant.Header.TRACE_ID);
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            String second = MDC.get(HttpConstant.Header.TRACE_ID);
            assertNotNull(first);
            assertNotNull(second);
            assertTrue(!first.equals(second), "traceId 必须每请求唯一");
        }

        @Test
        @DisplayName("请求结束后 MDC 被清空")
        void mdcClearedAfterCompletion() {
            // 不清的话，线程池复用的下一个请求会带着上一条的 traceId，
            // 排查时把两条完全无关的请求串成一条链
            request.addHeader(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            MDC.put(cn.hamm.airpower.api.base.ApiController.CURRENT_USER_ID, "1");

            interceptor.afterCompletion(request, response, new DemoController(), null);

            assertNull(MDC.get(HttpConstant.Header.TRACE_ID));
            assertNull(MDC.get(cn.hamm.airpower.api.base.ApiController.CURRENT_USER_ID));
        }

        @Test
        @DisplayName("请求正常结束后同样清理 MDC")
        void mdcClearedOnNormalCompletion() {
            request.addHeader(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            MDC.put(cn.hamm.airpower.api.base.ApiController.CURRENT_USER_ID, "1");
            interceptor.afterCompletion(request, response, new DemoController(), null);
            assertNull(MDC.get(cn.hamm.airpower.api.base.ApiController.CURRENT_USER_ID));
        }
    }

    @Nested
    @DisplayName("5. RBAC 校验")
    class Rbac {

        @Test
        @DisplayName("校验时传入的是方法级权限标识")
        void methodLevelIdentityUsed() {
            request.addHeader(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            assertNotNull(interceptor.checkedIdentity);
            assertTrue(interceptor.checkedIdentity.endsWith(":getDetail"), interceptor.checkedIdentity);
        }

        @Test
        @DisplayName("不同方法得到不同的权限标识")
        void differentMethodsDifferentIdentity() {
            request.addHeader(HEADER, tokenOf(USER_ID));
            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getDetail"));
            String detail = interceptor.checkedIdentity;

            interceptor.preHandle(request, response, handlerMethod(new DemoController(), "getPage"));
            assertTrue(!detail.equals(interceptor.checkedIdentity), "标识未随方法变化");
        }

        @Test
        @DisplayName("基类默认实现直接抛「未实现权限校验」")
        void baseClassThrowsNotImplemented() {
            // 基类不提供默认 RBAC：默认放行等于所有接口裸奔，
            // 抛异常强制业务模块显式表态
            RequestInterceptor plain = new RequestInterceptor();
            inject(plain, new ApiConfig() {{
                setAccessTokenSecret(SECRET);
                setAuthorizeHeader(HEADER);
            }});
            request.addHeader(HEADER, tokenOf(USER_ID));
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> plain.preHandle(request, response, handlerMethod(new DemoController(), "getDetail")));
            assertTrue(exception.getMessage().contains("未实现权限校验"), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("6. 返回值")
    class ReturnValue {

        @Test
        @DisplayName("放行时恒返回 true")
        void alwaysReturnsTrueOnPass() {
            // 调用方约定是「抛异常即拦截」，返回值不具备拦截语义
            assertTrue(interceptor.preHandle(request, response, handlerMethod(new PublicDemoController(), "getDetail")));
        }

        @Test
        @DisplayName("共享数据键名是公开常量")
        void shareKeysArePublicConstants() {
            assertEquals("REQUEST_METHOD_KEY", RequestInterceptor.REQUEST_METHOD_KEY);
            assertEquals("REQUEST_CONTROLLER_KEY", RequestInterceptor.REQUEST_CONTROLLER_KEY);
        }
    }
}
