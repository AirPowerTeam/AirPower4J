package cn.hamm.airpower.cookie;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Cookie 助手单元测试</h2>
 *
 * <p>{@link CookieHelper} 只做一件事：把 {@link CookieConfig} 摊到一个
 * {@link jakarta.servlet.http.Cookie} 上。它自己没有一行校验，
 * 全部边界都由 Servlet 规范决定的 {@code Cookie} 类兜底——
 * 所以本类逐条钉住「哪些输入会被容器拒绝、哪些会被原样放行」。</p>
 *
 * <p>实测结论（jakarta.servlet-api 6.1.0，写死在本类里以防规范升级后无声漂移）：</p>
 * <ul>
 *   <li><b>名字</b>：null / 空 / 非 token 字符 → {@code IllegalArgumentException}；
 *       但 {@code $} 开头、以及 {@code Path} / {@code Secure} 这类属性名都<b>放行</b></li>
 *   <li><b>值</b>：完全不校验，{@code null}、{@code ;}、换行、引号、中文全部原样透传</li>
 *   <li><b>路径</b>：完全不校验，{@code \r\n} 能穿过去，只有 NUL 会被剔除</li>
 * </ul>
 *
 * <p>后两条是本模块目前最大的风险面：写坏的配置不会被本层拦下，
 * 真正是否出事取决于容器的 Set-Cookie 处理器（Tomcat 会挡一部分）。
 * 测试在此固化现状，日后若在 {@code CookieHelper} 补校验，这些断言会先红。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("Cookie 助手单元测试")
class CookieHelperTest {

    /**
     * 被测对象；每次用例前重建，避免配置串味
     */
    private CookieHelper helper;

    /**
     * 注入到被测对象上的配置
     */
    private CookieConfig config;

    @BeforeEach
    void setUp() {
        config = new CookieConfig();
        helper = new CookieHelper();
        inject(helper, config);
    }

    @Nested
    @DisplayName("1. 默认配置下的产物")
    class WithDefaultConfig {

        @Test
        @DisplayName("键值原样透传")
        void keyAndValuePassedThrough() {
            Cookie cookie = helper.getCookie("token", "abc123");
            assertEquals("token", cookie.getName());
            assertEquals("abc123", cookie.getValue());
        }

        @Test
        @DisplayName("四个属性全部按默认配置落下")
        void attributesFromDefaults() {
            Cookie cookie = helper.getCookie("token", "abc123");
            assertEquals("/", cookie.getPath(), "默认路径不是根路径");
            assertTrue(cookie.isHttpOnly(), "默认未开启 HttpOnly");
            assertTrue(cookie.getSecure(), "默认未开启 Secure");
            assertEquals(86400, cookie.getMaxAge(), "默认有效期不是一天");
        }

        @Test
        @DisplayName("身份 Cookie 用的是配置的默认名")
        void authorizeCookieName() {
            Cookie cookie = helper.getAuthorizeCookie("jwt-token");
            assertEquals("authorization-key", cookie.getName());
            assertEquals("jwt-token", cookie.getValue());
        }

        @Test
        @DisplayName("身份 Cookie 的属性与普通 Cookie 完全一致")
        void authorizeCookieSameAttributes() {
            // 两条路径必须收敛到同一份属性，否则「换个入口登录」
            // 会拿到有效期/安全属性不同的 Cookie，排查时极易误判
            Cookie auth = helper.getAuthorizeCookie("v");
            Cookie plain = helper.getCookie("authorization-key", "v");
            assertEquals(plain.getPath(), auth.getPath());
            assertEquals(plain.getMaxAge(), auth.getMaxAge());
            assertEquals(plain.getSecure(), auth.getSecure());
            assertEquals(plain.isHttpOnly(), auth.isHttpOnly());
        }
    }

    @Nested
    @DisplayName("2. 配置逐项覆盖")
    class ConfigOverrides {

        @Test
        @DisplayName("自定义路径生效")
        void customPath() {
            config.setCookiePath("/api/v1");
            assertEquals("/api/v1", helper.getCookie("k", "v").getPath());
        }

        @Test
        @DisplayName("路径配 null 时交回容器默认")
        void nullPath() {
            // setPath(null) 不是异常，是「不写 Path 属性」
            // 浏览器会退回到请求路径的目录，作用域可能小于 "/"，登录态随机丢失
            config.setCookiePath(null);
            assertNull(helper.getCookie("k", "v").getPath(),
                    "路径为 null 时应原样透传，由容器决定默认作用域");
        }

