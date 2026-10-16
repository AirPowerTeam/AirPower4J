package cn.hamm.airpower.cookie;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Cookie 模块自动装配单元测试</h2>
 *
 * <p>本类用 {@link ApplicationContextRunner} 起一个最小容器，
 * 盯的是三件「单测最容易漏、上线才发现」的事：</p>
 * <ol>
 *   <li><b>Bean 是否真的注册上了</b>——{@code Auto} 靠 {@code @ComponentScan} 扫描，
 *       扫描包名写错时既不报错也不生效，模块静默失效；</li>
 *   <li><b>属性是否真的绑上了</b>——{@code @ConfigurationProperties} 的松散绑定
 *       （{@code cookie-max-age} → {@code cookieMaxAge}）一旦对不上，
 *       配置改了没有任何反应，而启动日志一切正常；</li>
 *   <li><b>配置错误是否在启动期暴露</b>——写错类型的值应当让容器起不来，
 *       而不是拖到第一个请求 500。</li>
 * </ol>
 *
 * <p>另外校验 {@code META-INF/spring/...AutoConfiguration.imports} 里的类名，
 * 该文件一旦在改包名/改类名时漏改，自动配置就会<b>整模块静默失效</b>，
 * 且没有任何编译期或启动期报错——这是 starter 类库最常见也最难查的故障。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("Cookie 模块自动装配单元测试")
class AutoConfigurationTest {

    /**
     * 自动配置类全名，与 imports 文件里登记的必须一致
     */
    private static final String AUTO_CLASS = "cn.hamm.airpower.cookie.Auto";

