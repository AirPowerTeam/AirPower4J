package cn.hamm.airpower.file;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.fixture.OverriddenKeyPlatform;
import cn.hamm.airpower.file.fixture.TestFilePlatform;
import cn.hamm.airpower.file.fixture.UnannotatedPlatform;
import cn.hamm.airpower.file.fixture.UnreliableInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <h1>文件存储平台基类单元测试：命名、哈希、扩展名校验、上传目录</h1>
 *
 * <p>基类把三个平台共有的逻辑收在一处，测试的抓手也只有三个：
 * 「算出来的名字对不对」「算出来的哈希对不对」「路径拼得对不对」。
 * 其中空值、纯空白、大小写、目录穿越这几类输入，是线上真正会遇到的。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("文件存储平台基类单元测试")
class AbstractFilePlatformFactoryTest {
    /**
     * 空内容的 MD5
     */
    private static final String MD5_EMPTY = "d41d8cd98f00b204e9800998ecf8427e";

    /**
     * 内容 "abc" 的 MD5
     */
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    /**
     * 内容 "中文内容" 的 MD5
     */
    private static final String MD5_CHINESE = "bcfd6d08ffd55b1bccb3d3d5abc1520c";

    /**
     * 被测平台（已实现 save，便于断言落盘入参）
     */
    private TestFilePlatform platform;

    /**
     * 被测平台（只实现 save，用于验证基类默认行为）
     */
    private UnannotatedPlatform plainPlatform;

    /**
     * 注入到被测对象的配置
     */
    private FileConfig fileConfig;

    @BeforeEach
    void setUp() {
        fileConfig = new FileConfig();
        platform = withConfig(new TestFilePlatform(), fileConfig);
        plainPlatform = withConfig(new UnannotatedPlatform(), fileConfig);
    }

    /**
     * 构造一个注入好 {@link FileConfig} 的平台
     *
     * @param target    被测平台
     * @param fileConfig 配置
     * @param <T>       平台类型
     * @return 被测平台
     */
    private static <T extends AbstractFilePlatformFactory> T withConfig(T target, FileConfig fileConfig) {
        ReflectionTestUtils.setField(target, "fileConfig", fileConfig);
        return target;
    }