        @Test
        @DisplayName("路径配空串不会退化成根路径")
        void emptyPath() {
            // 空串是「显式写一个空 Path」，与 null 语义不同，容器行为也不同
            config.setCookiePath("");
            assertEquals("", helper.getCookie("k", "v").getPath());
        }

        @ParameterizedTest(name = "有效期配成 {0} 秒")
        @ValueSource(ints = {0, 1, 3600, Integer.MAX_VALUE})
        @DisplayName("非负有效期原样写入")
        void maxAgePassthrough(int maxAge) {
            // 0 = 让浏览器立即删除；正数 = 存活秒数。
            // 这两种语义都依赖 Cookie 对象的原值，故不能被加工
            config.setCookieMaxAge(maxAge);
            assertEquals(maxAge, helper.getCookie("k", "v").getMaxAge());
        }

        @ParameterizedTest(name = "有效期配成 {0} 秒")
        @ValueSource(ints = {-1, -2, -86400, Integer.MIN_VALUE})
        @DisplayName("任何负数有效期都被容器归一成 -1（会话 Cookie）")
        void negativeMaxAgeNormalizedToSession(int maxAge) {
            // Servlet 6.1 的 Cookie.setMaxAge 会把负值统一收敛成 -1。
            // 实测：写 -86400 与写 -1 产出的 Cookie 完全一样，
            // 「想让它 86400 秒前就过期」这类意图根本不成立，只会得到一个会话 Cookie。
            // 配置层不做任何提示，故在此固化现状
            config.setCookieMaxAge(maxAge);
            assertEquals(-1, helper.getCookie("k", "v").getMaxAge(),
                    "负数有效期未被归一成 -1，容器行为可能变了");
        }

        @Test
        @DisplayName("安全开关可双向关闭")
        void securityFlagsCanBeDisabled() {
            // 关闭 Secure 是本地 http 调试的常规操作，必须真的能关掉，
            // 否则本地登录会被浏览器静默丢弃（表现为「登录后又跳回登录页」）
            config.setCookieSecurity(false);
            config.setCookieHttpOnly(false);
            Cookie cookie = helper.getCookie("k", "v");
            assertFalse(cookie.getSecure());
            assertFalse(cookie.isHttpOnly());
        }

        @Test
        @DisplayName("身份 Cookie 名可改")
        void authCookieNameOverride() {
            config.setAuthCookieName("spmp-token");
            assertEquals("spmp-token", helper.getAuthorizeCookie("v").getName());
        }
    }

    @Nested
    @DisplayName("3. 名字非法被容器拒绝")
    class InvalidNamesRejected {