    /**
     * 自动配置清单文件的类路径
     */
    private static final String IMPORTS = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    /**
     * 最小容器：只放本模块的自动配置 + 属性绑定所需的后置处理器
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Auto.class, ConfigurationPropertiesAutoConfiguration.class));

    @Nested
    @DisplayName("1. 自动装配")
    class Registration {

        @Test
        @DisplayName("配置类与助手都注册上了")
        void bothBeansRegistered() {
            runner.run(context -> {
                assertNotNull(context.getBean(CookieConfig.class), "CookieConfig 未被扫描到");
                assertNotNull(context.getBean(CookieHelper.class), "CookieHelper 未被扫描到");
            });
        }

        @Test
        @DisplayName("助手注入的就是容器里那个配置实例")
        void helperSeesContainerConfig() {
            // 字段注入拿到的必须是同一个对象：换了实例就意味着配置被复制过一份，
            // 后续「运行时改配置」不会反映到助手上
            runner.run(context -> {
                CookieConfig config = context.getBean(CookieConfig.class);
                CookieHelper helper = context.getBean(CookieHelper.class);
                assertSame(config, readInjectedConfig(helper), "助手拿到的是另一个配置实例");
            });
        }

        @Test
        @DisplayName("容器启动后各 Bean 唯一")
        void singleInstanceEach() {
            runner.run(context -> {
                assertEquals(1, context.getBeanNamesForType(CookieConfig.class).length, "CookieConfig 重复注册");
                assertEquals(1, context.getBeanNamesForType(CookieHelper.class).length, "CookieHelper 重复注册");
            });
        }
    }

    @Nested
    @DisplayName("2. 默认配置")
    class Defaults {

        @Test
        @DisplayName("不写任何配置时取到默认值")
        void defaultsApplied() {
            runner.run(context -> {
                CookieConfig config = context.getBean(CookieConfig.class);
                assertEquals("/", config.getCookiePath());
                assertEquals("authorization-key", config.getAuthCookieName());
                assertTrue(config.isCookieHttpOnly());
                assertTrue(config.isCookieSecurity());
                assertEquals(86400, config.getCookieMaxAge());
            });
        }

        @Test
        @DisplayName("不写任何配置时助手产出默认身份 Cookie")
        void defaultsProduceCookie() {
            runner.run(context -> {
                Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("v");
                assertEquals("authorization-key", cookie.getName());
                assertEquals(86400, cookie.getMaxAge());
            });
        }
    }

    @Nested
    @DisplayName("3. 属性绑定")
    class PropertyBinding {

        @Test
        @DisplayName("五个属性都能通过 kebab-case 绑定")
        void allPropertiesBind() {
            runner.withPropertyValues(
                    "airpower.cookie.cookie-path=/api/v1",
                    "airpower.cookie.auth-cookie-name=spmp-token",
                    "airpower.cookie.cookie-http-only=false",
                    "airpower.cookie.cookie-max-age=3600",
                    "airpower.cookie.cookie-security=false"
            ).run(context -> {
                CookieConfig config = context.getBean(CookieConfig.class);
                assertEquals("/api/v1", config.getCookiePath(), "cookie-path 未绑定");
                assertEquals("spmp-token", config.getAuthCookieName(), "auth-cookie-name 未绑定");
                assertFalse(config.isCookieHttpOnly(), "cookie-http-only 未绑定");
                assertEquals(3600, config.getCookieMaxAge(), "cookie-max-age 未绑定");
                assertFalse(config.isCookieSecurity(), "cookie-security 未绑定");
            });
        }

        @Test
        @DisplayName("绑定的值真正进了 Cookie")
        void boundValuesReachCookie() {
            // 只断言配置对象不够：真正要守住的是「配置 → 响应头」这条链没断
            runner.withPropertyValues(
                    "airpower.cookie.cookie-path=/api/v1",
                    "airpower.cookie.auth-cookie-name=spmp-token",
                    "airpower.cookie.cookie-max-age=60"
            ).run(context -> {
                MockHttpServletResponse response = new MockHttpServletResponse();
                response.addCookie(context.getBean(CookieHelper.class).getAuthorizeCookie("v"));
                // 去掉带运行时刻的 Expires 后比对，其余属性逐字校对
                String header = response.getHeader("Set-Cookie")
                        .replaceAll("; Expires=[^;]+", "");
                assertEquals("spmp-token=v; Path=/api/v1; Max-Age=60; Secure; HttpOnly",
                        header, "配置没有真正作用到响应头");
            });
        }

        @Test
        @DisplayName("负数与零能绑定")
        void negativeAndZeroBind() {
            // 0 = 立即删除、负数 = 会话 Cookie，都是有意义的配置
            runner.withPropertyValues("airpower.cookie.cookie-max-age=0")
                    .run(context -> assertEquals(0, context.getBean(CookieConfig.class).getCookieMaxAge()));
            runner.withPropertyValues("airpower.cookie.cookie-max-age=-1")
                    .run(context -> assertEquals(-1, context.getBean(CookieConfig.class).getCookieMaxAge()));
        }

        @Test
        @DisplayName("只写部分属性时其余保持默认值")
        void partialOverrideKeepsDefaults() {
            // 最常见的配置方式：只调一项。若松散绑定整体替换了配置对象，
            // 未写的项会变成 null / 0，这里守着
            runner.withPropertyValues("airpower.cookie.auth-cookie-name=only-name")
                    .run(context -> {
                        CookieConfig config = context.getBean(CookieConfig.class);
                        assertEquals("only-name", config.getAuthCookieName());
                        assertEquals("/", config.getCookiePath(), "未写的路径被清空了");
                        assertEquals(86400, config.getCookieMaxAge(), "未写的有效期被清空了");
                        assertTrue(config.isCookieHttpOnly(), "未写的 HttpOnly 被清空了");
                        assertTrue(config.isCookieSecurity(), "未写的 Secure 被清空了");
                    });
        }

        @Test
        @DisplayName("未登记的前缀不报错也不生效")
        void unknownPrefixIgnored() {
            // 写错前缀（airpower.cookies.* 这类）不会有任何提示，只会「配置不生效」
            runner.withPropertyValues("airpower.cookies.cookie-max-age=1")
                    .run(context -> assertEquals(86400, context.getBean(CookieConfig.class).getCookieMaxAge(),
                            "写错前缀竟生效了"));
        }
    }

    @Nested
    @DisplayName("4. 配置错误的暴露时机")
    class BindingFailure {

        @Test
        @DisplayName("有效期写成非数字时容器启动失败")
        void nonNumericMaxAgeFailsFast() {
            // 这一条是本模块唯一「启动期就能发现配置写错」的情形：
            // 绑定 int 失败 → BindException → 容器起不来，
            // 总好过带着默认值上线、以为配置生效了
            runner.withPropertyValues("airpower.cookie.cookie-max-age=one-day")
                    .run(context -> {
                        assertTrue(context.getStartupFailure() != null, "非法值竟让容器正常启动了");
                        assertTrue(rootCauseMessage(context.getStartupFailure()).contains("cookie-max-age"),
                                "报错信息未指出是哪个配置项: " + rootCauseMessage(context.getStartupFailure()));
                    });
        }

        @Test
        @DisplayName("有效期超出 int 范围时容器启动失败")
        void overflowMaxAgeFailsFast() {
            runner.withPropertyValues("airpower.cookie.cookie-max-age=99999999999")
                    .run(context -> assertTrue(context.getStartupFailure() != null, "溢出值竟让容器正常启动了"));
        }

        @Test
        @DisplayName("布尔值写成无法识别的词时容器启动失败")
        void wrongTypeFailsFast() {
            // Spring 的布尔转换只认 true/on/yes/1 与 false/off/no/0，
            // 其余一律转换失败 → 容器起不来，这类配置错误不会拖到线上
            runner.withPropertyValues("airpower.cookie.cookie-http-only=maybe")
                    .run(context -> assertTrue(context.getStartupFailure() != null, "非法布尔值竟被接受了"));
        }

        @ParameterizedTest(name = "{0} 绑定为 {1}")
        @CsvSource({"true,true", "on,true", "yes,true", "1,true",
                "false,false", "off,false", "no,false", "0,false"})
        @DisplayName("布尔值接受 yes/on/1 等多种写法")
        void lenientBooleanBinding(String literal, boolean expected) {
            // 容易踩的一处：写 `cookie-security: no` 想表达「本地调试别加密」，
            // 结果确实变成 false，但没人会想到 YAML 的 no 与 Java 的 false 是同一个意思；
            // 写错成别的词（如 maybe）又能让容器直接起不来
            runner.withPropertyValues("airpower.cookie.cookie-http-only=" + literal)
                    .run(context -> assertEquals(expected, context.getBean(CookieConfig.class).isCookieHttpOnly(),
                            "字面量 " + literal + " 的绑定结果不符"));
        }
    }

    @Nested
    @DisplayName("5. 自动配置清单文件")
    class AutoConfigurationImports {

        @Test
        @DisplayName("清单文件存在且登记了 Auto")
        void importsListsAuto() throws IOException {
            // 改类名或改包名时漏改这个文件，模块会整块静默失效且无任何报错，
            // 只有真正去调 CookieHelper 的人才会发现 bean 不存在
            String content = new String(new ClassPathResource(IMPORTS).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(Arrays.stream(content.split("\\R")).map(String::trim)
                            .anyMatch(line -> line.equals(AUTO_CLASS)),
                    "自动配置清单里没有 " + AUTO_CLASS + "，实际内容: " + content);
        }

        @Test
        @DisplayName("清单里登记的类真实存在")
        void importsTargetExists() throws IOException, ClassNotFoundException {
            // 文件里写了一个不存在的类名时，Spring Boot 会在启动时才炸；
            // 这里提前断言，能把「改包名后忘了改清单」在测试期拦住
            String content = new String(new ClassPathResource(IMPORTS).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String line : content.split("\\R")) {
                String className = line.trim();
                if (className.isEmpty()) {
                    continue;
                }
                assertNotNull(Class.forName(className), "清单里的类不存在: " + className);
            }
        }
    }

    /**
     * 反射读出助手注入的配置对象
     *
     * @param helper 助手
     * @return 配置对象
     */
    private CookieConfig readInjectedConfig(CookieHelper helper) {
        try {
            var field = CookieHelper.class.getDeclaredField("cookieConfig");
            field.setAccessible(true);
            return (CookieConfig) field.get(helper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 cookieConfig 失败", e);
        }
    }

    /**
     * 取整条异常链上的信息文本
     *
     * <p>Spring 的绑定失败会套好几层：BindException → BindHandler → ConversionFailedException
     * → NumberFormatException。配置项名字只出现在最外层，
     * 只看最里层会得到一句毫无线索的 {@code For input string: "one-day"}。</p>
     *
     * @param error 异常
     * @return 信息文本
     */
    private String rootCauseMessage(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            text.append(current.getMessage()).append('\n');
            current = current.getCause() == current ? null : current.getCause();
        }
        return text.toString();
    }
}