    /**
     * 构造一个上传文件
     *
     * @param fileName 文件名
     * @param content  内容
     * @return 上传文件
     */
    private static MultipartFile newFile(String fileName, String content) {
        return new MockMultipartFile("file", fileName, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("获取上传文件名")
    class GetMultipartFileName {

        @Test
        @DisplayName("取原始文件名")
        void returnsOriginalFilename() {
            assertEquals("avatar.png", platform.getFileName(newFile("avatar.png", "abc")));
        }

        @Test
        @DisplayName("文件为 null 时抛「文件不能为空」")
        void nullFileThrows() {
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> platform.getFileName((MultipartFile) null));
            assertEquals("文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("原始文件名为 null 时报参数非法")
        void nullFilenameThrows() throws IOException {
            // MockMultipartFile 会把 null 原始文件名转成空串，只能用 mock 造出真正的 null
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn(null);
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.getFileName(file));
            assertEquals(PARAM_INVALID.getCode(), exception.getCode());
            assertEquals("文件名不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("原始文件名为空串时不会报「文件名不能为空」")
        void blankFilenameIsNotNullFilename() {
            MultipartFile file = new MockMultipartFile("file", "", null, new byte[]{1});
            assertEquals("", platform.getFileName(file),
                    "⚠️ 已记录行为：空串与 null 走了不同的分支，只有 null 会报「文件名不能为空」");
        }

        @Test
        @DisplayName("无扩展名的文件名原样返回")
        void nameWithoutExtension() {
            assertEquals("README", platform.getFileName(newFile("README", "abc")),
                    "文件名不做任何加工，扩展名是在 upload 阶段才从它身上切出来的");
        }

        @Test
        @DisplayName("原始文件名里的路径不做清洗")
        void traversalKeptAsIs() {
            assertEquals("../../etc/passwd", platform.getFileName(newFile("../../etc/passwd", "abc")),
                    "⚠️ 已记录行为：getFileName 不做清洗，安全性依赖 upload() 用 MD5 重命名；"
                            + "绕过 upload() 直接调 save() 传原始文件名的用法不受任何保护");
        }
    }

    @Nested
    @DisplayName("获取本地文件名")
    class GetLocalFileName {

        @Test
        @DisplayName("取文件名部分")
        void returnsName() throws IOException {
            Path file = Files.createTempFile("airpower", ".txt");
            try {
                assertEquals(file.getFileName().toString(), platform.getFileName(file.toFile()));
            } finally {
                Files.deleteIfExists(file);
            }
        }

        @Test
        @DisplayName("文件为 null 时抛「文件不能为空」")
        void nullFileThrows() {
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.getFileName((File) null));
            assertEquals("文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("根路径的文件名为空串时原样返回")
        void rootFileHasBlankName() {
            assertEquals("", platform.getFileName(new File(File.separator)),
                    "⚠️ 已记录行为：File#getName() 对根路径返回空串，whenNull 拦不住，"
                            + "本地平台据此上传会生成以点结尾的文件名");
        }
    }

    @Nested
    @DisplayName("计算文件 MD5")
    class Hash {

        @Test
        @DisplayName("空文件的 MD5")
        void emptyFile() {
            assertEquals(MD5_EMPTY, platform.getFileHash(newFile("empty.txt", "")));
        }

        @Test
        @DisplayName("普通内容的 MD5")
        void knownContent() {
            assertEquals(MD5_ABC, platform.getFileHash(newFile("a.txt", "abc")));
        }

        @Test
        @DisplayName("中文内容按 UTF-8 计算")
        void chineseContent() {
            assertEquals(MD5_CHINESE, platform.getFileHash(newFile("a.txt", "中文内容")),
                    "MD5 对字节序列计算，中文必须先按 UTF-8 编码");
        }

        @Test
        @DisplayName("文件为 null 时抛「文件不能为空」")
        void nullFileThrows() {
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> platform.getFileHash((MultipartFile) null));
            assertEquals("文件不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("读取失败时抛出带原因的异常")
        void brokenStreamThrows() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getInputStream()).thenThrow(new IOException("模拟读取失败"));
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.getFileHash(file));
            assertTrue(exception.getMessage().startsWith("计算文件哈希失败"), exception.getMessage());
            assertTrue(exception.getMessage().contains("模拟读取失败"),
                    "异常原因必须透传，否则线上无从判断是网络问题还是文件损坏");
        }

        @Test
        @DisplayName("文件名不影响哈希")
        void filenameIrrelevant() {
            assertEquals(platform.getFileHash(newFile("a.txt", "abc")),
                    platform.getFileHash(newFile("b.png", "abc")),
                    "文件名取 MD5 后做去重：文件名不同但内容相同的文件会落到同一路径并相互覆盖");
        }

        @Test
        @DisplayName("大文件也能算出 32 位小写十六进制")
        void largeFile() {
            byte[] content = new byte[2 * 1024 * 1024];
            Arrays.fill(content, (byte) 'a');
            String hash = platform.getFileHash(new MockMultipartFile("file", "big.bin", null, content));
            assertEquals(32, hash.length());
            assertEquals(hash.toLowerCase(Locale.ROOT), hash, "哈希必须是小写十六进制");
        }

        @Test
        @DisplayName("输入流为 null 时抛「文件输入流不能为空」")
        void nullStreamThrows() {
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.getInputStreamHash(null));
            assertEquals("文件输入流不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("哈希读到流末尾，不依赖 available()")
        void hashIgnoresAvailable() {
            InputStream inputStream = new UnreliableInputStream("abc".getBytes(StandardCharsets.UTF_8), 0, -1);
            assertEquals(MD5_ABC, platform.getInputStreamHash(inputStream),
                    "available() 失真时哈希仍必须正确——但 upload 拿同一个值做大小校验，那里就会失真");
        }

        @Test
        @DisplayName("读取中断时抛出带原因的异常")
        void brokenInputStreamThrows() {
            InputStream inputStream = new UnreliableInputStream("abc".getBytes(StandardCharsets.UTF_8), 3, 2);
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.getInputStreamHash(inputStream));
            assertTrue(exception.getMessage().startsWith("计算文件哈希失败"), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("校验文件扩展名")
    class ValidateExtension {

        @Test
        @DisplayName("命中白名单放行")
        void allowed() {
            assertDoesNotThrow(() -> platform.validateUploadFileExtension("png", "jpg", "png"));
        }

        @Test
        @DisplayName("未命中白名单报「文件类型不允许上传」")
        void rejected() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.validateUploadFileExtension("exe", "jpg", "png"));
            assertEquals(PARAM_INVALID.getCode(), exception.getCode());
            assertEquals("文件类型不允许上传", exception.getMessage());
        }

        @Test
        @DisplayName("扩展名为 null 时报「文件类型不能为空」")
        void nullExtension() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.validateUploadFileExtension(null, "jpg"));
            assertEquals("文件类型不能为空", exception.getMessage());
        }

        @Test
        @DisplayName("扩展名为空串或纯空白时报「文件类型不能为空」")
        void blankExtension() {
            assertEquals("文件类型不能为空", assertThrows(ServiceException.class,
                    () -> platform.validateUploadFileExtension("", "jpg")).getMessage());
            assertEquals("文件类型不能为空", assertThrows(ServiceException.class,
                    () -> platform.validateUploadFileExtension("   ", "jpg")).getMessage());
        }

        @Test
        @DisplayName("白名单区分大小写")
        void caseSensitive() {
            assertThrows(ServiceException.class, () -> platform.validateUploadFileExtension("PNG", "jpg", "png"),
                    "⚠️ 已记录行为：白名单不忽略大小写，业务侧写 png 却上传 .PNG 会被拦下");
            assertDoesNotThrow(() -> platform.validateUploadFileExtension("PNG", "JPG", "PNG"),
                    "调用方也可以自己把白名单写成大写来匹配");
        }

        @Test
        @DisplayName("带点的扩展名不匹配")
        void extensionWithDot() {
            assertThrows(ServiceException.class, () -> platform.validateUploadFileExtension(".png", "png"),
                    "白名单里要写 png 而不是 .png，两种写法混用时极易踩坑");
        }

        @Test
        @DisplayName("白名单为空时任何扩展名都不放行")
        void emptyWhiteList() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.validateUploadFileExtension("png"));
            assertEquals("文件类型不允许上传", exception.getMessage(),
                    "⚠️ 已记录行为：漏传白名单不会提示「白名单为空」，而是让所有文件都传不上去");
        }

        @Test
        @DisplayName("白名单数组为 null 时抛 NPE")
        void nullArrayThrows() {
            assertThrows(NullPointerException.class,
                    () -> platform.validateUploadFileExtension("png", (String[]) null),
                    "⚠️ 已记录缺陷：Arrays.stream(null) 直接 NPE，应先判空再校验");
        }

        @Test
        @DisplayName("白名单里含 null 元素不会 NPE")
        void nullElementInArray() {
            assertThrows(ServiceException.class,
                    () -> platform.validateUploadFileExtension("png", null, "jpg"),
                    "含 null 元素的白名单会正常判定为「不允许上传」");
        }
    }

