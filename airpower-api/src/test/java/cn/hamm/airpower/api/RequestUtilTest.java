package cn.hamm.airpower.api;

import cn.hamm.airpower.core.exception.ServiceException;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>请求工具类单元测试</h1>
 *
 * <p>本类守的是一条<b>安全不变量</b>：来源 IP 只能取自「由服务端一侧写入」的位置。</p>
 *
 * <p>{@code getIpAddress} 的取值方向一旦搞反——从代理链<b>最左</b>侧取值——
 * 攻击者只要在自己发出的请求里塞一个白名单内的 IP，IP 白名单就会被整个绕过。
 * 因此这里不只测「能解析出 IP」，还要钉住取值方向。</p>
 *
 * <p>其次是 IP 字面量的解析边界：前导零（{@code 010} 可能被解析成八进制）、
 * 端口、方括号、IPv6 zone id、IPv4 映射地址，任一处放宽都会让伪造串混进白名单匹配。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("请求工具类单元测试")
class RequestUtilTest {

    /**
     * 可信代理头是全局静态状态，必须在每个用例前后复位，
     * 否则用例之间的执行顺序会互相污染（JUnit 默认顺序不固定）
     */
    @BeforeEach
    @AfterEach
    void resetTrustProxyHeader() {
        RequestUtil.setTrustProxyHeader("");
    }

    /**
     * 构造一个指定了远端地址与请求头的请求
     *
     * @param remoteAddr 远端地址
     * @param headerName  头名
     * @param headerValue 头值
     * @return 请求
     */
    private MockHttpServletRequest request(String remoteAddr, String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (headerName != null) {
            request.addHeader(headerName, headerValue);
        }
        return request;
    }

    /**
     * 构造单条参数的有序 Map
     *
     * @param key   键
     * @param value 值
     * @return 参数
     */
    private Map<String, Object> params(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    @Nested
    @DisplayName("1. IP 字面量解析")
    class ParseAddress {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "192.168.1.1        | 192.168.1.1",
                "0.0.0.0            | 0.0.0.0",
                "255.255.255.255    | 255.255.255.255",
                "'  10.0.0.7  '     | 10.0.0.7",
                "192.168.1.1:8080   | 192.168.1.1",
                "2001:db8::1        | 2001:db8::1",
                "'[2001:db8::1]'    | 2001:db8::1",
                "'[2001:db8::1]:80' | 2001:db8::1",
                "fe80::1%eth0       | fe80::1",
                "::ffff:192.168.1.1 | 192.168.1.1",
                "::FFFF:192.168.1.1 | 192.168.1.1"
        })
        @DisplayName("合法写法应被规范化")
        void legalAddressNormalized(String raw, String expected) {
            assertEquals(expected, RequestUtil.parseAddress(raw));
        }

        @ParameterizedTest(name = "[{index}] {0} 应被拒绝")
        @ValueSource(strings = {
                "unknown",
                "192.168.1",
                "192.168.1.1.1",
                "192.168.1.",
                ".192.168.1.1",
                "256.1.1.1",
                "1.1.1.256",
                "010.1.1.1",
                "192.168.001.1",
                "1.1.1.1000",
                "[2001:db8::1",
                "2001:db8:::1",
                "2001:zzzz::1",
                "abcd",
                "1234",
                "1.1.1.1a",
                "a1.1.1.1"
        })
        @DisplayName("非法写法必须返回空串，绝不能原样透传")
        void illegalAddressRejected(String raw) {
            // 原样返回会让调用方把任意文本当 IP 去做白名单比较
            assertEquals("", RequestUtil.parseAddress(raw), "非法 IP 应返回空串: " + raw);
        }

