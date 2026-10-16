package cn.hamm.airpower.file;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.fixture.CloseAwareInputStream;
import cn.hamm.airpower.file.fixture.SingleStreamMultipartFile;
import cn.hamm.airpower.file.fixture.TestFilePlatform;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <h1>文件上传单元测试</h1>
 *
 * <p>上传链路有两段：<b>命名</b>（MD5 + 扩展名）与<b>落盘</b>（平台的 save）。
 * 本类把两段都钉住，重点是这几条容易被忽略的规则：</p>
 * <ul>
 *     <li>大小上限比较的是 {@code available()}，不是真实长度</li>
 *     <li>传入自定义回调后，基类<b>不再</b>做默认大小校验</li>
 *     <li>同一内容重复上传会落到同一路径并覆盖</li>
 *     <li>异常一律被包装成「上传文件失败，」，原始错误码会丢</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("文件上传单元测试")
class FileUploadTest {
    /**
     * 空内容的 MD5
     */
    private static final String MD5_EMPTY = "d41d8cd98f00b204e9800998ecf8427e";

    /**
     * 内容 "abc" 的 MD5
     */
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    /**
     * 被测平台
     */
    private TestFilePlatform platform;

    /**
     * 被测配置
     */
    private FileConfig fileConfig;

    @BeforeEach
    void setUp() {
        fileConfig = new FileConfig();
        platform = withConfig(new TestFilePlatform(), fileConfig);
    }

    /**
     * 构造一个注入好 {@link FileConfig} 的平台
     *
     * @param target     被测平台
     * @param fileConfig 配置
     * @param <T>        平台类型
     * @return 被测平台
     */
    private static <T extends AbstractFilePlatformFactory> T withConfig(T target, FileConfig fileConfig) {
        ReflectionTestUtils.setField(target, "fileConfig", fileConfig);
        return target;
    }

    /**
     * 构造内容为 {@code abc} 的流
     *
     * @return 输入流
     */
    private static InputStream abcStream() {
        return new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("以输入流上传")
    class UploadStream {

        @Test
        @DisplayName("返回「目录 + 文件名」，并把两者透传给平台")
        void returnsJoinedPath() {
            String path = platform.upload(abcStream(), "upload/avatar/", "a.txt");
            assertEquals("upload/avatar/a.txt", path);
            assertEquals("upload/avatar/", platform.lastDirectory);
            assertEquals("a.txt", platform.lastFileName);
            assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), platform.lastContent);
        }

        @Test
        @DisplayName("目录缺尾斜杠时自动补齐")
        void formatsDirectory() {
            assertEquals("upload/avatar/a.txt", platform.upload(abcStream(), "upload/avatar", "a.txt"));
            assertEquals("upload/avatar/", platform.lastDirectory,
                    "补斜杠后才能与文件名直接拼接，否则路径会变成 upload/avatara.txt");
        }

        @Test
        @DisplayName("目录为 null 时抛 ServiceException")
        void nullDirectoryThrowsNpe() {
            assertThrows(ServiceException.class, () -> platform.upload(abcStream(), null, "a.txt"),
                    "⚠️ 已记录缺陷：formatDirectory 在 try 之外，NPE 直接穿透，业务侧拿到的是 500 而不是可读的提示");
        }

        @Test
        @DisplayName("文件名为 null 时抛出 ServiceException")
        void nullFileNameNotRejected() {
            assertThrows(ServiceException.class, () -> platform.upload(abcStream(), "upload/avatar/", null));
        }

        @Test
        @DisplayName("文件名为空串时抛出ServiceException")
        void blankFileNameReturnsDirectory() {
            assertThrows(ServiceException.class, () -> platform.upload(abcStream(), "upload/avatar/", ""));
        }