    @Nested
    @DisplayName("拼接上传目录")
    class UploadDirectory {

        @Test
        @DisplayName("根目录 + 类别，且以分隔符结尾")
        void joinsRootAndCategory() {
            assertEquals("upload/avatar/", platform.getUploadDirectory("avatar"),
                    "返回值必须能直接与文件名拼接，否则调用方自己拼会得到 upload/avatara.txt");
        }

        @Test
        @DisplayName("追加当天日期目录，日期自成一级")
        void appendsTodayDirectory() {
            String directory = platform.getUploadDirectory("avatar", true);
            assertTrue(directory.matches("upload/avatar/\\d{8}/"),
                    "日期必须自成一级目录（upload/avatar/20261017/），不能粘在类别名后面，实际为 " + directory);
        }

        @Test
        @DisplayName("不追加日期时目录里不含日期")
        void noDateDirectory() {
            assertEquals("upload/avatar/", platform.getUploadDirectory("avatar", false),
                    "isToday=false 时不应混入日期目录");
        }

        @Test
        @DisplayName("子目录形式的类别不会产生双斜杠")
        void nestedCategory() {
            assertEquals("upload/avatar/2026/", platform.getUploadDirectory("avatar/2026"),
                    "类别自带尾斜杠时不重复补，否则路径里会出现 upload/avatar/2026//");
        }