        @Test
        @DisplayName("前导零写法必须被拒绝")
        void leadingZeroRejected() {
            // `010` 在八进制解析下是 8，不同解析器会得到不同的 IP。
            // 允许它进白名单比较，等于同一份白名单在不同组件里含义不同
            assertEquals("", RequestUtil.parseAddress("010.1.1.1"));
            assertEquals("", RequestUtil.parseAddress("192.168.01.1"));
            // 单个 0 是合法的
            assertEquals("0.0.0.0", RequestUtil.parseAddress("0.0.0.0"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("空值与空白串必须返回空串")
        void blankAddressRejected(String raw) {
            assertEquals("", RequestUtil.parseAddress(raw));
        }

        @Test
        @DisplayName("只有一对引号时不剥引号也不应抛异常")
        void unbalancedQuoteTreatedAsIllegal() {
            // 长度为 1 时 `charAt(0)` 与 `charAt(len-1)` 是同一个字符，
            // 旧写法若不做 `length > 1` 判断会切出 `substring(1, 0)` 抛异常
            assertDoesNotThrow(() -> RequestUtil.parseAddress("\""));
            assertEquals("", RequestUtil.parseAddress("\""));
        }

        @Test
        @DisplayName("被双引号包裹的 Forwarded 取值应剥壳后再剥端口")
        void quotedForwardedValue() {
            assertEquals("2001:db8::1", RequestUtil.parseAddress("\"[2001:db8::1]:8080\""));
            assertEquals("192.0.2.60", RequestUtil.parseAddress("\"192.0.2.60\""));
        }
    }

    @Nested
    @DisplayName("2. 字节数组解析")
    class ToInetBytes {

        @Test
        @DisplayName("IPv4 应解析成 4 字节且取值正确")
        void ipv4Bytes() {
            byte[] bytes = RequestUtil.toInetBytes("192.168.1.1");
            assertEquals(4, bytes.length);
            assertEquals((byte) 192, bytes[0]);
            assertEquals((byte) 168, bytes[1]);
            assertEquals((byte) 1, bytes[2]);
            assertEquals((byte) 1, bytes[3]);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "256.0.0.1", "010.0.0.1", "unknown", "1.1.1"})
        @DisplayName("非法输入必须返回 null 而不是抛异常")
        void illegalReturnsNull(String raw) {
            // 这是运行在每条请求上的热路径，不能因一个坏头把整个请求打挂
            assertNull(RequestUtil.toInetBytes(raw));
        }

        @Test
        @DisplayName("非十六进制开头的「伪 IPv6」必须返回 null")
        void nonHexLeadingCharRejected() {
            // `InetAddress.getByName` 对这类串会退化成 DNS 解析：
            // 一旦放行，一个请求头就能把应用变成 DNS 放大器，并拖慢所有请求
            assertNull(RequestUtil.toInetBytes("zzz::1"));
            assertNull(RequestUtil.toInetBytes("g::1"));
        }

        @Test
        @DisplayName("合法 IPv6 应返回 16 字节")
        void ipv6Bytes() {
            assertEquals(16, RequestUtil.toInetBytes("2001:db8::1").length);
        }
    }

    @Nested
    @DisplayName("3. 可信代理头")
    class TrustProxyHeader {

        @Test
        @DisplayName("未配置可信代理头时只用 TCP 对端地址")
        void noTrustProxyHeaderFallsBackToRemoteAddr() {
            MockHttpServletRequest request = request("10.0.0.1", "X-Forwarded-For", "203.0.113.9");
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request),
                    "默认配置下客户端自填的代理头不得被采信");
        }

