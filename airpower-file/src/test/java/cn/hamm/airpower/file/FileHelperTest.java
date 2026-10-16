package cn.hamm.airpower.file;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.fixture.DuplicateKeyPlatform;
import cn.hamm.airpower.file.fixture.InheritedKeyPlatform;
import cn.hamm.airpower.file.fixture.OverriddenKeyPlatform;
import cn.hamm.airpower.file.fixture.TestFilePlatform;
import cn.hamm.airpower.file.fixture.UnannotatedPlatform;
import cn.hamm.airpower.file.platform.aliyun.AliyunOssHelper;
import cn.hamm.airpower.file.platform.local.LocalFileHelper;
import cn.hamm.airpower.file.platform.tencent.TencentCloudCosHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>文件平台注册与获取单元测试</h1>
 *
 * <p>{@link FileHelper} 干两件事：启动时把容器里的平台按标识收进 Map，
 * 业务侧按标识取用。真正容易出事的是<b>启动期</b>：标识写重、漏标注、
 * 默认平台拼错，都应该在这里失败，而不是等第一次上传时才暴露。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("文件平台注册与获取单元测试")
class FileHelperTest {
    /**
     * 被测对象
     */
    private FileHelper fileHelper;

    /**
     * 被测配置
     */
    private FileConfig fileConfig;

    /**
     * 本地平台的真实标识，供「默认平台拼错」用例参照
     */
    private static final String REAL_LOCAL_KEY =
            LocalFileHelper.class.getAnnotation(FilePlatform.class).value();

    @BeforeEach
    void setUp() {
        fileHelper = new FileHelper();
        fileConfig = new FileConfig();
        ReflectionTestUtils.setField(fileHelper, "fileConfig", fileConfig);
    }

    /**
     * 模拟容器里的平台列表并触发注册，等价于启动时的 {@code @PostConstruct}
     *
     * @param platforms 容器里的平台
     * @return 被测对象
     */
    private FileHelper register(AbstractFilePlatformFactory... platforms) {
        ReflectionTestUtils.setField(fileHelper, "allPlatforms", List.of(platforms));
        invokeRegister();
        return fileHelper;
    }