        @Test
        @DisplayName("类别为 null、空串、纯空白时报「文件类别不能为空」")
        void blankCategory() {
            for (String category : new String[]{null, "", "   "}) {
                ServiceException exception =
                        assertThrows(ServiceException.class, () -> platform.getUploadDirectory(category));
                assertEquals(PARAM_INVALID.getCode(), exception.getCode());
                assertEquals("文件类别不能为空", exception.getMessage());
            }
        }

        @Test
        @DisplayName("类别自带尾斜杠时不会重复")
        void categoryWithTrailingSeparator() {
            assertEquals("upload/avatar/", platform.getUploadDirectory("avatar/"),
                    "formatDirectory 只在缺少分隔符时补，已有分隔符不会变成 //");
        }

        @Test
        @DisplayName("根目录未配置时报「请配置上传文件的根目录」")
        void blankRootDirectory() {
            for (String root : new String[]{null, "", "   "}) {
                FileConfig config = new FileConfig();
                config.setUploadDirectory(root);
                AbstractFilePlatformFactory custom = withConfig(new TestFilePlatform(), config);
                ServiceException exception =
                        assertThrows(ServiceException.class, () -> custom.getUploadDirectory("avatar"));
                assertEquals(PARAM_INVALID.getCode(), exception.getCode());
                assertEquals("请配置上传文件的根目录", exception.getMessage(),
                        "空根目录若放行，formatDirectory 会把它变成文件系统根目录，文件直接落到根下");
            }
        }

        @Test
        @DisplayName("根目录换成自定义值后同样补斜杠")
        void customRootDirectory() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("data/upload");
            AbstractFilePlatformFactory custom = withConfig(new TestFilePlatform(), config);
            assertEquals("data/upload/avatar/", custom.getUploadDirectory("avatar"));
        }