        @Test
        @DisplayName("代理链必须取最右侧那一项")
        void chainTakesRightmostEntry() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = request("10.0.0.1", "X-Forwarded-For", "203.0.113.9, 10.0.0.1");
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request),
                    "最右一项由最靠近服务端的代理追加，是唯一可信的位置");
        }

        @Test
        @DisplayName("客户端伪造的链首不得通过")
        void spoofedChainHeadRejected() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = request("10.0.0.1", "X-Forwarded-For", "10.0.0.1, 203.0.113.9");
            assertEquals("203.0.113.9", RequestUtil.getIpAddress(request),
                    "取链首会让攻击者自己填的白名单 IP 被直接采信");
        }

        @ParameterizedTest(name = "[{index}] 头值 {0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "203.0.113.9                    | 203.0.113.9",
                "' 203.0.113.9 '                | 203.0.113.9",
                "198.51.100.7, 203.0.113.9      | 203.0.113.9",
                "203.0.113.9, 198.51.100.7      | 198.51.100.7",
                "203.0.113.9,unknown            | 203.0.113.9",
                "unknown, 203.0.113.9            | 203.0.113.9",
                ",,, 203.0.113.9                | 203.0.113.9",
                "203.0.113.9,,                  | 203.0.113.9",
                "unknown                        | 192.0.2.1",
                "'  ,  ,  '                     | 192.0.2.1",
                "198.51.100.7,unknown,203.0.113.9 | 203.0.113.9"
        })
        @DisplayName("从右往左取第一个合法 IP；全不合法时回退远端地址")
        void chainFindsFirstLegalFromRight(String headerValue, String expected) {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = request("192.0.2.1", "X-Forwarded-For", headerValue);
            assertEquals(expected, RequestUtil.getIpAddress(request));
        }

        @Test
        @DisplayName("头为空或未配置时回退远端地址")
        void emptyHeaderFallsBack() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request("10.0.0.1", null, null)));
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request("10.0.0.1", "X-Forwarded-For", "   ")));
        }

        @Test
        @DisplayName("未配置别的头时不得读取该头")
        void onlyConfiguredHeaderIsRead() {
            RequestUtil.setTrustProxyHeader("X-Real-IP");
            MockHttpServletRequest request = request("10.0.0.1", "X-Forwarded-For", "203.0.113.9");
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request),
                    "只信任配置里点名的那一个头");
        }

        @Test
        @DisplayName("RFC 7239 Forwarded 头按键值对解析且取最右侧")
        void forwardedHeaderParsed() {
            RequestUtil.setTrustProxyHeader("Forwarded");
            MockHttpServletRequest request = request("10.0.0.1", "Forwarded",
                    "for=192.0.2.60;proto=http;by=203.0.113.43, for=\"[2001:db8::1]:8080\"");
            assertEquals("2001:db8::1", RequestUtil.getIpAddress(request));
        }

        @Test
        @DisplayName("Forwarded 的 for 键忽略大小写")
        void forwardedForKeyCaseInsensitive() {
            RequestUtil.setTrustProxyHeader("Forwarded");
            assertEquals("192.0.2.60",
                    RequestUtil.getIpAddress(request("10.0.0.1", "Forwarded", "For=192.0.2.60")));
            assertEquals("192.0.2.60",
                    RequestUtil.getIpAddress(request("10.0.0.1", "Forwarded", "FOR=192.0.2.60")));
        }

        @Test
        @DisplayName("Forwarded 中出现多个 for 时取最右侧那个")
        void forwardedTakesRightmostFor() {
            RequestUtil.setTrustProxyHeader("Forwarded");
            assertEquals("198.51.100.7",
                    RequestUtil.getIpAddress(request("10.0.0.1", "Forwarded",
                            "for=192.0.2.60, for=198.51.100.7")));
        }

        @Test
        @DisplayName("Forwarded 里没有可用 for 时回退远端地址")
        void forwardedWithoutUsableForFallsBack() {
            RequestUtil.setTrustProxyHeader("Forwarded");
            assertEquals("10.0.0.1",
                    RequestUtil.getIpAddress(request("10.0.0.1", "Forwarded", "proto=https;by=203.0.113.43")));
            assertEquals("10.0.0.1",
                    RequestUtil.getIpAddress(request("10.0.0.1", "Forwarded", "for=unknown")));
            assertEquals("10.0.0.1",
                    RequestUtil.getIpAddress(request("10.0.0.1", "Forwarded", "for=")));
        }

        @Test
        @DisplayName("头名大小写不敏感地识别为 Forwarded")
        void forwardedHeaderNameCaseInsensitive() {
            RequestUtil.setTrustProxyHeader("forwarded");
            // 若未按 Forwarded 解析，会走逗号 IP 链分支，`for=192.0.2.60` 会被判为非法 IP
            assertEquals("192.0.2.60",
                    RequestUtil.getIpAddress(request("10.0.0.1", "forwarded", "for=192.0.2.60")));
        }

        @Test
        @DisplayName("头名配置为 null 时归一为不信任")
        void nullHeaderNormalized() {
            // 配置文件里写成 `trust-proxy-header:` 会得到 null
            RequestUtil.setTrustProxyHeader(null);
            assertEquals("10.0.0.1",
                    RequestUtil.getIpAddress(request("10.0.0.1", "X-Forwarded-For", "203.0.113.9")));
        }

        @Test
        @DisplayName("头名两端的空白应被裁掉")
        void headerNameTrimmed() {
            RequestUtil.setTrustProxyHeader("  X-Forwarded-For  ");
            assertEquals("203.0.113.9",
                    RequestUtil.getIpAddress(request("10.0.0.1", "X-Forwarded-For", "203.0.113.9")));
        }

        @Test
        @DisplayName("头名配置为纯空白时等价于不信任任何头")
        void blankHeaderNameMeansNoTrust() {
            RequestUtil.setTrustProxyHeader("     ");
            assertEquals("10.0.0.1",
                    RequestUtil.getIpAddress(request("10.0.0.1", "X-Forwarded-For", "203.0.113.9")));
        }

        @Test
        @DisplayName("远端地址也取不到时返回空串而不是抛异常")
        void noAddressAtAllReturnsEmpty() {
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = new MockHttpServletRequest();
            // MockHttpServletRequest 默认给 127.0.0.1，必须显式置空才是「取不到」
            request.setRemoteAddr(null);
            assertEquals("", RequestUtil.getIpAddress(request));
        }

        @Test
        @DisplayName("读头抛异常时返回空串（失败即关闭）")
        void headerLookupFailureYieldsEmptyIp() {
            // try 块同时包住了「读头」和「读远端地址」，所以头读取抛异常时不会走到回退分支，
            // 直接返回空串。这是<b>失败即关闭</b>：空 IP 会让 IP 白名单拒绝请求，
            // 比「悄悄退化成 TCP 对端地址」更安全。固化这一行为，
            // 将来有人把 try 块缩小以实现「读头失败就回退」时，本用例会提醒他这是个安全语义变更
            RequestUtil.setTrustProxyHeader("X-Forwarded-For");
            MockHttpServletRequest request = new MockHttpServletRequest() {
                @Override
                public String getHeader(String name) {
                    throw new IllegalStateException("容器不支持该头");
                }
            };
            request.setRemoteAddr("10.0.0.1");
            assertDoesNotThrow(() -> assertEquals("", RequestUtil.getIpAddress(request)),
                    "一个坏头不得让整条请求 500");
        }
    }

    @Nested
    @DisplayName("4. 上传请求判定")
    class UploadRequest {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "multipart/form-data",
                "multipart/form-data; boundary=----WebKitFormBoundary",
                "multipart/form-data;charset=UTF-8"
        })
        @DisplayName("multipart/form-data 开头的类型头应判定为上传")
        void multipartDetected(String contentType) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType(contentType);
            assertTrue(RequestUtil.isUploadRequest(request));
        }

        @Test
        @DisplayName("multipart/mixed 不会被判定为上传")
        void multipartMixedNotDetected() {
            // 判定用的是 `startsWith("multipart/form-data")` 而不是 `startsWith("multipart")`，
            // 所以 `multipart/mixed` 这类同样可能带大文件的类型会漏判，
            // 后果是它被包一层请求体缓存，白白占内存（功能上仍正确）。
            // 这里固化当前边界，避免有人误以为「凡 multipart 都不缓存」
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType("multipart/mixed");
            assertFalse(RequestUtil.isUploadRequest(request));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"application/json", "text/plain", "application/x-www-form-urlencoded"})
        @DisplayName("非上传类型不得被误判")
        void nonMultipartRejected(String contentType) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType(contentType);
            assertFalse(RequestUtil.isUploadRequest(request));
        }

        @Test
        @DisplayName("ServletRequest 重载与 HttpServletRequest 重载结论一致")
        void bothOverloadsAgree() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType("multipart/form-data; boundary=x");
            ServletRequest asServletRequest = request;
            assertTrue(RequestUtil.isUploadRequest(asServletRequest));
            assertTrue(RequestUtil.isUploadRequest(request));
        }
    }

    @Nested
    @DisplayName("5. QueryString 拼接")
    class QueryString {

        @Test
        @DisplayName("空 Map 得到空串")
        void emptyMapGivesEmptyString() {
            assertEquals("", RequestUtil.mapToQueryString(Map.of()));
        }

        @Test
        @DisplayName("单条参数不应带分隔符")
        void singleEntryHasNoDelimiter() {
            assertEquals("a=1", RequestUtil.mapToQueryString(params("a", 1)));
        }

        @Test
        @DisplayName("多条参数按 & 连接")
        void multipleEntriesJoined() {
            Map<String, Object> map = params("a", 1);
            map.put("b", "x");
            assertEquals("a=1&b=x", RequestUtil.mapToQueryString(map));
        }

        @Test
        @DisplayName("值为 null 会抛 NPE")
        void nullValueThrows() {
            // `item.getValue().toString()` 对 null 直接 NPE。
            // 这里固化当前行为：调用方必须先剔除 null，不能指望这里自动跳过
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("a", null);
            assertThrows(NullPointerException.class, () -> RequestUtil.mapToQueryString(map));
        }

        @Test
        @DisplayName("URL 拼接结果形如 url?k=v")
        void buildQueryUrl() {
            assertEquals("http://x/y?page=2", RequestUtil.buildQueryUrl("http://x/y", params("page", 2)));
        }

        @Test
        @DisplayName("空参数时 URL 仍带问号")
        void buildQueryUrlWithEmptyMap() {
            assertEquals("http://x/y?", RequestUtil.buildQueryUrl("http://x/y", Map.of()));
        }

        @Test
        @DisplayName("参数为 null 时抛ServiceException")
        void buildQueryUrlWithNullMap() {
            assertThrows(ServiceException.class, () -> RequestUtil.buildQueryUrl("http://x/y", null));
        }
    }
}
