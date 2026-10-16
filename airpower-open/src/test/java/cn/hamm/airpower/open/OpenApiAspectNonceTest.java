package cn.hamm.airpower.open;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.redis.RedisConfig;
import cn.hamm.airpower.redis.RedisHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>Open API 切面的 nonce 防重放单元测试</h1>
 *
 * <p>这里守的是一条<b>顺序不变量</b>：{@code checkSignature} 必须早于 {@code checkNonce}。</p>
 *
 * <p>曾经写反过一次——nonce 在签名验证之前就写进了 Redis。后果有两层：
 * 任何人都能（无需任何凭据）往共享 Redis 里灌入任意数量、任意大小的键；
 * 以及预烧合作方将要使用的 nonce，使其后续合法请求被拒。
 * 这类缺陷<b>编译不会报错、既有测试也不会失败</b>，因为顺序是一种语义约定，
 * 只有专门断言「签名失败时 Redis 零写入」的测试才能挡住它。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("Open API 切面的 nonce 防重放单元测试")
class OpenApiAspectNonceTest {

    /**
     * 应用标识
     */
    private static final String APP_KEY = "partner-app-key";

    /**
     * 应用密钥
     */
    private static final String APP_SECRET = "partner-app-secret";

    /**
     * 业务报文内容
     */
    private static final String CONTENT = "{\"amount\":1}";

    /**
     * 合法的 nonce（长度在允许区间内）
     */
    private static final String VALID_NONCE = "abcdefgh12345678";

    /**
     * 业务方法的返回值，用于确认切面确实放行到了业务层
     */
    private static final String BUSINESS_RESULT = "业务返回值";

    private ValueOperations<String, Object> valueOperations;
    private IOpenAppService openAppService;

    private static Method annotatedEndpoint() throws NoSuchMethodException {
        return OpenApiAspectNonceTest.class.getDeclaredMethod("sampleEndpoint", OpenRequest.class);
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入字段失败: " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private RedisHelper redisHelperStub() {
        RedisHelper helper = new RedisHelper();
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(template.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.setIfAbsent(Mockito.anyString(), Mockito.any(),
                Mockito.anyLong(), Mockito.any())).thenReturn(Boolean.TRUE);
        RedisConfig config = Mockito.mock(RedisConfig.class);
        Mockito.when(config.getPrefix()).thenReturn("test:");
        inject(helper, "redisTemplate", template);
        inject(helper, "redisConfig", config);
        return helper;
    }

    @BeforeEach
    @AfterEach
    void resetGlobalProxyConfig() {
        cn.hamm.airpower.api.RequestUtil.setTrustProxyHeader("");
    }

    /**
     * 供切面取到 {@link OpenApi} 注解的示例端点
     *
     * @param openRequest 请求
     */
    @OpenApi
    @SuppressWarnings("unused")
    void sampleEndpoint(OpenRequest openRequest) {
        // 测试中不会真正调用，仅用于让切面能反射拿到注解
    }

    private IOpenApp openApp() {
        return new IOpenApp() {
            @Override
            public String getAppSecret() {
                return APP_SECRET;
            }

            @Override
            public String getPublicKey() {
                return "";
            }
        };
    }

    private OpenRequest request(String nonce, String signature) {
        OpenRequest openRequest = new OpenRequest();
        openRequest.setAppKey(APP_KEY);
        openRequest.setVersion(1);
        openRequest.setTimestamp(System.currentTimeMillis());
        openRequest.setContent(CONTENT);
        openRequest.setNonce(nonce);
        openRequest.setSignature(signature);
        return openRequest;
    }

