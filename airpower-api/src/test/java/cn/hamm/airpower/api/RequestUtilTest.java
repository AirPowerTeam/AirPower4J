package cn.hamm.airpower.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <h1>请求工具类单元测试</h1>
 *
 * <p>只覆盖 {@link RequestUtil#getIpAddress} 的取值方向：代理链里只有最右侧那一项
 * 由离服务端最近的代理追加，客户端塞进去的前缀一律不可信，因此只能从右往左取。</p>
 *
 * <p>取最左侧会让 IP 白名单形同虚设——攻击者只要在自己的请求里写上白名单里的 IP，
 * 追加式代理（nginx 默认 {@code $proxy_add_x_forwarded_for}）不会覆盖它，
 * 白名单拿到的就是攻击者自己填的那个地址。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("请求工具类单元测试")
class RequestUtilTest {

    /**
     * 未配置可信代理头时的 TCP 对端地址
     */
    private static final String REMOTE_ADDR = "198.51.100.7";

    @BeforeEach
    @AfterEach
    void resetTrustProxyHeader() {
        RequestUtil.setTrustProxyHeader("");
    }

    /**
     * 构造带代理头与对端地址的请求
     *
     * @param headerName  代理头名
     * @param headerValue 代理头值
     * @param remoteAddr  TCP 对端地址
     * @return 请求
     */
    private MockHttpServletRequest request(String headerName, String headerValue, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (null != headerValue) {
            request.addHeader(headerName, headerValue);
        }
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Nested
    @DisplayName("X-Forwarded-For 取值方向")
    class XForwardedForTest {

        /**
         * 信任的代理头名
         */
        private static final String XFF = "X-Forwarded-For";

        @Test
        @DisplayName("多级代理链应取最右侧一项")
        void takesRightmostEntry() {
            RequestUtil.setTrustProxyHeader(XFF);
            String actual = RequestUtil.getIpAddress(request(XFF, "10.0.0.1, 198.51.100.7, 203.0.113.9", REMOTE_ADDR));
            assertEquals("203.0.113.9", actual, "最右侧由最近的代理追加，才是可信的来源地址");
        }

        @Test
        @DisplayName("可信度取决于代理是否追加，取值方向无法弥补透传")
        void trustDependsOnProxyAppending() {
            RequestUtil.setTrustProxyHeader(XFF);
            // 代理原样透传时，链上只有客户端自己填的那一项，取最右侧同样拿到伪造值
            String actual = RequestUtil.getIpAddress(request(XFF, "10.0.0.1", "203.0.113.9"));
            assertEquals("10.0.0.1", actual,
                    "取值方向只能防住追加式代理；代理必须覆盖或追加该头，否则解析出的仍是客户端填的值");
        }

        @Test
        @DisplayName("最右侧非法时应向左回退到最近一个合法地址")
        void fallsBackLeftWhenRightmostInvalid() {
            RequestUtil.setTrustProxyHeader(XFF);
            String actual = RequestUtil.getIpAddress(request(XFF, "203.0.113.9, unknown", REMOTE_ADDR));
            assertEquals("203.0.113.9", actual, "最右侧是占位值时应继续向左找，而不是直接放弃");
        }

        @Test
        @DisplayName("拒绝八进制歧义写法并向左回退")
        void rejectsOctalAmbiguity() {
            RequestUtil.setTrustProxyHeader(XFF);
            String actual = RequestUtil.getIpAddress(request(XFF, "203.0.113.9, 010.1.1.1", REMOTE_ADDR));
            assertEquals("203.0.113.9", actual, "010 开头的八进制歧义写法必须判非法，否则绕过白名单");
        }

        @Test
        @DisplayName("单一取值时原样返回")
        void singleEntry() {
            RequestUtil.setTrustProxyHeader(XFF);
            assertEquals("203.0.113.9", RequestUtil.getIpAddress(request(XFF, "203.0.113.9", REMOTE_ADDR)));
        }

        @Test
        @DisplayName("最右侧的 IPv4 映射地址应归一化")
        void normalizesMappedAddress() {
            RequestUtil.setTrustProxyHeader(XFF);
            String actual = RequestUtil.getIpAddress(request(XFF, "10.0.0.1, ::ffff:203.0.113.9", REMOTE_ADDR));
            assertEquals("203.0.113.9", actual, "::ffff: 前缀应被剥掉，否则与白名单里的写法对不上");
        }

        @Test
        @DisplayName("最右侧带端口的 IPv6 应剥掉端口")
        void stripsPortFromRightmost() {
            RequestUtil.setTrustProxyHeader(XFF);
            String actual = RequestUtil.getIpAddress(request(XFF, "10.0.0.1, [2001:db8::1]:8080", REMOTE_ADDR));
            assertEquals("2001:db8::1", actual, "方括号内的 IPv6 不应带上端口");
        }

        @Test
        @DisplayName("整条链都非法时回退到 TCP 对端地址")
        void allInvalidFallsBackToRemoteAddr() {
            RequestUtil.setTrustProxyHeader(XFF);
            String actual = RequestUtil.getIpAddress(request(XFF, "unknown, garbage", REMOTE_ADDR));
            assertEquals(REMOTE_ADDR, actual, "代理头不可用时应回退到 TCP 对端地址");
        }
    }

    @Nested
    @DisplayName("Forwarded 头取值方向")
    class ForwardedTest {

        /**
         * 信任的代理头名
         */
        private static final String FORWARDED = "Forwarded";

        @Test
        @DisplayName("多级转发链应取最右侧元素")
        void takesRightmostElement() {
            RequestUtil.setTrustProxyHeader(FORWARDED);
            String header = "for=198.51.100.7;proto=http, for=10.0.0.1;proto=https, for=203.0.113.9;proto=https";
            assertEquals("203.0.113.9", RequestUtil.getIpAddress(request(FORWARDED, header, REMOTE_ADDR)),
                    "与 X-Forwarded-For 同一取值方向，配置成 Forwarded 不能绕开");
        }

        @Test
        @DisplayName("带引号与端口的 IPv6 应正确解析")
        void parsesQuotedIpv6() {
            RequestUtil.setTrustProxyHeader(FORWARDED);
            String header = "for=10.0.0.1, for=\"[2001:db8::1]:8080\"";
            assertEquals("2001:db8::1", RequestUtil.getIpAddress(request(FORWARDED, header, REMOTE_ADDR)));
        }

        @Test
        @DisplayName("没有 for 参数的元素应跳过并继续向左")
        void skipsElementWithoutFor() {
            RequestUtil.setTrustProxyHeader(FORWARDED);
            String header = "for=203.0.113.9;proto=https, by=203.0.113.40;proto=https";
            assertEquals("203.0.113.9", RequestUtil.getIpAddress(request(FORWARDED, header, REMOTE_ADDR)),
                    "仅有 by 参数的元素不提供来源地址，应继续向左找 for");
        }
    }

    @Nested
    @DisplayName("回退与默认配置")
    class FallbackTest {

        @Test
        @DisplayName("未配置可信代理头时只认 TCP 对端地址")
        void untrustedHeaderIgnoredByDefault() {
            RequestUtil.setTrustProxyHeader("");
            String actual = RequestUtil.getIpAddress(request("X-Forwarded-For", "10.0.0.1", REMOTE_ADDR));
            assertEquals(REMOTE_ADDR, actual, "未配置可信代理头时，任意请求头都不应影响来源 IP");
        }

        @Test
        @DisplayName("代理头缺失时回退到 TCP 对端地址")
        void headerAbsent() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            assertEquals(REMOTE_ADDR, RequestUtil.getIpAddress(request("X-Forwarded-For", null, REMOTE_ADDR)));
        }

        @Test
        @DisplayName("代理头与对端地址都不可用时返回空串")
        void bothUnavailable() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            assertEquals("", RequestUtil.getIpAddress(request("X-Forwarded-For", null, "unknown")),
                    "取不到来源地址时返回空串而不是抛异常；返回非空占位值会让白名单误判成「不在名单内」");
        }
    }
}
