package cn.hamm.airpower.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>IP 白名单匹配单元测试</h1>
 *
 * <p>白名单规则与来源 IP 都走同一套归一化后按字节比较，
 * 因此测试重点是「写法不同但指向同一个地址」必须命中，
 * 以及「解析不了的规则」必须被跳过而不是被当成放行。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("IP 白名单匹配单元测试")
class IpMatcherTest {

    @Nested
    @DisplayName("精确匹配")
    class ExactMatchTest {

        @Test
        @DisplayName("单条规则命中")
        void singleRule() {
            assertTrue(IpMatcher.matches("10.0.0.1", "10.0.0.1"));
        }

        @Test
        @DisplayName("多行规则中任一条命中即可")
        void anyLineMatches() {
            assertTrue(IpMatcher.matches("10.0.0.1\n10.0.0.2\n10.0.0.3", "10.0.0.2"));
        }

        @Test
        @DisplayName("规则两侧空白应被忽略")
        void trimsWhitespace() {
            assertTrue(IpMatcher.matches("  10.0.0.1  \n  10.0.0.2  ", "10.0.0.2"),
                    "配置里常见的缩进与空行不应影响匹配");
        }

        @Test
        @DisplayName("逗号与分号也应作为分隔符")
        void supportsOtherSeparators() {
            assertTrue(IpMatcher.matches("10.0.0.1,10.0.0.2", "10.0.0.2"));
            assertTrue(IpMatcher.matches("10.0.0.1;10.0.0.2", "10.0.0.2"));
        }

        @Test
        @DisplayName("不在名单内应判否")
        void notInList() {
            assertFalse(IpMatcher.matches("10.0.0.1\n10.0.0.2", "10.0.0.9"));
        }

        @Test
        @DisplayName("空名单一律判否")
        void emptyWhiteList() {
            assertFalse(IpMatcher.matches("", "10.0.0.1"));
            assertFalse(IpMatcher.matches("   \n  ", "10.0.0.1"));
            assertFalse(IpMatcher.matches(null, "10.0.0.1"));
        }
    }

    @Nested
    @DisplayName("归一化后指向同一地址")
    class NormalizationTest {

        @Test
        @DisplayName("IPv6 压缩写法与完整写法视为同一条")
        void ipv6EquivalentForms() {
            assertTrue(IpMatcher.matches("0:0:0:0:0:0:0:1", "::1"),
                    "同一个 IPv6 地址的两种写法不相等，白名单会形同虚设");
            assertTrue(IpMatcher.matches("::1", "0:0:0:0:0:0:0:1"));
        }

        @Test
        @DisplayName("IPv4 映射地址应与原 IPv4 视为同一条")
        void ipv4MappedAddress() {
            assertTrue(IpMatcher.matches("10.0.0.1", "::ffff:10.0.0.1"));
            assertTrue(IpMatcher.matches("::ffff:10.0.0.1", "10.0.0.1"));
        }

        @Test
        @DisplayName("规则带端口后缀应被剥掉")
        void stripsPortFromRule() {
            assertTrue(IpMatcher.matches("10.0.0.1:8080", "10.0.0.1"));
            assertTrue(IpMatcher.matches("[2001:db8::1]:8080", "2001:db8::1"));
        }
    }

    @Nested
    @DisplayName("非法规则与非法来源")
    class IllegalInputTest {

        @Test
        @DisplayName("单条规则非法应被跳过，其余规则仍生效")
        void skipsIllegalRule() {
            assertTrue(IpMatcher.matches("10.0.0.1\nnot-an-ip", "10.0.0.1"),
                    "一条写错不应让整个 App 全部拒绝");
        }

        @Test
        @DisplayName("全部规则非法时判否而不是放行")
        void allRulesIllegal() {
            assertFalse(IpMatcher.matches("not-an-ip\n010.1.1.1", "10.1.1.1"),
                    "010 开头的八进制歧义写法必须判非法，否则会被静默当成 8.1.1.1 之外的地址放行");
        }

        @Test
        @DisplayName("来源 IP 非法时判否")
        void illegalSourceIp() {
            assertFalse(IpMatcher.matches("10.0.0.1", "unknown"));
            assertFalse(IpMatcher.matches("10.0.0.1", ""));
            assertFalse(IpMatcher.matches("10.0.0.1", null));
        }

        @Test
        @DisplayName("IPv4 规则不应命中 IPv6 来源")
        void doesNotCrossAddressFamily() {
            assertFalse(IpMatcher.matches("10.0.0.1", "2001:db8::1"),
                    "地址族不同的两个地址字节长度不同，不应判为同一条");
            assertFalse(IpMatcher.matches("::1", "1.0.0.1"));
        }
    }
}