        @Test
        @DisplayName("类别里的 .. 不会被规范化")
        void traversalCategoryKept() {
            assertEquals("upload/../etc/", platform.getUploadDirectory("../etc"),
                    "⚠️ 已记录行为：类别参数原样拼进路径，既不规范化也不拒绝 ..，"
                            + "能防住穿越的只有 save 时对文件名的正则校验，目录部分没有任何防护");
        }
    }

    @Nested
    @DisplayName("基类默认实现")
    class DefaultBehavior {

        @Test
        @DisplayName("getUrl 默认原样返回路径")
        void urlDefaultsToPath() {
            assertEquals("upload/avatar/a.txt", plainPlatform.getUrl("upload/avatar/a.txt", 60),
                    "⚠️ 已记录行为：默认实现不做任何拼接，私有存储的平台必须重写为带签名的临时链接");
        }

        @Test
        @DisplayName("getUrl 的有效期参数被忽略")
        void urlIgnoresExpiry() {
            assertEquals("a.txt", plainPlatform.getUrl("a.txt", 0));
            assertEquals("a.txt", plainPlatform.getUrl("a.txt", -1),
                    "负数有效期不会报错，只是被默认实现原样忽略");
        }

        @Test
        @DisplayName("download 未实现时抛「该平台暂不支持下载文件」")
        void downloadUnsupported() {
            ServiceException exception = assertThrows(ServiceException.class, () -> plainPlatform.download("a.txt"));
            assertEquals("该平台暂不支持下载文件", exception.getMessage());
        }

        @Test
        @DisplayName("delete 未实现时抛「该平台暂不支持删除文件」")
        void deleteUnsupported() {
            ServiceException exception = assertThrows(ServiceException.class, () -> plainPlatform.delete("a.txt"));
            assertEquals("该平台暂不支持删除文件", exception.getMessage());
        }

        @Test
        @DisplayName("平台可以覆写默认行为")
        void overriddenByPlatform() throws IOException {
            assertEquals("test://a.txt?second=60", platform.getUrl("a.txt", 60));
            platform.delete("a.txt");
            assertEquals("a.txt", platform.deletedPath);
            try (InputStream inputStream = platform.download("a.txt")) {
                assertEquals("downloaded:a.txt", new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Nested
    @DisplayName("读取平台标识")
    class PlatformKey {

        @Test
        @DisplayName("取注解上的标识")
        void readsAnnotation() {
            assertEquals("TEST", platform.getKey());
        }

        @Test
        @DisplayName("子类重新标注时取子类的标识")
        void childKey() {
            assertEquals("OVERRIDDEN", withConfig(new OverriddenKeyPlatform(), fileConfig).getKey());
        }

        @Test
        @DisplayName("未标注注解时抛异常并带上类名")
        void missingAnnotation() {
            RuntimeException exception = assertThrows(RuntimeException.class, plainPlatform::getKey);
            assertTrue(exception.getMessage().contains(UnannotatedPlatform.class.getName()),
                    "异常里必须写清是哪个类缺注解，否则容器里几十个 Bean 时无从定位");
            assertTrue(exception.getMessage().contains("@FilePlatform"), exception.getMessage());
        }

        @Test
        @DisplayName("getKey 是 final，子类不能改写标识的读取方式")
        void getKeyIsFinal() throws NoSuchMethodException {
            assertTrue(Modifier.isFinal(AbstractFilePlatformFactory.class.getMethod("getKey").getModifiers()),
                    "标识只能来自注解；子类覆写会让注册表与实际平台对不上");
        }
    }

    @Nested
    @DisplayName("契约与健壮性")
    class Contract {

        @Test
        @DisplayName("save 是抽象方法，必须由平台实现")
        void saveIsAbstract() throws NoSuchMethodException {
            Method save = AbstractFilePlatformFactory.class.getMethod("save", InputStream.class, String.class, String.class);
            assertTrue(Modifier.isAbstract(save.getModifiers()),
                    "save 留给平台实现，基类只统一命名、哈希与大小校验");
        }

        @Test
        @DisplayName("upload 提供 6 个重载")
        void uploadOverloads() {
            long count = Arrays.stream(AbstractFilePlatformFactory.class.getMethods())
                    .filter(method -> "upload".equals(method.getName()))
                    .count();
            assertEquals(6L, count,
                    "输入流 / 上传文件 / 本地文件三类入口，各带与不带大小回调的重载；少一个就是 API 缺口");
        }

        @Test
        @DisplayName("未注入配置时依赖配置的接口会 NPE")
        void missingConfig() {
            TestFilePlatform bare = new TestFilePlatform();
            assertThrows(NullPointerException.class, () -> bare.getUploadDirectory("avatar"),
                    "getUploadDirectory 不做判空：平台要么作为 Spring Bean 使用，要么调用方自己兜住");
        }

        @Test
        @DisplayName("入参为 null 时都要明确拒绝")
        void nullArgumentsRejected() {
            assertThrows(ServiceException.class, () -> platform.upload((MultipartFile) null, "avatar"));
            assertThrows(ServiceException.class, () -> platform.upload((File) null, "avatar"));
            assertThrows(ServiceException.class, () -> platform.getFileHash((File) null));
            assertThrows(ServiceException.class, () -> platform.getFileName((MultipartFile) null));
            assertThrows(ServiceException.class, () -> platform.getFileName((File) null));
        }

        @Test
        @DisplayName("mock 实现的上传文件也能正常参与上传")
        void mockedMultipartFile() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("avatar.png");
            when(file.getInputStream())
                    .thenAnswer(invocation -> new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));
            assertEquals("upload/avatar/" + MD5_ABC + ".png", platform.upload(file, "avatar"));
        }
    }
}