    /**
     * 调用私有的 {@code registerPlatforms()}
     *
     * @apiNote {@code @PostConstruct} 由容器调用，单元测试里只能反射触发；
     * 反射会把异常包一层，这里还原成原始异常抛出，否则断言只能拿到 InvocationTargetException
     */
    private void invokeRegister() {
        try {
            Method method = FileHelper.class.getDeclaredMethod("registerPlatforms");
            method.setAccessible(true);
            method.invoke(fileHelper);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    /**
     * 读取当前注册表里的平台数量
     *
     * @return 平台数量
     */
    @SuppressWarnings("unchecked")
    private int registeredCount() {
        Map<String, AbstractFilePlatformFactory> platforms =
                (Map<String, AbstractFilePlatformFactory>) ReflectionTestUtils.getField(fileHelper, "platforms");
        return platforms.size();
    }

    /**
     * 捕获被测类在动作期间打印的日志
     *
     * @param action 触发日志的动作
     * @return 日志文本
     */
    private static String captureLog(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(FileHelper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (all, one) -> all + one + "\n");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Nested
    @DisplayName("注册平台")
    class Registration {

        @Test
        @DisplayName("按标识注册，可取回同一个实例")
        void registersByKey() {
            TestFilePlatform test = new TestFilePlatform();
            register(test);
            assertSame(test, fileHelper.getPlatform("TEST"),
                    "注册表必须保存容器里的同一个实例，另建实例会让业务侧拿不到注入的 Bean");
            assertEquals(1, registeredCount());
        }

        @Test
        @DisplayName("多个平台可同时注册")
        void registersMultiplePlatforms() {
            TestFilePlatform test = new TestFilePlatform();
            OverriddenKeyPlatform other = new OverriddenKeyPlatform();
            register(test, other);
            assertEquals(2, registeredCount());
            assertSame(test, fileHelper.getPlatform("TEST"));
            assertSame(other, fileHelper.getPlatform("OVERRIDDEN"));
        }

        @Test
        @DisplayName("未标注 @FilePlatform 的平台被跳过")
        void skipsUnannotated() {
            UnannotatedPlatform plain = new UnannotatedPlatform();
            register(plain);
            assertEquals(0, registeredCount(), "没标注的平台不该进注册表，否则业务侧到用时才发现取不到");
            assertThrows(ServiceException.class, () -> fileHelper.getPlatform(plain.getClass().getName()));
        }

        @Test
        @DisplayName("标识重复时抛异常并点名两个类")
        void duplicateKeyRejected() {
            ServiceException exception = assertThrows(ServiceException.class, () -> register(
                    new TestFilePlatform(), new DuplicateKeyPlatform()));
            assertTrue(exception.getMessage().startsWith("文件存储平台 key 重复: TEST"), exception.getMessage());
            assertTrue(exception.getMessage().contains(TestFilePlatform.class.getName()), exception.getMessage());
            assertTrue(exception.getMessage().contains(DuplicateKeyPlatform.class.getName()),
                    "报错必须点名冲突的两个类，否则同类平台多时无从下手：" + exception.getMessage());
        }

        @Test
        @DisplayName("同一实例出现两次也算重复")
        void sameInstanceTwiceRejected() {
            TestFilePlatform test = new TestFilePlatform();
            ServiceException exception = assertThrows(ServiceException.class, () -> register(test, test));
            assertTrue(exception.getMessage().contains("key 重复"),
                    "容器里出现两个完全相同的 Bean，同样会让「落到哪个平台」变得不确定：" + exception.getMessage());
        }

        @Test
        @DisplayName("子类未标注时继承父类标识并因此撞车")
        void inheritedKeyCollides() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> register(new TestFilePlatform(), new InheritedKeyPlatform()));
            assertTrue(exception.getMessage().contains("key 重复: TEST"), exception.getMessage());
        }

        @Test
        @DisplayName("子类自己标注标识时互不冲突")
        void overriddenKeyDoesNotCollide() {
            TestFilePlatform parent = new TestFilePlatform();
            OverriddenKeyPlatform child = new OverriddenKeyPlatform();
            register(parent, child);
            assertSame(parent, fileHelper.getPlatform("TEST"));
            assertSame(child, fileHelper.getPlatform("OVERRIDDEN"),
                    "子类直接标注的注解优先于从父类继承的注解");
        }

        @Test
        @DisplayName("重复注册同一批平台不会报错")
        void reRegisterIsIdempotent() {
            register(new TestFilePlatform());
            assertDoesNotThrow(FileHelperTest.this::invokeRegister, "注册前先 clear，重复调用不应误报重复");
            assertEquals(1, registeredCount());
        }

        @Test
        @DisplayName("重新注册会替换掉上一批平台")
        void reRegisterClearsPrevious() {
            register(new TestFilePlatform());
            register(new OverriddenKeyPlatform());
            assertEquals(1, registeredCount());
            assertThrows(ServiceException.class, () -> fileHelper.getPlatform("TEST"),
                    "平台下线后不该再从注册表里取到");
        }

        @Test
        @DisplayName("容器里一个平台都没有时不报错")
        void emptyContainer() {
            register();
            assertEquals(0, registeredCount());
            assertThrows(ServiceException.class, () -> fileHelper.getPlatform("TEST"));
        }

        @Test
        @DisplayName("注册日志带上平台数量与标识")
        void logsRegisteredPlatforms() {
            String log = captureLog(() -> register(new TestFilePlatform(), new OverriddenKeyPlatform()));
            assertTrue(log.contains("已注册 2 个文件存储平台"), log);
            assertTrue(log.contains("TEST") && log.contains("OVERRIDDEN"), log);
        }
    }

    @Nested
    @DisplayName("按标识取平台")
    class GetPlatformByKey {

        @Test
        @DisplayName("未注册的标识抛「暂未支持的文件存储平台」")
        void unknownKey() {
            register(new TestFilePlatform());
            ServiceException exception = assertThrows(ServiceException.class, () -> fileHelper.getPlatform("MINIO"));
            assertTrue(exception.getMessage().contains("暂未支持的文件存储平台 MINIO"), exception.getMessage());
        }

        @Test
        @DisplayName("标识为 null 时也抛异常，提示里带出 null")
        void nullKey() {
            register(new TestFilePlatform());
            ServiceException exception = assertThrows(ServiceException.class, () -> fileHelper.getPlatform(null));
            assertTrue(exception.getMessage().contains("暂未支持的文件存储平台 null"),
                    "提示里必须带上实际传进来的标识，否则传错参时无从对照：" + exception.getMessage());
        }

        @Test
        @DisplayName("标识为 null 不会被当成「不传」")
        void nullKeyIsNotDefault() {
            fileConfig.setDefaultPlatform("TEST");
            register(new TestFilePlatform());
            assertThrows(ServiceException.class, () -> fileHelper.getPlatform((String) null),
                    "getPlatform(String) 与 getPlatform() 是两个重载，null 走前者而不是回退默认平台");
        }

        @Test
        @DisplayName("空串标识不被当成默认平台")
        void blankKeyRejected() {
            register(new TestFilePlatform());
            ServiceException exception = assertThrows(ServiceException.class, () -> fileHelper.getPlatform(""));
            assertTrue(exception.getMessage().contains("暂未支持的文件存储平台"), exception.getMessage());
        }

        @Test
        @DisplayName("标识区分大小写")
        void caseSensitive() {
            register(new TestFilePlatform());
            assertThrows(ServiceException.class, () -> fileHelper.getPlatform("test"),
                    "Map 按原样查找，小写标识取不到大写注册的平台");
        }

        @Test
        @DisplayName("三个内置平台都能按标识取回")
        void builtinPlatforms() {
            fileConfig.setDefaultPlatform(REAL_LOCAL_KEY);
            register(new LocalFileHelper(),
                    new AliyunOssHelper(),
                    new TencentCloudCosHelper());
            assertEquals(3, registeredCount());
            assertSame(fileHelper.getPlatform(), fileHelper.getPlatform(REAL_LOCAL_KEY));
        }
    }

    @Nested
    @DisplayName("取默认平台")
    class DefaultPlatform {

        @Test
        @DisplayName("按配置里的默认标识取")
        void usesConfiguredDefault() {
            fileConfig.setDefaultPlatform("TEST");
            TestFilePlatform test = new TestFilePlatform();
            register(test);
            assertSame(test, fileHelper.getPlatform());
        }

        @Test
        @DisplayName("默认标识未注册时抛「暂未支持的文件存储平台」")
        void defaultPlatformMissing() {
            fileConfig.setDefaultPlatform("MINIO");
            register(new TestFilePlatform());
            ServiceException exception = assertThrows(ServiceException.class, () -> fileHelper.getPlatform());
            assertTrue(exception.getMessage().contains("暂未支持的文件存储平台 MINIO"), exception.getMessage());
        }

        @Test
        @DisplayName("默认标识为空串时不会被静默兜底")
        void blankDefaultPlatform() {
            fileConfig.setDefaultPlatform("");
            register(new TestFilePlatform());
            ServiceException exception = assertThrows(ServiceException.class, () -> fileHelper.getPlatform());
            assertTrue(exception.getMessage().contains("暂未支持的文件存储平台"),
                    "⚠️ 已记录缺陷：默认平台配错不会在启动时校验，只有第一次 getPlatform() 才报出来："
                            + exception.getMessage());
        }

        @Test
        @DisplayName("默认标识大小写拼错时报错并带出实际配置值")
        void typoInDefaultPlatform() {
            fileConfig.setDefaultPlatform(REAL_LOCAL_KEY.toLowerCase(java.util.Locale.ROOT));
            register(new LocalFileHelper());
            ServiceException exception = assertThrows(ServiceException.class, () -> fileHelper.getPlatform());
            assertTrue(exception.getMessage().contains(REAL_LOCAL_KEY.toLowerCase(java.util.Locale.ROOT)),
                    "报错应带上用户实际配置的值，便于直接照抄修正：" + exception.getMessage());
        }
    }
}