    private OpenApiAspect<?> aspect(RedisHelper redisHelper) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.1");
        openAppService = Mockito.mock(IOpenAppService.class);
        Mockito.when(openAppService.getByAppKey(APP_KEY)).thenReturn(openApp());
        OpenApiAspect<?> aspect = new OpenApiAspect<>();
        inject(aspect, "request", servletRequest);
        inject(aspect, "openAppService", openAppService);
        inject(aspect, "redisHelper", redisHelper);
        return aspect;
    }

    private ProceedingJoinPoint joinPoint(OpenRequest openRequest) throws Throwable {
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        // MethodSignature 本身就是 Signature，切面里 getSignature() 之后直接强转成它
        MethodSignature methodSignature = Mockito.mock(MethodSignature.class);
        Mockito.when(methodSignature.getName()).thenReturn("sampleEndpoint");
        Mockito.when(methodSignature.getMethod()).thenReturn(annotatedEndpoint());
        Mockito.when(pjp.getSignature()).thenReturn(methodSignature);
        Mockito.when(pjp.getArgs()).thenReturn(new Object[]{openRequest});
        Mockito.when(pjp.proceed()).thenReturn(BUSINESS_RESULT);
        return pjp;
    }

    /**
     * 断言本次调用没有向 Redis 写入过任何键
     */
    private void verifyNoRedisWrite() {
        Mockito.verify(valueOperations, Mockito.never())
                .setIfAbsent(Mockito.anyString(), Mockito.any(), Mockito.anyLong(), Mockito.any());
        Mockito.verify(valueOperations, Mockito.never())
                .set(Mockito.anyString(), Mockito.any(), Mockito.anyLong(), Mockito.any());
    }

    @Nested
    @DisplayName("1. 认证失败时不得写入 Redis（核心不变量）")
    class OrderingInvariant {

        @Test
        @DisplayName("签名校验不通过时 Redis 完全不被触碰")
        void authenticationFailureMustNotWriteRedis() throws Throwable {
            OpenApiAspect<?> aspect = aspect(redisHelperStub());
            // 形状合法但签名无法通过的请求
            OpenRequest openRequest = request(VALID_NONCE, "not-the-right-signature");

            assertThrows(ServiceException.class, () -> aspect.openApi(joinPoint(openRequest)),
                    "签名不匹配必须抛业务异常");
            verifyNoRedisWrite();
        }

        @Test
        @DisplayName("1MB 的伪造 nonce 在写 Redis 之前就被拒绝")
        void oversizedNonceRejectedBeforeRedis() throws Throwable {
            OpenApiAspect<?> aspect = aspect(redisHelperStub());
            // 旧实现会把这段原文直接拼进 Redis 的键
            OpenRequest openRequest = request("a".repeat(1024 * 1024), "not-the-right-signature");

            assertThrows(ServiceException.class, () -> aspect.openApi(joinPoint(openRequest)));
            verifyNoRedisWrite();
        }

        @Test
        @DisplayName("checkNonce 在 openApi 中位于 checkSignature 之后")
        void nonceIsConsumedAfterSignatureCheck() throws Exception {
            // 这是本组测试真正要钉住的不变量：顺序。
            // 用源码结构断言，是因为「签名失败时 Redis 零写入」只能证明失败路径，
            // 而「成功路径上 nonce 确实被消费了」需要一个可用的签名方案——
            // 而签名方案本身正在演进，不适合被这里硬编码
            java.nio.file.Path sourcePath = java.nio.file.Path.of(
                    "src/main/java/cn/hamm/airpower/open/OpenApiAspect.java");
            // 从 IDE 启动时工作目录不一定是模块根，跳过而不是伪装成通过
            org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(sourcePath),
                    "未找到切面源码（工作目录不是模块根），跳过源码结构断言");
            String source = new String(java.nio.file.Files.readAllBytes(sourcePath),
                    java.nio.charset.StandardCharsets.UTF_8);
            int signatureAt = source.indexOf("openRequest.checkSignature(openApp);");
            int nonceAt = source.indexOf("checkNonce(openRequest.getAppKey(), openRequest.getNonce());");
            assertTrue(signatureAt > 0 && nonceAt > 0, "未能在切面源码中定位到两处调用");
            assertTrue(signatureAt < nonceAt,
                    "checkNonce 必须排在 checkSignature 之后——"
                            + "顺序颠倒会让未认证调用方往共享 Redis 里灌数据");
        }
    }

    @Nested
    @DisplayName("2. Redis 键必须是定长摘要，不能直接拼 nonce 原文")
    class KeyShape {

        private String captureKey(String nonce) {
            OpenApiAspect<?> aspect = aspect(redisHelperStub());
            aspect.checkNonce(APP_KEY, nonce);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(valueOperations).setIfAbsent(captor.capture(), Mockito.any(),
                    Mockito.anyLong(), Mockito.any());
            return captor.getValue();
        }

        @Test
        @DisplayName("键的 nonce 部分是 64 位十六进制摘要，且不含 nonce 原文")
        void keyUsesHashedNonce() {
            String key = captureKey(VALID_NONCE);
            assertTrue(key.matches(".*:nonce:[0-9a-f]{64}$"),
                    "键应以 64 位十六进制摘要结尾，实际: " + key);
            assertFalse(key.contains(VALID_NONCE), "键中不得出现 nonce 原文: " + key);
        }

        @Test
        @DisplayName("不同 nonce 得到不同的键（摘要未退化）")
        void differentNoncesDifferentKeys() {
            assertNotEquals(captureKey(VALID_NONCE), captureKey(VALID_NONCE.replace('8', '9')),
                    "不同 nonce 必须映射到不同的键");
        }
    }

    @Nested
    @DisplayName("3. 重复请求的判定必须是原子的")
    class RepeatGuard {

        @Test
        @DisplayName("Redis 返回「键已存在」时抛出重复请求")
        void existingKeyRejected() {
            RedisHelper redisHelper = redisHelperStub();
            Mockito.when(valueOperations.setIfAbsent(Mockito.anyString(), Mockito.any(),
                    Mockito.anyLong(), Mockito.any())).thenReturn(Boolean.FALSE);
            OpenApiAspect<?> aspect = aspect(redisHelper);

            ServiceException exception = assertThrows(ServiceException.class,
                    () -> aspect.checkNonce(APP_KEY, VALID_NONCE),
                    "键已存在说明是重放请求，必须拒绝");
            assertEquals(cn.hamm.airpower.exception.Errors.REPEAT_REQUEST.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("判定用 SET NX 而非 get+set（后者存在并发窗口）")
        void usesAtomicSetIfAbsentNotGetThenSet() {
            OpenApiAspect<?> aspect = aspect(redisHelperStub());
            aspect.checkNonce(APP_KEY, VALID_NONCE);

            // get + set 是两条独立命令，两个并发请求可同时读到「不存在」而双双通过。
            // 一旦改回这种写法，本断言立即失败
            Mockito.verify(valueOperations, Mockito.never()).get(Mockito.anyString());
        }
    }

    @Nested
    @DisplayName("4. nonce 长度上界")
    class NonceLength {

        @ParameterizedTest(name = "长度 {0} 合法")
        @ValueSource(ints = {8, 16, 32, 36, 64})
        @DisplayName("合法长度应通过")
        void legalLengthAccepted(int length) {
            assertDoesNotThrow(() -> aspect(redisHelperStub()).checkNonceShape("n".repeat(length)),
                    "长度 " + length + " 应当放行");
        }

        @ParameterizedTest(name = "长度 {0} 非法")
        @ValueSource(ints = {1, 7, 65, 256, 4096, 1024 * 1024})
        @DisplayName("越界长度必须被拒绝")
        void illegalLengthRejected(int length) {
            assertThrows(ServiceException.class,
                    () -> aspect(redisHelperStub()).checkNonceShape("n".repeat(length)),
                    "nonce 长度必须在 8~64 之间，实际 " + length);
        }

        @Test
        @DisplayName("空 nonce 必须被拒绝")
        void emptyRejected() {
            assertThrows(ServiceException.class,
                    () -> aspect(redisHelperStub()).checkNonceShape(""),
                    "空 nonce 无法区分重放，必须拒绝");
        }
    }
}
