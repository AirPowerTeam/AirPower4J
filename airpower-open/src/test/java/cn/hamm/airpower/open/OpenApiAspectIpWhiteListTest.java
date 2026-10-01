package cn.hamm.airpower.open;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hamm.airpower.api.RequestUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;

import static cn.hamm.airpower.exception.Errors.INVALID_REQUEST_ADDRESS;
import static cn.hamm.airpower.exception.Errors.MISSING_REQUEST_ADDRESS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>Open API 切面的 IP 白名单校验单元测试</h1>
 *
 * <p>只覆盖 {@code checkIpWhiteList}：它决定了一个已通过签名校验的调用
 * 能否落到业务方法上。三条约束必须分别钉住——</p>
 * <ul>
 *     <li>白名单未配置时放行（它是可选的第二道防线）</li>
 *     <li>来源地址取不到时必须报「来源地址异常」，不能报成「不在白名单内」</li>
 *     <li>来源地址合法但不在名单里时报「不在白名单内」</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("Open API 切面的 IP 白名单校验单元测试")
class OpenApiAspectIpWhiteListTest {

    /**
     * 应用标识，仅用于日志
     */
    private static final String APP_KEY = "test-app-key";

    @BeforeEach
    @AfterEach
    void resetTrustProxyHeader() {
        RequestUtil.setTrustProxyHeader("");
    }

    /**
     * 构造只填了白名单的开放应用
     *
     * @param ipWhiteList IP 白名单
     * @return 开放应用
     */
    private IOpenApp openApp(String ipWhiteList) {
        return new IOpenApp() {
            @Override
            public String getAppSecret() {
                return "secret";
            }

            @Override
            public Integer getArithmetic() {
                return 0;
            }

            @Override
            public String getPrivateKey() {
                return "";
            }

            @Override
            public String getIpWhiteList() {
                return ipWhiteList;
            }
        };
    }

    /**
     * 构造切面并注入请求
     *
     * @param request 请求
     * @return 切面
     */
    private OpenApiAspect<?> aspectWith(MockHttpServletRequest request) {
        OpenApiAspect<?> aspect = new OpenApiAspect<>();
        try {
            Field field = OpenApiAspect.class.getDeclaredField("request");
            field.setAccessible(true);
            field.set(aspect, request);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 request 字段失败", e);
        }
        return aspect;
    }

    @Nested
    @DisplayName("白名单未配置")
    class UnconfiguredTest {

        @Test
        @DisplayName("应放行")
        void passes() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            assertDoesNotThrow(() -> aspectWith(request).checkIpWhiteList(openApp(""), APP_KEY),
                    "白名单是可选的第二道防线，未配置时不应拦下调用");
        }

        @Test
        @DisplayName("只有空白字符时同样放行")
        void blankWhiteListPasses() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            assertDoesNotThrow(() -> aspectWith(request).checkIpWhiteList(openApp("     "), APP_KEY));
        }

        @Test
        @DisplayName("每次调用都应打出未配置白名单的告警")
        void warnsOnEveryCall() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            Logger logger = (Logger) LoggerFactory.getLogger(OpenApiAspect.class);
            logger.addAppender(appender);
            try {
                OpenApiAspect<?> aspect = aspectWith(request);
                aspect.checkIpWhiteList(openApp(""), APP_KEY);
                aspect.checkIpWhiteList(openApp(""), APP_KEY);
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
            long warnCount = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().contains("未配置 IP 白名单"))
                    .filter(event -> event.getFormattedMessage().contains(APP_KEY))
                    .count();
            assertEquals(2L, warnCount,
                    "「配了 AppKey 却没有 IP 限制」是安全配置缺失，每次调用都要能看到，不能只提示一次");
        }
    }

    @Nested
    @DisplayName("白名单已配置")
    class ConfiguredTest {

        @Test
        @DisplayName("命中名单应放行")
        void hitPasses() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            assertDoesNotThrow(() -> aspectWith(request).checkIpWhiteList(openApp("10.0.0.1  ;   10.0.0.2"), APP_KEY));
        }

        @Test
        @DisplayName("未命中名单应报「不在白名单内」")
        void missThrowsInvalidRequestAddress() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.9");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> aspectWith(request).checkIpWhiteList(openApp("10.0.0.1"), APP_KEY));
            assertEquals(INVALID_REQUEST_ADDRESS.getCode(), exception.getCode(),
                    "来源地址明确却不在名单里，报「来源地址异常」会把排查带偏");
        }

        @Test
        @DisplayName("取不到来源地址应报「来源地址异常」")
        void unknownIpThrowsMissingRequestAddress() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("unknown");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> aspectWith(request).checkIpWhiteList(openApp("10.0.0.1"), APP_KEY));
            assertEquals(MISSING_REQUEST_ADDRESS.getCode(), exception.getCode(),
                    "这条错误码原先永远不触发：getIpAddress 取不到时返回占位值 unknown 而非空串");
        }

        @Test
        @DisplayName("客户端伪造的代理链前缀不得通过白名单")
        void spoofedProxyChainPrefixRejected() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = new MockHttpServletRequest();
            // 攻击者自己填了白名单里的 10.0.0.1，代理按惯例把真实对端追加到末尾
            request.addHeader("X-Forwarded-For", "10.0.0.1, 203.0.113.9");
            request.setRemoteAddr("10.0.0.1");
            assertThrows(ServiceException.class,
                    () -> aspectWith(request).checkIpWhiteList(openApp("10.0.0.1"), APP_KEY),
                    "取代理链最左侧时，攻击者填的白名单 IP 会被直接采信");
        }

        @Test
        @DisplayName("代理追加的真实对端命中名单时应放行")
        void realClientInWhiteListPasses() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
            request.setRemoteAddr("10.0.0.1");
            assertDoesNotThrow(() -> aspectWith(request).checkIpWhiteList(openApp("10.0.0.1"), APP_KEY));
        }
    }
}
