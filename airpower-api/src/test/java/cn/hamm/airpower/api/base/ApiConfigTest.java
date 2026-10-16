package cn.hamm.airpower.api.base;

import cn.hamm.airpower.api.RequestUtil;
import cn.hamm.airpower.api.permission.PermissionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>全局配置单元测试</h2>
 *
 * <p>{@link ApiConfig#apply()} 在 {@code @PostConstruct} 阶段把配置同步到两个静态工具类。
 * 这条推送链一旦断掉，配置就<b>看起来生效了、实际没生效</b>——
 * 配置文件改了 IP 白名单策略却毫无反应，是最难排查的一类问题。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("全局配置单元测试")
class ApiConfigTest {

    /**
     * 两个工具类都是静态状态，必须复位，否则用例之间互相污染
     */
    @AfterEach
    void resetStatics() {
        RequestUtil.setTrustProxyHeader("");
        PermissionUtil.setBasePackageName("");
        PermissionUtil.setPermissionWithPackage(true);
    }

    @Nested
    @DisplayName("1. 默认值")
    class Defaults {

        @Test
        @DisplayName("分页上下限有可用默认值")
        void pagingDefaults() {
            ApiConfig config = new ApiConfig();
            // 下限必须小于上限，否则夹取区间为空，任何分页请求都会抛异常
            assertTrue(config.getMinPageSize() < config.getMaxPageSize(),
                    "minPageSize 必须小于 maxPageSize");
            assertTrue(config.getDefaultPageSize() >= config.getMinPageSize()
                    && config.getDefaultPageSize() <= config.getMaxPageSize(),
                    "默认每页条数必须落在上下限之间，否则第一次查询就失败");
        }

        @Test
        @DisplayName("默认排序字段是 id")
        void defaultSortFieldIsId() {
            // 默认按 id 排：唯一、且与「分页以唯一列收尾」的约定一致
            assertEquals("id", new ApiConfig().getDefaultSortField());
        }

        @Test
        @DisplayName("请求体缓存上限为 256KB")
        void cacheLimitDefault() {
            assertTrue(new ApiConfig().getMaxRequestCacheLimit() > 0);
        }

        @Test
        @DisplayName("默认不信任任何代理头")
        void noProxyHeaderByDefault() {
            // 默认不信任是唯一安全的选择：代理头可被客户端伪造
            assertEquals("", new ApiConfig().getTrustProxyHeader());
        }

        @Test
        @DisplayName("默认开启请求与响应日志")
        void logsOnByDefault() {
            ApiConfig config = new ApiConfig();
            assertTrue(config.getRequestLog());
            assertTrue(config.getResponseLog());
        }

        @Test
        @DisplayName("默认只记录白名单里的头")
        void defaultLogHeaders() {
            // 全量记录请求头会把 Authorization 原样写进日志文件，
            // 日志系统一旦有读取权限，令牌就等于公开了
            List<String> headers = List.of(new ApiConfig().getRequestLogHeaders());
            assertTrue(headers.contains("Authorization"));
            assertFalse(headers.contains("Cookie"), "不应默认记录 Cookie");
        }
    }

    @Nested
    @DisplayName("2. 配置推送到工具类")
    class ApplyPushesToTools {

        @Test
        @DisplayName("可信代理头被推送到请求工具类")
        void trustProxyHeaderPushed() {
            ApiConfig config = new ApiConfig();
            config.setTrustProxyHeader("X-Forwarded-For");
            config.apply();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.9");
            assertEquals("203.0.113.9", RequestUtil.getIpAddress(request),
                    "配置未生效：来源 IP 仍取的是 TCP 对端地址");
        }

        @Test
        @DisplayName("未配置可信代理头时推送的是「不信任」")
        void emptyProxyHeaderPushed() {
            ApiConfig config = new ApiConfig();
            config.setTrustProxyHeader("X-Forwarded-For");
            config.apply();
            config.setTrustProxyHeader("");
            config.apply();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.9");
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request));
        }

        @Test
        @DisplayName("基础包名被推送到权限工具类")
        void basePackagePushed() {
            ApiConfig config = new ApiConfig();
            config.setBasePackageName("cn.hamm.airpower");
            config.apply();
            String identity = PermissionUtil.getPermissionIdentity(
                    cn.hamm.airpower.api.fixture.DemoController.class,
                    java.util.Arrays.stream(cn.hamm.airpower.api.fixture.DemoController.class.getMethods())
                            .filter(item -> item.getName().equals("getDetail"))
                            .findFirst().orElseThrow());
            assertTrue(identity.startsWith("api.fixture."),
                    "基础包名未推送，实际标识: " + identity);
            assertFalse(identity.startsWith("cn.hamm"), "基础包名前缀未被裁掉: " + identity);
        }

        @Test
        @DisplayName("权限标识是否带包名可被配置")
        void permissionWithPackagePushed() {
            ApiConfig config = new ApiConfig();
            config.setPermissionWithPackage(false);
            config.apply();
            String identity = PermissionUtil.getPermissionIdentity(
                    cn.hamm.airpower.api.fixture.DemoController.class,
                    java.util.Arrays.stream(cn.hamm.airpower.api.fixture.DemoController.class.getMethods())
                            .filter(item -> item.getName().equals("getDetail"))
                            .findFirst().orElseThrow());
            assertEquals("demo:getDetail", identity);
        }

        @Test
        @DisplayName("apply 可重复调用且结果稳定")
        void applyIsIdempotent() {
            // 某些容器会重复触发 @PostConstruct；重复推送不应改变结果
            ApiConfig config = new ApiConfig();
            config.setTrustProxyHeader("X-Real-IP");
            config.apply();
            config.apply();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Real-IP", "203.0.113.9");
            assertEquals("203.0.113.9", RequestUtil.getIpAddress(request));
        }
    }

    @Nested
    @DisplayName("3. 越界配置")
    class InvalidConfiguration {

        @Test
        @DisplayName("下限大于上限时静默收敛到上限，不会抛异常")
        void minGreaterThanMaxSilentlyCollapses() {
            // 期望：配置层就该拒绝这种写法（min > max 显然是配错了）。
            // 实际：夹取是先 Math.max(下限) 再 Math.min(上限)，两步各自独立，
            // 于是结果恒等于上限，配置错误被静默吞掉、没有任何提示。
            //
            // 影响有限（分页仍能工作，只是「最小每页条数」这条配置完全失效），
            // 但排查时很难想到是这个原因。固化现状供日后决定是否要在
            // ApiConfig 里加一条启动期校验。
            ApiConfig config = new ApiConfig();
            config.setMinPageSize(100);
            config.setMaxPageSize(10);
            cn.hamm.airpower.api.query.QueryHelper helper = new cn.hamm.airpower.api.query.QueryHelper();
            inject(helper, config);
            assertEquals(10, helper.createPageable(
                    new cn.hamm.airpower.api.query.page.Page().setPageSize(500), null).getPageSize(),
                    "min > max 时结果恒为 max");
        }

        @Test
        @DisplayName("默认每页条数超出上限时会被夹回上限")
        void defaultPageSizeClamped() {
            // 默认值 5000 + 上限 1000：夹取后仍能正常分页，
            // 说明「默认值越界」不会致命，只是实际生效的是上限
            ApiConfig config = new ApiConfig();
            config.setDefaultPageSize(5000);
            config.setMaxPageSize(1000);
            cn.hamm.airpower.api.query.QueryHelper helper = new cn.hamm.airpower.api.query.QueryHelper();
            inject(helper, config);
            assertEquals(1000, helper.createPageable(
                    new cn.hamm.airpower.api.query.page.Page().setPageSize(5000), null).getPageSize());
        }

        @Test
        @DisplayName("可信代理头为 null 时归一为不信任")
        void nullProxyHeaderNormalized() {
            // 配置文件里写成 `trust-proxy-header:` 会得到 null
            ApiConfig config = new ApiConfig();
            config.setTrustProxyHeader(null);
            config.apply();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.9");
            assertEquals("10.0.0.1", RequestUtil.getIpAddress(request));
        }


    }

    /**
     * 反射注入配置
     *
     * @param helper 查询助手
     * @param config 配置
     */
    private void inject(cn.hamm.airpower.api.query.QueryHelper helper, ApiConfig config) {
        try {
            java.lang.reflect.Field field =
                    cn.hamm.airpower.api.query.QueryHelper.class.getDeclaredField("apiConfig");
            field.setAccessible(true);
            field.set(helper, config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 apiConfig 失败", e);
        }
    }
}