        @ParameterizedTest(name = "名字为 \"{0}\"")
        @ValueSource(strings = {" ", "a b", "a;b", "a,b", "a=b", "a:b", "a\"b", "a(b)", "a@b",
                "a<b", "a/b", "a[b]", "a{b}", "a?b", "a\\b", "a\u0001b", "中文"})
        @DisplayName("非 token 字符的名字抛 IllegalArgumentException")
        void nonTokenNameRejected(String name) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> helper.getCookie(name, "v"),
                            "非法的 Cookie 名 \"" + name + "\" 竟被接受");
            assertTrue(error.getMessage().contains("Cookie name"), "异常信息与预期不符: " + error.getMessage());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {""})
        @DisplayName("null 与空名抛 IllegalArgumentException")
        void blankNameRejected(String name) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> helper.getCookie(name, "v"),
                            "null / 空 Cookie 名竟被接受");
            assertTrue(error.getMessage().contains("must not be null or empty"),
                    "异常信息与预期不符: " + error.getMessage());
        }

        @Test
        @DisplayName("名字非法时不会返回半成品 Cookie")
        void nothingReturnedOnFailure() {
            // 名字校验发生在 CookieHelper 自己设置属性之前，
            // 抛异常即代表没有任何 Cookie 被创建，调用方的响应体不会被写脏
            assertThrows(IllegalArgumentException.class, () -> helper.getCookie("a;b", "v"));
        }
    }

    @Nested
    @DisplayName("4. Servlet 6.1 放行的怪名字")
    class OddButAcceptedNames {

        @ParameterizedTest(name = "名字为 \"{0}\"")
        @ValueSource(strings = {"$token", "$", "_token", "-token", ".token", "token|1",
                "token^1", "token~1", "token+1", "token!1", "token*1", "#token"})
        @DisplayName("token 规则允许的分隔符一律放行")
        void separatorLikeNamesAccepted(String name) {
            // RFC 2616 的 token 只排除控制符与分隔符，
            // 所以 $ _ - . | ^ ~ + ! * # 这些「看着奇怪」的键名是能正常下发的
            assertEquals(name, helper.getCookie(name, "v").getName());
        }

        @ParameterizedTest(name = "名字为属性名 \"{0}\"")
        @ValueSource(strings = {"Path", "path", "Secure", "Comment", "Max-Age", "Expires",
                "Domain", "Discard", "Version", "HttpOnly", "SameSite"})
        @DisplayName("与 Set-Cookie 属性同名的键被放行")
        void attributeLikeNamesAccepted(String name) {
            // Servlet 6.1 去掉了「属性名不能当 Cookie 名」的检查（Tomcat 老版本会拒绝）。
            // 该行为随容器版本变化，这里显式记录 6.1.0 的现状
            assertEquals(name, helper.getCookie(name, "v").getName());
        }
    }

    @Nested
    @DisplayName("5. 值完全不被校验")
    class ValueNotValidated {

        @ParameterizedTest(name = "值为 \"{0}\"")
        @ValueSource(strings = {"", "a;b", "a b", "a,b", "a=b", "a\"b", "a\\b", "中文值",
                "line1\nline2", "carriage\rreturn", "tab\there", "{\"a\":1}", "<script>alert(1)</script>"})
        @DisplayName("特殊字符的值原样透传")
        void valuePassedThroughVerbatim(String value) {
            // 本层不做任何 URL 编码：编码是容器在写 Set-Cookie 时的职责。
            // 应用侧若自己先编码一次，容器会再编一次导致双重编码、令牌取不回来
            Cookie cookie = helper.getCookie("k", value);
            assertEquals(value, cookie.getValue(), "值被改写了，说明本层开始编码了");
        }

        @Test
        @DisplayName("null 值被接受")
        void nullValueAccepted() {
            // value=null 不抛异常，产出的是「只有名字没有值」的 Cookie。
            // 语义是「删除该 Cookie」还是「写入空值」，同样取决于容器
            Cookie cookie = helper.getCookie("k", null);
            assertEquals("k", cookie.getName());
            assertNull(cookie.getValue());
        }

        @Test
        @DisplayName("身份 Cookie 传 null 值也不会炸")
        void authorizeCookieNullValue() {
            // 登录逻辑里「登出」常直接传 null 过来，这里必须不炸
            Cookie cookie = helper.getAuthorizeCookie(null);
            assertEquals("authorization-key", cookie.getName());
            assertNull(cookie.getValue());
        }
    }

    @Nested
    @DisplayName("6. 路径不被校验")
    class PathNotValidated {

        @ParameterizedTest(name = "路径为 \"{0}\"")
        @ValueSource(strings = {"relative", "/a;b", "/a=b", "/a b", "/a\\b", "//a", "/a/../b",
                "/a/./b", "/中文/路径", "/"})
        @DisplayName("任意字符串路径都被原样接受")
        void anyPathAccepted(String path) {
            // 没有「必须以 / 开头」「不能含分号」这类检查：
            // 配置写错不会在这里暴露，最终以什么形态发出去由容器决定
            config.setCookiePath(path);
            assertEquals(path, helper.getCookie("k", "v").getPath());
        }

        @Test
        @DisplayName("路径里的 CR/LF 能穿过本层")
        void crlfSurvivesThisLayer() {
            // 若容器不校验，这条路径就能往 Set-Cookie 响应头里塞入额外内容。
            // 本模块不做防护是现状（Tomcat 的 Rfc6265 处理器会拦），
            // 这里钉住它，好让日后补校验时有据可依
            config.setCookiePath("/a\r\nSet-Cookie: injected=1");
            assertEquals("/a\r\nSet-Cookie: injected=1", helper.getCookie("k", "v").getPath());
        }

        @Test
        @DisplayName("路径里的 NUL 也原样保留")
        void nulPreservedAsIs() {
            // 实测：连 NUL 都不会被剔除，路径一个字节都不改。
            // 这是本层「零校验」的极端证据——配置文件里能塞进来的，Cookie 全盘照收
            config.setCookiePath("/a\u0000b");
            assertEquals("/a\u0000b", helper.getCookie("k", "v").getPath(),
                    "路径被改写了，说明容器开始做净化了");
        }
    }

    @Nested
    @DisplayName("7. 身份 Cookie 名的配置错误延迟到调用时才炸")
    class AuthCookieNameMisconfigured {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "token key", "token;x=y"})
        @DisplayName("非法名字在调用时才抛异常")
        void misconfiguredNameFailsAtCallTime(String name) {
            // 配置层没有任何校验，Spring 启动阶段不会报错，
            // 错误要等到第一次发身份 Cookie（通常是登录）才暴露成 500
            config.setAuthCookieName(name);
            assertThrows(IllegalArgumentException.class, () -> helper.getAuthorizeCookie("v"));
        }
    }

    @Nested
    @DisplayName("8. 对象隔离与契约")
    class IsolationAndContract {

        @Test
        @DisplayName("每次调用返回新实例")
        void newInstanceEachCall() {
            // 若改成复用单例，一个请求改属性就会污染其他请求的 Cookie
            Cookie first = helper.getCookie("k", "v");
            Cookie second = helper.getCookie("k", "v");
            assertNotSame(first, second);
        }

        @Test
        @DisplayName("改动返回值不会影响下一次调用")
        void mutationDoesNotLeak() {
            Cookie first = helper.getCookie("k", "v");
            first.setPath("/hacked");
            first.setMaxAge(0);
            assertEquals("/", helper.getCookie("k", "v").getPath(), "上一次改的属性泄漏了");
            assertEquals(86400, helper.getCookie("k", "v").getMaxAge(), "上一次改的属性泄漏了");
        }

        @Test
        @DisplayName("身份 Cookie 每次也是新实例")
        void authorizeCookieIsIndependent() {
            assertNotSame(helper.getAuthorizeCookie("v"), helper.getAuthorizeCookie("v"));
        }

        @Test
        @DisplayName("改配置后立即反映到下一次调用")
        void configReadOnEveryCall() {
            // 配置是读时取值而不是构造时快照，配置刷新后无需重建 Bean
            assertEquals("/", helper.getCookie("k", "v").getPath());
            config.setCookiePath("/late");
            assertEquals("/late", helper.getCookie("k", "v").getPath());
        }

        @Test
        @DisplayName("两个方法都是 final")
        void methodsAreFinal() throws NoSuchMethodException {
            // final 是刻意的：子类无法覆写后偷偷换掉属性策略，
            // 登录鉴权这类对安全属性敏感的入口不该有子类插手的机会
            assertTrue(Modifier.isFinal(
                            CookieHelper.class.getMethod("getCookie", String.class, String.class).getModifiers()),
                    "getCookie 不再是 final");
            assertTrue(Modifier.isFinal(
                            CookieHelper.class.getMethod("getAuthorizeCookie", String.class).getModifiers()),
                    "getAuthorizeCookie 不再是 final");
        }

        @Test
        @DisplayName("未注入配置时使用会空指针")
        void failsWithoutWiring() {
            // 字段注入的代价：new 出来的 CookieHelper 必然不可用，
            // 只能交给 Spring 容器创建。这里把后果写死，免得日后误改注入方式
            CookieHelper bare = new CookieHelper();
            assertThrows(NullPointerException.class, () -> bare.getCookie("k", "v"));
            assertThrows(NullPointerException.class, () -> bare.getAuthorizeCookie("v"));
        }
    }

    @Nested
    @DisplayName("9. 落到响应头的样子")
    class RenderedHeader {

        @Test
        @DisplayName("默认身份 Cookie 渲染为带全部安全属性的 Set-Cookie")
        void defaultHeader() {
            // 断言最终报文而不是内部字段：这是浏览器唯一看到的东西，
            // 中间任何一层（配置、助手、容器渲染）改写了属性都躲不过
            String header = render(helper.getAuthorizeCookie("jwt-token"));

            assertEquals("authorization-key=jwt-token; Path=/; Max-Age=86400; Secure; HttpOnly", header,
                    "实际响应头与预期不符，浏览器据此决定是否保存");
        }

        @Test
        @DisplayName("关闭安全开关后属性确实消失")
        void disabledFlagsDisappear() {
            config.setCookieSecurity(false);
            config.setCookieHttpOnly(false);
            config.setCookieMaxAge(0);
            String header = render(helper.getCookie("k", "v"));

            // Max-Age=0 是删除 Cookie 的标准写法
            assertEquals("k=v; Path=/; Max-Age=0", header, "关闭后的属性仍出现在响应头里");
        }

        @Test
        @DisplayName("Max-Age 与 Expires 同时下发且指向同一时刻")
        void maxAgeAndExpiresAgree() {
            // 实测容器会同时写 Max-Age 与 Expires：前者是 RFC 6265 的权威值，
            // 后者是给老客户端的兜底。两者都由 maxAge 推出，不能互相矛盾
            String header = renderRaw(helper.getCookie("k", "v"));
            assertTrue(header.contains("Max-Age=86400"), "缺少 Max-Age: " + header);

            Instant expires = parseExpires(header);
            long delta = Math.abs(Duration.between(Instant.now().plusSeconds(86400), expires).toSeconds());
            assertTrue(delta < 60, "Expires 与 Max-Age 推出的到期时间不一致: " + header);
        }

        @Test
        @DisplayName("负数有效期既没有 Max-Age 也没有 Expires")
        void negativeMaxAgeRendersAsSessionCookie() {
            // 会话 Cookie 没有固定到期日，只能靠「两个属性都不写」表达：
            // 实测 Cookie 对象上的 -1 在渲染时被整个丢掉，不会写成 Max-Age=-1。
            // 排查「浏览器为什么不发这个 Cookie」时，看到的就是这条报文
            config.setCookieMaxAge(-1);
            String header = render(helper.getCookie("k", "v"));

            assertEquals("k=v; Path=/; Secure; HttpOnly", header, "会话 Cookie 的报文与预期不符");
            assertFalse(header.contains("Max-Age"), "会话 Cookie 不应带 Max-Age: " + header);
        }

        @Test
        @DisplayName("路径为 null 时响应头里没有 Path")
        void nullPathOmitsPathAttribute() {
            config.setCookiePath(null);
            String header = render(helper.getCookie("k", "v"));

            assertFalse(header.contains("Path"), "Path 属性不该出现: " + header);
        }

        /**
         * 渲染响应头并去掉 Expires 属性
         *
         * <p>Expires 里带的是渲染那一刻的时刻，逐字符比对必然不稳定；
         * 它本身由 maxAge 推出，单独用一条用例校验即可。</p>
         *
         * @param cookie 待渲染的 Cookie
         * @return 去掉 Expires 后的响应头
         */
        private String render(Cookie cookie) {
            return renderRaw(cookie).replaceAll("; Expires=[^;]+", "");
        }

        /**
         * 渲染完整的响应头
         *
         * @param cookie 待渲染的 Cookie
         * @return 响应头文本
         */
        private String renderRaw(Cookie cookie) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.addCookie(cookie);
            return response.getHeader("Set-Cookie");
        }

        /**
         * 从响应头里取出 Expires 属性代表的时刻
         *
         * @param header 响应头
         * @return 到期时刻
         */
        private Instant parseExpires(String header) {
            Matcher matcher = Pattern.compile("Expires=([^;]+)").matcher(header);
            assertTrue(matcher.find(), "响应头里没有 Expires: " + header);
            return ZonedDateTime.parse(matcher.group(1).trim(),
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        }
    }

    @Nested
    @DisplayName("10. 键值组合回归")
    class KeyValueMatrix {

        @ParameterizedTest(name = "[{index}] {0}={1}")
        @CsvSource(delimiter = '|', value = {
                "k|v",
                "authorization-key|jwt",
                "session|abc.def.ghi",
                "a|b=c",
                "token|eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig",
        })
        @DisplayName("常见键值组合原样产出")
        void commonPairs(String key, String value) {
            Cookie cookie = helper.getCookie(key, value);
            assertEquals(key, cookie.getName());
            assertEquals(value, cookie.getValue());
        }

        @Test
        @DisplayName("超长值不会在构造期被截断")
        void longValueNotTruncated() {
            // 超过 4KB 的令牌会在容器层被拒或截断，但本层必须原样传递，
            // 否则问题会被掩盖成「值莫名其妙变短了」
            String value = "x".repeat(4096);
            assertEquals(value, helper.getCookie("k", value).getValue());
        }

        @Test
        @DisplayName("同名不同值各自独立")
        void sameNameDifferentValues() {
            // 一次响应里可能同时下发多个同名 Cookie（不同 Path），
            // 本层不做任何合并或覆盖
            Cookie first = helper.getCookie("k", "1");
            Cookie second = helper.getCookie("k", "2");
            assertEquals("1", first.getValue());
            assertEquals("2", second.getValue());
        }
    }

    /**
     * 反射注入配置
     *
     * <p>{@link CookieHelper} 用的是字段注入，没有别的入口，单元测试只能反射写入。</p>
     *
     * @param helper 被测助手
     * @param config 配置
     */
    private void inject(CookieHelper helper, CookieConfig config) {
        try {
            Field field = CookieHelper.class.getDeclaredField("cookieConfig");
            field.setAccessible(true);
            field.set(helper, config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 cookieConfig 失败", e);
        }
    }
}