        @Test
        @DisplayName("超过大小上限时报「文件大小超出限制」")
        void oversizedRejected() {
            fileConfig.setUploadMaxSize(2);
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> platform.upload(abcStream(), "upload/avatar/", "a.txt"));
            assertTrue(exception.getMessage().contains("文件大小超出限制"), exception.getMessage());
            assertEquals(0, platform.saveCount, "大小不通过时不应落盘");
        }

        @Test
        @DisplayName("大小超限后仍保留参数非法的错误码")
        void oversizedKeepsErrorCode() {
            fileConfig.setUploadMaxSize(2);
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> platform.upload(abcStream(), "upload/avatar/", "a.txt"));
            assertTrue(exception.getMessage().contains("文件大小超出限制"), exception.getMessage());
            assertEquals(PARAM_INVALID.getCode(), exception.getCode(),
                    "错误码必须一路透传到前端：降级成通用服务错误会把排查方向带偏");
        }

        @Test
        @DisplayName("刚好等于上限时放行")
        void exactlyAtLimitPasses() {
            fileConfig.setUploadMaxSize(3);
            assertEquals("upload/avatar/a.txt", platform.upload(abcStream(), "upload/avatar/", "a.txt"),
                    "比较用的是 > 而不是 >=，等于上限视为合法");
        }

        @Test
        @DisplayName("上限为 0 时空文件仍可上传")
        void zeroLimitAllowsEmptyStream() {
            fileConfig.setUploadMaxSize(0);
            assertEquals("upload/avatar/a.txt",
                    platform.upload(new ByteArrayInputStream(new byte[0]), "upload/avatar/", "a.txt"));
        }

        @Test
        @DisplayName("available() 失真时超大文件能绕过上限")
        void availableZeroBypassesLimit() {
            fileConfig.setUploadMaxSize(10);
            byte[] content = new byte[4096];
            InputStream inputStream = new UnreliableInputStream(content, 0, -1);
            assertEquals("upload/avatar/a.bin",
                    platform.upload(inputStream, "upload/avatar/", "a.bin"),
                    "⚠️ 已记录缺陷：大小校验依赖 available()，未标记长度的流（如压缩包、网络流）恒为 0，10MB 上限形同虚设");
        }

        @Test
        @DisplayName("不带回调的重载同样走默认大小校验")
        void threeArgsOverloadUsesDefaultLimit() {
            fileConfig.setUploadMaxSize(2);
            assertThrows(ServiceException.class, () -> platform.upload(abcStream(), "upload/avatar/", "a.txt"));
        }

        @Test
        @DisplayName("传入回调后不再做默认校验，回调拿到字节数")
        void customCallbackSkipsDefaultCheck() {
            fileConfig.setUploadMaxSize(2);
            AtomicReference<Integer> received = new AtomicReference<>();
            String path = platform.upload(abcStream(), "upload/avatar/", "a.txt", received::set);
            assertEquals("upload/avatar/a.txt", path);
            assertEquals(3, received.get(), "回调收到的是 available() 的值，也就是字节数");
            assertEquals(1, platform.saveCount, "传了回调就不再拦大小，超限文件也能落盘");
        }

        @Test
        @DisplayName("回调抛异常时被包装成「上传文件失败」")
        void callbackFailureWrapped() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.upload(abcStream(), "upload/avatar/", "a.txt",
                            size -> {
                                throw new IllegalStateException("大小不合法");
                            }));
            assertTrue(exception.getMessage().startsWith("上传文件失败，"), exception.getMessage());
            assertTrue(exception.getMessage().contains("大小不合法"),
                    "回调自己的提示必须透传，否则业务侧写的大小校验等于白写");
        }

        @Test
        @DisplayName("回调抛出的业务异常保留错误码与提示")
        void callbackErrorCodeKept() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.upload(abcStream(), "upload/avatar/", "a.txt",
                            size -> {
                                throw new ServiceException(PARAM_INVALID.getCode(), "自定义提示");
                            }));
            assertEquals(PARAM_INVALID.getCode(), exception.getCode(),
                    "业务侧自己写的大小校验抛出的带码异常，包装后错误码不能丢");
            assertTrue(exception.getMessage().contains("自定义提示"), exception.getMessage());
        }

        @Test
        @DisplayName("平台落盘失败时被包装成「上传文件失败」")
        void saveFailureWrapped() {
            platform.saveException = new IllegalStateException("磁盘只读");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.upload(abcStream(), "upload/avatar/", "a.txt"));
            assertTrue(exception.getMessage().startsWith("上传文件失败，"), exception.getMessage());
            assertTrue(exception.getMessage().contains("磁盘只读"), "平台失败的原因必须透传");
        }

        @Test
        @DisplayName("平台抛出的业务异常保留错误码")
        void saveFailureKeepsErrorCode() {
            platform.saveException = new ServiceException(PARAM_INVALID.getCode(), "文件名非法");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.upload(abcStream(), "upload/avatar/", "a.txt"));
            assertEquals(PARAM_INVALID.getCode(), exception.getCode(),
                    "本地平台因文件名非法抛出的 PARAM_INVALID，包装后错误码不能丢");
            assertTrue(exception.getMessage().contains("文件名非法"), exception.getMessage());
            assertEquals("文件名非法", exception.getCause().getMessage(),
                    "原始异常要留在 cause 上，否则异常拦截器打印出来的只有一句包装后的提示");
        }

        @Test
        @DisplayName("未注入配置时 NPE 被包装成「上传文件失败」")
        void missingConfigWrapped() {
            TestFilePlatform bare = new TestFilePlatform();
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> bare.upload(abcStream(), "upload/avatar/", "a.txt"));
            assertTrue(exception.getMessage().startsWith("上传文件失败，"), exception.getMessage());
        }

        @Test
        @DisplayName("回调为 null 时等价于默认校验")
        void nullCallbackIsDefault() {
            fileConfig.setUploadMaxSize(2);
            assertThrows(ServiceException.class, () -> platform.upload(abcStream(), "upload/avatar/", "a.txt", null));
        }

        @Test
        @DisplayName("同一内容重复上传落到同一路径并覆盖")
        void sameContentSamePath() {
            String first = platform.upload(abcStream(), "upload/avatar/", "a.txt");
            String second = platform.upload(abcStream(), "upload/avatar/", "a.txt");
            assertEquals(first, second);
            assertEquals(2, platform.saveCount, "路径相同但两次都会落盘，第二次覆盖第一次");
            assertEquals(List.of("upload/avatar/a.txt", "upload/avatar/a.txt"), platform.savedKeys);
        }
    }

    @Nested
    @DisplayName("以 MultipartFile 上传")
    class UploadMultipartFile {

        @Test
        @DisplayName("文件名为「MD5 + 小写扩展名」")
        void namesFileByMd5() {
            MultipartFile file = new MockMultipartFile("file", "avatar.PNG", "image/png", "abc".getBytes(StandardCharsets.UTF_8));
            String path = platform.upload(file, "avatar");
            assertEquals("upload/avatar/" + MD5_ABC + ".png", path,
                    "扩展名统一小写，Windows / Linux 上同名文件不会各存一份");
            assertEquals(MD5_ABC, platform.lastFileName.substring(0, MD5_ABC.length()));
            assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), platform.lastContent);
        }

        @Test
        @DisplayName("没有扩展名时文件名以点结尾")
        void noExtension() {
            MultipartFile file = new MockMultipartFile("file", "README", null, "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("upload/avatar/" + MD5_ABC + ".", platform.upload(file, "avatar"),
                    "⚠️ 已记录行为：扩展名为空仍会拼一个点；本地能落盘，Windows 会把尾点截掉，云端对象名多一个点");
        }

        @Test
        @DisplayName("隐藏文件按无扩展名处理")
        void dotFileHasNoExtension() {
            MultipartFile file = new MockMultipartFile("file", ".gitignore", null, "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("upload/avatar/" + MD5_ABC + ".", platform.upload(file, "avatar"));
        }

        @Test
        @DisplayName("同名不同内容落到不同路径")
        void differentContentDifferentPath() {
            MultipartFile png = new MockMultipartFile("file", "a.png", null, "abc".getBytes(StandardCharsets.UTF_8));
            MultipartFile other = new MockMultipartFile("file", "b.png", null, "abd".getBytes(StandardCharsets.UTF_8));
            assertNotEquals(platform.upload(png, "avatar"), platform.upload(other, "avatar"));
        }

        @Test
        @DisplayName("不同名同内容落到同一路径")
        void sameContentSamePath() {
            MultipartFile png = new MockMultipartFile("file", "a.png", null, "abc".getBytes(StandardCharsets.UTF_8));
            MultipartFile copy = new MockMultipartFile("file", "b.png", null, "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals(platform.upload(png, "avatar"), platform.upload(copy, "avatar"),
                    "以内容哈希命名实现了秒传式去重：同一份内容只会留一份，重复上传会覆盖");
        }

        @Test
        @DisplayName("空文件用空内容的 MD5 命名")
        void emptyFile() {
            MultipartFile file = new MockMultipartFile("file", "empty.txt", null, new byte[0]);
            assertEquals("upload/avatar/" + MD5_EMPTY + ".txt", platform.upload(file, "avatar"));
        }

        @Test
        @DisplayName("类别为空时先报错，不去读文件")
        void blankCategoryRejectedFirst() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("a.png");
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.upload(file, ""));
            assertEquals("文件类别不能为空", exception.getMessage(),
                    "目录先于文件校验：类别错了就不该再去读文件内容");
        }

        @Test
        @DisplayName("读取文件失败时抛出「上传文件失败，」")
        void brokenInputStreamThrows() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("a.png");
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)))
                    .thenThrow(new IOException("模拟磁盘读取失败"));
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.upload(file, "avatar"));
            assertEquals("上传文件失败，", exception.getMessage(),
                    "⚠️ 已记录缺陷：这里既没带上原因，连异常类型都看不出；"
                            + "对比同一方法里另外两处包装都带了 e.getMessage()，显然属于遗漏");
        }

        @Test
        @DisplayName("文件名带路径穿越时不会被原样拼进路径")
        void traversalNameIsNeutralised() {
            MultipartFile file = new MockMultipartFile("file", "../../../etc/passwd", null,
                    "abc".getBytes(StandardCharsets.UTF_8));
            String path = platform.upload(file, "avatar");
            assertTrue(path.startsWith("upload/avatar/" + MD5_ABC + "."), "文件名主体是 MD5，实际为 " + path);
            assertTrue(!path.contains(".."), "生成的文件名里不可能出现 ..，扩展名取自最后一个点之后，实际为 " + path);
        }

        @Test
        @DisplayName("最后一个点之后的目录片段会被当成扩展名")
        void extensionKeepsDirectoryPart() {
            MultipartFile file = new MockMultipartFile("file", "../../../etc/passwd", null,
                    "abc".getBytes(StandardCharsets.UTF_8));
            String path = platform.upload(file, "avatar");
            assertEquals("upload/avatar/" + MD5_ABC + "./etc/passwd", path,
                    "⚠️ 已记录缺陷：FileUtil.getExtension 取「最后一个点之后」的全部内容，"
                            + "于是目录片段被带进文件名；本地平台会因文件名含 / 拒绝落盘，云端则会多出一层对象 key，实际为 " + path);
        }

        @Test
        @DisplayName("超过大小上限时被拦下")
        void oversizedRejected() {
            fileConfig.setUploadMaxSize(2);
            MultipartFile file = new MockMultipartFile("file", "a.txt", null, "abc".getBytes(StandardCharsets.UTF_8));
            ServiceException exception = assertThrows(ServiceException.class, () -> platform.upload(file, "avatar"));
            assertTrue(exception.getMessage().contains("文件大小超出限制"), exception.getMessage());
            assertEquals(0, platform.saveCount);
        }

        @Test
        @DisplayName("传入回调后可以上传超大文件")
        void customCallbackAllowsOversized() {
            fileConfig.setUploadMaxSize(2);
            AtomicReference<Integer> received = new AtomicReference<>();
            MultipartFile file = new MockMultipartFile("file", "a.txt", null, "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("upload/avatar/" + MD5_ABC + ".txt",
                    platform.upload(file, "avatar", received::set));
            assertEquals(3, received.get());
        }

        @Test
        @DisplayName("只返回同一个流的上传文件会存成 0 字节")
        void singleStreamMultipartFileLosesContent() {
            MultipartFile file = new SingleStreamMultipartFile("abc".getBytes(StandardCharsets.UTF_8), "a.txt");
            String path = platform.upload(file, "avatar");
            assertEquals("upload/avatar/" + MD5_ABC + ".txt", path);
            assertEquals(0, platform.lastContent.length,
                    "⚠️ 已记录缺陷：算哈希时已把流读空，落盘拿到的是空流，"
                            + "文件内容与文件名对不上，且不会有任何报错");
        }

        @Test
        @DisplayName("自定义回调收到的字节数与内容一致")
        void callbackReceivesRealSize() {
            AtomicInteger size = new AtomicInteger(-1);
            MultipartFile file = new MockMultipartFile("file", "a.txt", null, "abc".getBytes(StandardCharsets.UTF_8));
            platform.upload(file, "avatar", size::set);
            assertEquals(3, size.get());
        }
    }

    @Nested
    @DisplayName("以本地文件上传")
    class UploadLocalFile {

        @Test
        @DisplayName("文件名为「MD5 + 小写扩展名」")
        void namesFileByMd5() throws IOException {
            Path file = Files.createTempFile("airpower", ".TXT");
            try {
                Files.writeString(file, "abc");
                assertEquals("upload/avatar/" + MD5_ABC + ".txt", platform.upload(file.toFile(), "avatar"));
            } finally {
                Files.deleteIfExists(file);
            }
        }

        @Test
        @DisplayName("没有扩展名时文件名以点结尾")
        void noExtension() throws IOException {
            Path file = Files.createTempFile("airpower", "");
            try {
                Files.writeString(file, "abc");
                assertEquals("upload/avatar/" + MD5_ABC + ".", platform.upload(file.toFile(), "avatar"));
            } finally {
                Files.deleteIfExists(file);
            }
        }

        @Test
        @DisplayName("文件不存在时抛出「计算文件哈希失败」并带上原因")
        void missingFileThrows() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.upload(new File("no-such-file.txt"), "avatar"));
            assertTrue(exception.getMessage().startsWith("计算文件哈希失败"), exception.getMessage());
            assertTrue(exception.getMessage().contains("No such file or directory"),
                    "文件是在算哈希那一步就被打开的，原因能透传出来：" + exception.getMessage());
        }

        @Test
        @DisplayName("传入目录时报「计算文件哈希失败」")
        void directoryThrows() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> platform.upload(new File(System.getProperty("java.io.tmpdir")), "avatar"));
            assertTrue(exception.getMessage().startsWith("计算文件哈希失败"), exception.getMessage());
            assertTrue(exception.getMessage().contains("Is a directory"),
                    "把目录当文件传进来时，提示里必须能看出是「这是目录」而不是「文件损坏」：" + exception.getMessage());
        }

        @Test
        @DisplayName("空文件用空内容的 MD5 命名")
        void emptyFile() throws IOException {
            Path file = Files.createTempFile("airpower", ".txt");
            try {
                assertEquals("upload/avatar/" + MD5_EMPTY + ".txt", platform.upload(file.toFile(), "avatar"));
            } finally {
                Files.deleteIfExists(file);
            }
        }

        @Test
        @DisplayName("同名文件内容相同落到同一路径")
        void idempotentByContent() throws IOException {
            Path file = Files.createTempFile("airpower", ".txt");
            try {
                Files.writeString(file, "abc");
                assertEquals(platform.upload(file.toFile(), "avatar"), platform.upload(file.toFile(), "avatar"));
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    @Nested
    @DisplayName("上传路径的组合")
    class PathComposition {

        @Test
        @DisplayName("自定义根目录会体现在最终路径里")
        void customRoot() {
            fileConfig.setUploadDirectory("/data/upload");
            assertEquals("/data/upload/avatar/" + MD5_ABC + ".txt",
                    platform.upload(new MockMultipartFile("file", "a.txt", null,
                            "abc".getBytes(StandardCharsets.UTF_8)), "avatar"));
        }

        @Test
        @DisplayName("类别可以带子目录")
        void nestedCategory() {
            assertEquals("upload/avatar/2026/" + MD5_ABC + ".txt",
                    platform.upload(new MockMultipartFile("file", "a.txt", null,
                            "abc".getBytes(StandardCharsets.UTF_8)), "avatar/2026"),
                    "类别本身不做规范化和清洗，调用方应保证它安全");
        }

        @Test
        @DisplayName("多次上传会依次记录各自的路径")
        void recordsEachUpload() {
            List<String> paths = new ArrayList<>();
            paths.add(platform.upload(abcStream(), "upload/avatar/", "a.txt"));
            paths.add(platform.upload(abcStream(), "upload/avatar/", "b.txt"));
            assertEquals(List.of("upload/avatar/a.txt", "upload/avatar/b.txt"), paths);
            assertEquals(paths, platform.savedKeys, "返回给调用方的路径与平台实际落盘的路径必须一致");
        }

        @Test
        @DisplayName("内容以 UTF-8 落盘")
        void utf8Content() {
            String content = "中文内容";
            platform.upload(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    "upload/avatar/", "a.txt");
            assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), platform.lastContent);
        }

        @Test
        @DisplayName("上传后不会关闭调用方传入的流")
        void doesNotCloseCallerStream() {
            CloseAwareInputStream inputStream =
                    new CloseAwareInputStream("abc".getBytes(StandardCharsets.UTF_8));
            platform.upload(inputStream, "upload/avatar/", "a.txt");
            assertFalse(inputStream.isClosed(),
                    "⚠️ 已记录行为：流的所有权在调用方，基类与平台都不关它；"
                            + "调用方想复用同一个流时不会被踩到，但也得自己记得关");
        }

        @Test
        @DisplayName("算哈希那一步会把 MultipartFile 给的流关掉")
        void hashStepClosesMultipartStream() {
            CloseAwareInputStream content = new CloseAwareInputStream("abc".getBytes(StandardCharsets.UTF_8));
            MultipartFile file = new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return "a.txt";
                }

                @Override
                public String getContentType() {
                    return null;
                }

                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public long getSize() {
                    return 3;
                }

                @Override
                public byte[] getBytes() {
                    return "abc".getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public InputStream getInputStream() {
                    return content;
                }

                @Override
                public void transferTo(java.io.File dest) {
                    // 测试用不到
                }
            };
            platform.upload(file, "avatar");
            assertTrue(content.isClosed(),
                    "⚠️ 已记录行为：getFileHash() 用 try-with-resources 关掉了从 MultipartFile 取出的流。"
                            + "每次返回新流的实现（Spring 自带的几种）不受影响，"
                            + "但复用同一流实例的实现会因此丢内容，见 singleStreamMultipartFileLosesContent");
        }
    }
}
