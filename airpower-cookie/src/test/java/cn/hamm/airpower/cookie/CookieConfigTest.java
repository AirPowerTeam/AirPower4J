package cn.hamm.airpower.cookie;

import cn.hamm.airpower.core.datetime.DateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Cookie 配置单元测试</h2>
 *
 * <p>本类守的是 {@link CookieConfig} 的<b>默认值</b>与<b>无校验事实</b>。</p>
 *
 * <p>为什么值得单独测：{@code CookieConfig} 是整个模块唯一的输入面，
 * 它的默认值直接决定浏览器拿到什么属性的 Cookie。默认值改动<b>编译不会报错</b>、
 * {@link CookieHelper} 也照样工作，只是线上悄悄变成「会话 Cookie 失效」
 * 「本地 http 登录后状态丢失」这类极难从代码上看出来的故障。</p>
 *
 * <p>另一半价值在于固化「这里没有任何校验」：五个字段全是裸 setter，
 * 写错的配置不会在启动期被拒绝，只会在第一次发 Cookie 时才暴露成 500。
 * 哪天真加上 {@code @Validated}，本类会先红，提示同步更新文档与断言。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("Cookie 配置单元测试")
class CookieConfigTest {

    @Nested
    @DisplayName("1. 默认值")
    class Defaults {

        @Test
        @DisplayName("路径默认值取自 CookieHelper.DEFAULT_PATH")
        void pathDefaultIsSharedConstant() {
            // 这里断言的是「引用了同一个常量」而不是字面量 "/"：
            // 两处默认值必须同步，改常量时配置默认值要跟着变，不能各写各的
            assertEquals(CookieHelper.DEFAULT_PATH, new CookieConfig().getCookiePath(),
                    "配置默认值与 CookieHelper.DEFAULT_PATH 不一致");
            assertEquals("/", CookieHelper.DEFAULT_PATH, "DEFAULT_PATH 被改成了非根路径，会让所有 Cookie 挂到子路径上");
        }

        @Test
        @DisplayName("有效期默认值是一天")
        void maxAgeDefaultIsOneDay() {
            // 同样是与常量耦合：DateTimeUtil.SECOND_PER_DAY 改了这里跟着改
            assertEquals(DateTimeUtil.SECOND_PER_DAY, new CookieConfig().getCookieMaxAge(),
                    "有效期默认值与 DateTimeUtil.SECOND_PER_DAY 不一致");
            assertEquals(86400, new CookieConfig().getCookieMaxAge(), "一天的秒数不是 86400");
        }

        @Test
        @DisplayName("身份串 Cookie 名默认为 authorization-key")
        void authCookieNameDefault() {
            assertEquals("authorization-key", new CookieConfig().getAuthCookieName());
        }

        @Test
        @DisplayName("默认同时开启 HttpOnly 与 Secure")
        void securityDefaultsOn() {
            CookieConfig config = new CookieConfig();
            // HttpOnly 关掉 = 令牌能被 JS 读走，一次 XSS 就完成账号接管
            assertTrue(config.isCookieHttpOnly(), "默认必须开启 HttpOnly");
            // Secure 关掉 = Cookie 会随 http 明文发出
            assertTrue(config.isCookieSecurity(), "默认必须开启 Secure");
        }
    }

    @Nested
    @DisplayName("2. setter 往返")
    class SetterRoundTrip {

        @Test
        @DisplayName("五个字段的取值都能原样读回")
        void roundTrip() {
            CookieConfig config = new CookieConfig();
            config.setCookiePath("/api");
            config.setAuthCookieName("token");
            config.setCookieHttpOnly(false);
            config.setCookieMaxAge(120);
            config.setCookieSecurity(false);

            assertEquals("/api", config.getCookiePath());
            assertEquals("token", config.getAuthCookieName());
            assertFalse(config.isCookieHttpOnly());
            assertEquals(120, config.getCookieMaxAge());
            assertFalse(config.isCookieSecurity());
        }

        @Test
        @DisplayName("可以显式写回默认值")
        void restoreDefaults() {
            // 配置热更新（改完再改回去）是常见运维动作，读写必须对称
            CookieConfig config = new CookieConfig();
            CookieConfig defaults = new CookieConfig();

            config.setCookiePath("/x");
            config.setCookieMaxAge(1);
            config.setCookiePath(defaults.getCookiePath());
            config.setCookieMaxAge(defaults.getCookieMaxAge());

            assertEquals(defaults.getCookiePath(), config.getCookiePath());
            assertEquals(defaults.getCookieMaxAge(), config.getCookieMaxAge());
        }

        @ParameterizedTest(name = "有效期配成 {0} 秒")
        @ValueSource(ints = {0, -1, -86400, Integer.MIN_VALUE, Integer.MAX_VALUE})
        @DisplayName("有效期不做任何范围校验")
        void maxAgeAcceptsAnything(int maxAge) {
            // 0 = 立即删除，负数 = 会话 Cookie，都是 RFC 里的合法语义；
            // 但「一天」这种明显写错的值同样照单全收，没有任何提示
            CookieConfig config = new CookieConfig();
            config.setCookieMaxAge(maxAge);
            assertEquals(maxAge, config.getCookieMaxAge(), "无效值被改写了");
        }

        @Test
        @DisplayName("路径与 Cookie 名可被置为 null")
        void nullableFieldsAccepted() {
            // 配置文件里写 `cookie-path:`（YAML 空值）会得到 null，
            // 之后 setPath(null) 的语义是「交回容器默认」，与「不写」并不等价
            CookieConfig config = new CookieConfig();
            config.setCookiePath(null);
            config.setAuthCookieName(null);
            assertNull(config.getCookiePath());
            assertNull(config.getAuthCookieName());
        }
    }

    @Nested
    @DisplayName("3. 对象语义")
    class ObjectSemantics {

        @Test
        @DisplayName("同值配置相等，异值配置不等")
        void equalsAndHashCode() {
            // @Data 生成 equals/hashCode：容器按值比较配置时依赖它们
            CookieConfig one = new CookieConfig();
            CookieConfig two = new CookieConfig();
            assertEquals(one, two);
            assertEquals(one.hashCode(), two.hashCode());

            two.setAuthCookieName("other");
            assertNotEquals(one, two);
        }

        @Test
        @DisplayName("每个实例独立，互不影响")
        void instancesAreIndependent() {
            // 字段初始化用的是实例字段赋值，若误写成 static 就会串味
            CookieConfig one = new CookieConfig();
            CookieConfig two = new CookieConfig();
            assertNotSame(one, two);

            one.setCookieMaxAge(1);
            assertEquals(DateTimeUtil.SECOND_PER_DAY, two.getCookieMaxAge(), "改一个实例却影响了另一个");
        }

        @Test
        @DisplayName("toString 能看出改了哪些字段")
        void toStringShowsFields() {
            // 配置的 toString 会被日志打出来，改字段后要能一眼看出是哪一项
            CookieConfig config = new CookieConfig();
            config.setCookieMaxAge(60);
            assertTrue(config.toString().contains("60"),
                    "toString 未包含改动后的值: " + config);
            assertFalse(new CookieConfig().toString().contains("null"),
                    "默认值里出现了 null 字段: " + new CookieConfig());
        }
    }
}
