package cn.hamm.airpower.file.platform.local;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.FileConfig;
import cn.hamm.airpower.file.fixture.SingleStreamMultipartFile;
import cn.hamm.airpower.file.fixture.UnreliableInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.nio.file.Paths;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>本地存储平台单元测试</h2>
 *
 * <p>三个平台里只有它会直接动磁盘，因此测试重点是两件事：</p>
 * <ul>
 *     <li>文件是否真的落到了配置的根目录下</li>
 *     <li>删除时能否挡住「跳出根目录」和「顺着符号链接删到别处」</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("本地存储平台单元测试")
class LocalFileHelperTest {
    /**
     * 内容 "abc" 的 MD5
     */
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    /**
     * 存储根目录（每个用例独立）
     */
    @TempDir
    Path root;

    /**
     * 被测平台
     */
    private LocalFileHelper helper;

    /**
     * 本地存储配置
     */
    private LocalFileConfig localFileConfig;

    @BeforeEach
    void setUp() {
        localFileConfig = new LocalFileConfig();
        localFileConfig.setLocalAbsoluteDirectory(root.toString());
        helper = new LocalFileHelper();
        ReflectionTestUtils.setField(helper, "localFileConfig", localFileConfig);
        ReflectionTestUtils.setField(helper, "fileConfig", new FileConfig());
    }

    /**
     * 在根目录下创建一个文件
     *
     * @param relativePath 相对路径
     * @param content      内容
     * @return 真实路径
     * @throws IOException IO 异常
     */
    private Path createFile(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
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
    @DisplayName("保存文件")
    class Save {

        @Test
        @DisplayName("写到「根目录 + 目录 + 文件名」")
        void writesToConfiguredRoot() throws IOException {
            helper.save(abcStream(), "upload/avatar/", "a.txt");
            Path target = root.resolve("upload/avatar/a.txt");
            assertTrue(Files.exists(target), "文件应落在 " + target);
            assertEquals("abc", Files.readString(target));
        }

        @Test
        @DisplayName("目录不存在时自动创建")
        void createsMissingDirectories() throws IOException {
            helper.save(abcStream(), "a/b/c/d/", "a.txt");
            assertTrue(Files.exists(root.resolve("a/b/c/d/a.txt")));
        }

        @Test
        @DisplayName("同名文件会被覆盖")
        void overwritesExistingFile() throws IOException {
            Path target = createFile("upload/a.txt", "旧内容");
            helper.save(abcStream(), "upload/", "a.txt");
            assertEquals("abc", Files.readString(target), "同名上传以新内容为准，不做多版本保留");
        }

        @Test
        @DisplayName("目录不带尾斜杠时仍能落对位置")
        void directoryWithoutSeparator() throws IOException {
            helper.save(abcStream(), "upload/avatar", "a.txt");
            assertTrue(Files.exists(root.resolve("upload/avatar/a.txt")),
                    "FileUtil.saveFile 内部会再补一次分隔符，绕过 upload() 直接调用也不会把文件名粘到目录名上");
            assertFalse(Files.exists(root.resolve("upload/avatara.txt")));
        }

        @Test
        @DisplayName("空内容落盘为 0 字节文件")
        void emptyContent() throws IOException {
            helper.save(new ByteArrayInputStream(new byte[0]), "upload/", "empty.txt");
            assertEquals(0, Files.size(root.resolve("upload/empty.txt")));
        }

        @Test
        @DisplayName("文件名带 .. 时被拒绝")
        void traversalFileNameRejected() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> helper.save(abcStream(), "upload/", "../evil.txt"));
            assertTrue(exception.getMessage().contains("非法的文件名"), exception.getMessage());
        }

        @Test
        @DisplayName("文件名带目录分隔符时被拒绝")
        void fileNameWithSeparatorRejected() {
            assertThrows(ServiceException.class, () -> helper.save(abcStream(), "upload/", "a/b.txt"),
                    "文件名的正则不允许 /，也顺带挡住了「扩展名里带路径」的穿越写法");
        }

        @Test
        @DisplayName("文件名含非安全字符时被拒绝")
        void unsafeCharacterRejected() {
            assertThrows(ServiceException.class, () -> helper.save(abcStream(), "upload/", "a b.txt"));
            assertThrows(ServiceException.class, () -> helper.save(abcStream(), "upload/", "中文名.txt"));
        }

        @Test
        @DisplayName("目标是符号链接时被拒绝")
        void symbolicLinkRejected() throws IOException {
            Path outside = Files.createTempFile("airpower-outside", ".txt");
            try {
                Files.createDirectories(root.resolve("upload"));
                Files.createSymbolicLink(root.resolve("upload/link.txt"), outside);
                ServiceException exception = assertThrows(ServiceException.class,
                        () -> helper.save(abcStream(), "upload/", "link.txt"));
                assertTrue(exception.getMessage().contains("符号链接"), exception.getMessage());
            } finally {
                Files.deleteIfExists(outside);
            }
        }

        @Test
        @DisplayName("流读取失败时抛「保存文件失败」")
        void brokenStreamThrows() {
            InputStream inputStream = new UnreliableInputStream("abc".getBytes(StandardCharsets.UTF_8), 3, 1);
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> helper.save(inputStream, "upload/", "a.txt"));
            assertTrue(exception.getMessage().startsWith("保存文件失败，"), exception.getMessage());
        }

        @Test
        @DisplayName("大文件能完整落盘")
        void largeFile() throws IOException {
            byte[] content = new byte[1024 * 1024];
            java.util.Arrays.fill(content, (byte) 'z');
            helper.save(new ByteArrayInputStream(content), "upload/", "big.bin");
            assertArrayEquals(content, Files.readAllBytes(root.resolve("upload/big.bin")));
        }
    }

    @Nested
    @DisplayName("删除文件")
    class Delete {

        @Test
        @DisplayName("删除已存在的文件")
        void deletesExistingFile() throws IOException {
            Path target = createFile("upload/avatar/a.txt", "abc");
            helper.delete("upload/avatar/a.txt");
            assertFalse(Files.exists(target), "文件应已被删除");
        }

        @Test
        @DisplayName("文件不存在时静默通过")
        void missingFileIsNoop() throws IOException {
            assertDoesNotThrow(() -> helper.delete("upload/not-exist.txt"),
                    "删除是幂等操作，重复调用不应报错，否则重试机制会被卡住");
        }

        @Test
        @DisplayName("路径为 null、空串、纯空白时报「文件路径不能为空」")
        void blankPathRejected() {
            for (String path : new String[]{null, "", "   "}) {
                ServiceException exception = assertThrows(ServiceException.class, () -> helper.delete(path));
                assertEquals(PARAM_INVALID.getCode(), exception.getCode());
                assertEquals("文件路径不能为空", exception.getMessage());
            }
        }

        @Test
        @DisplayName("路径跳出根目录时报「非法的文件路径」")
        void traversalRejected() throws IOException {
            Path outside = Files.createTempFile("airpower-outside", ".txt");
            try {
                ServiceException exception =
                        assertThrows(ServiceException.class, () -> helper.delete("../" + outside.getFileName()));
                assertEquals("非法的文件路径", exception.getMessage());
                assertTrue(Files.exists(outside), "根目录外的文件必须毫发无损");
            } finally {
                Files.deleteIfExists(outside);
            }
        }

        @Test
        @DisplayName("多层 .. 也逃不出去")
        void deepTraversalRejected() {
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> helper.delete("upload/../../../etc/hosts"));
            assertEquals("非法的文件路径", exception.getMessage());
        }

        @Test
        @DisplayName("绝对路径一律拒绝")
        void absolutePathRejected() {
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> helper.delete(Paths.get("/etc").toString()));
            assertEquals("非法的文件路径", exception.getMessage(),
                    "Path#resolve 遇到绝对路径会直接返回它，不做根目录判断就会删掉系统文件");
        }

        @Test
        @DisplayName("同名前缀的兄弟目录不算越权")
        void siblingPrefixRejected() throws IOException {
            // 根目录 /tmp/root-a，攻击路径 /tmp/root-a-evil/x.txt，
            // 字符串前缀看着都在根目录里，但 Path#startsWith 按路径段比较，必须判成越权
            ServiceException exception =
                    assertThrows(ServiceException.class, () -> helper.delete("../" + root.getFileName() + "-evil/x.txt"));
            assertEquals("非法的文件路径", exception.getMessage());
        }

        @Test
        @DisplayName("路径含 . 与 .. 但仍在根内时正常删除")
        void normalisedInsideRoot() throws IOException {
            Path target = createFile("upload/a.txt", "abc");
            helper.delete("upload/./sub/../a.txt");
            assertFalse(Files.exists(target), "规范化后仍在根目录内，属于合法删除");
        }

        @Test
        @DisplayName("符号链接本身拒绝删除")
        void symbolicLinkRejected() throws IOException {
            Path outside = Files.createTempFile("airpower-outside", ".txt");
            try {
                Files.createDirectories(root.resolve("upload"));
                Files.createSymbolicLink(root.resolve("upload/link.txt"), outside);
                ServiceException exception =
                        assertThrows(ServiceException.class, () -> helper.delete("upload/link.txt"));
                assertEquals("不允许操作符号链接", exception.getMessage());
            } finally {
                Files.deleteIfExists(outside);
            }
        }

        @Test
        @DisplayName("中间目录是符号链接时拒绝删除")
        void symbolicLinkDirectoryRejected() throws IOException {
            // 根目录内的 upload/link -> 根目录外的一整个目录树
            Path outsideDir = Files.createTempDirectory("airpower-outside");
            try {
                Path secret = outsideDir.resolve("secret.txt");
                Files.writeString(secret, "机密");
                Files.createDirectories(root.resolve("upload"));
                Files.createSymbolicLink(root.resolve("upload/link"), outsideDir);
                ServiceException exception =
                        assertThrows(ServiceException.class, () -> helper.delete("upload/link/secret.txt"));
                assertEquals("不允许操作符号链接", exception.getMessage(),
                        "目标自身不是链接，但父目录是链接时照样能删到根目录之外，必须逐级检查");
                assertTrue(Files.exists(secret), "根目录外的文件必须毫发无损");
            } finally {
                Files.deleteIfExists(outsideDir.resolve("secret.txt"));
                Files.deleteIfExists(outsideDir);
            }
        }

        @Test
        @DisplayName("多级目录里的符号链接同样被拦下")
        void deepSymbolicLinkRejected() throws IOException {
            Path outsideDir = Files.createTempDirectory("airpower-outside");
            try {
                Files.writeString(outsideDir.resolve("secret.txt"), "机密");
                Files.createDirectories(root.resolve("upload/a/b"));
                Files.createSymbolicLink(root.resolve("upload/a/link"), outsideDir);
                assertThrows(ServiceException.class, () -> helper.delete("upload/a/link/secret.txt"));
                assertTrue(Files.exists(outsideDir.resolve("secret.txt")));
            } finally {
                Files.deleteIfExists(outsideDir.resolve("secret.txt"));
                Files.deleteIfExists(outsideDir);
            }
        }

        @Test
        @DisplayName("链路中间是符号链接时，删链接自己也不行")
        void symbolicLinkItselfUnderLinkRejected() throws IOException {
            Path outsideDir = Files.createTempDirectory("airpower-outside");
            try {
                Files.createDirectories(root.resolve("upload"));
                Files.createSymbolicLink(root.resolve("upload/link"), outsideDir);
                assertThrows(ServiceException.class, () -> helper.delete("upload/link"),
                        "删除符号链接自身（不跟随）本可以放行，这里按「路径中出现链接就拒绝」的从严策略处理");
            } finally {
                Files.deleteIfExists(root.resolve("upload/link"));
                Files.deleteIfExists(outsideDir);
            }
        }

        @Test
        @DisplayName("路径中的普通目录不会被误判为链接")
        void plainDirectoryStillDeletable() throws IOException {
            Path target = createFile("upload/a/b/c.txt", "abc");
            helper.delete("upload/a/b/c.txt");
            assertFalse(Files.exists(target), "逐级检查不能把正常的多级目录当成链接拦下来");
        }

        @Test
        @DisplayName("非空目录删不掉")
        void nonEmptyDirectoryRejected() throws IOException {
            createFile("upload/a.txt", "abc");
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.delete("upload"));
            assertEquals("删除文件失败", exception.getMessage());
            assertTrue(Files.exists(root.resolve("upload/a.txt")), "删除失败时不能连带删掉里面的文件");
        }

        @Test
        @DisplayName("空目录会被整个删掉")
        void emptyDirectoryDeleted() throws IOException {
            Files.createDirectories(root.resolve("upload/empty"));
            helper.delete("upload/empty");
            assertFalse(Files.exists(root.resolve("upload/empty")),
                    "⚠️ 已记录行为：delete 不区分文件与目录，传目录进来删的是目录本身");
        }

        @Test
        @DisplayName("传根目录本身时被拒绝")
        void deleteRootRejected() throws IOException {
            createFile("upload/a.txt", "abc");
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.delete("."));
            assertEquals("不允许删除存储根目录", exception.getMessage(),
                    "「.」规范化后正好等于根目录，startsWith 判定会放行；"
                            + "一个 delete 调用就能抹掉整个存储目录");
            assertTrue(Files.exists(root), "根目录必须还在");
        }

        @Test
        @DisplayName("根目录为空时同样拒绝删除根目录")
        void deleteEmptyRootRejected() {
            assertThrows(ServiceException.class, () -> helper.delete("."));
            assertTrue(Files.exists(root), "空根目录也不能被一次 delete 抹掉");
        }

        @Test
        @DisplayName("绕道 .. 回到根目录同样被拒绝")
        void deleteRootViaParentRejected() throws IOException {
            createFile("upload/a.txt", "abc");
            assertThrows(ServiceException.class, () -> helper.delete("upload/.."),
                    "规范化后等于根目录的路径必须一并拦下");
            assertTrue(Files.exists(root));
        }
    }

    @Nested
    @DisplayName("上传与删除联动")
    class UploadThenDelete {

        @Test
        @DisplayName("上传后返回的相对路径可直接用于删除")
        void uploadReturnsDeletablePath() throws IOException {
            MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                    "abc".getBytes(StandardCharsets.UTF_8));
            String path = helper.upload(file, "avatar");
            assertEquals("upload/avatar/" + MD5_ABC + ".png", path);
            Path target = root.resolve("upload/avatar/" + MD5_ABC + ".png");
            assertTrue(Files.exists(target), "文件应真实落盘");

            helper.delete(path);
            assertFalse(Files.exists(target), "返回的路径就是存储路径，能直接喂给 delete");
        }

        @Test
        @DisplayName("重复上传同内容会覆盖同一个文件")
        void reUploadOverwrites() throws IOException {
            MultipartFile file = new MockMultipartFile("file", "avatar.png", null, "abc".getBytes(StandardCharsets.UTF_8));
            helper.upload(file, "avatar");
            String path = helper.upload(file, "avatar");
            assertEquals("upload/avatar/" + MD5_ABC + ".png", path);
            try (var files = Files.list(root.resolve("upload/avatar"))) {
                assertEquals(1, files.count(), "同名覆盖，目录里应只剩一个文件");
            }
        }

        @Test
        @DisplayName("文件名带目录片段时本地落盘被拒绝")
        void traversalNameRejectedOnLocal() {
            MultipartFile file = new MockMultipartFile("file", "../../../etc/passwd", null,
                    "abc".getBytes(StandardCharsets.UTF_8));
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.upload(file, "avatar"));
            assertTrue(exception.getMessage().contains("非法的文件名"),
                    "扩展名取自最后一个点之后，把「../../../etc/」带进了文件名，本地平台必须拒绝：" + exception.getMessage());
        }

        @Test
        @DisplayName("只返回同一个流的上传文件会落成 0 字节")
        void singleStreamFileBecomesEmpty() throws IOException {
            MultipartFile file = new SingleStreamMultipartFile("abc".getBytes(StandardCharsets.UTF_8), "a.txt");
            String path = helper.upload(file, "avatar");
            Path target = root.resolve(path);
            assertEquals(0, Files.size(target),
                    "⚠️ 已记录缺陷：算哈希的流已读空，落盘拿到 0 字节，但文件名却是全量内容的 MD5，"
                            + "再上传一次「同名」文件会因路径相同而把损坏结果固化下来");
        }

        @Test
        @DisplayName("超过大小上限时不落盘")
        void oversizedNotSaved() {
            FileConfig fileConfig = new FileConfig();
            fileConfig.setUploadMaxSize(2);
            ReflectionTestUtils.setField(helper, "fileConfig", fileConfig);
            MultipartFile file = new MockMultipartFile("file", "a.txt", null, "abc".getBytes(StandardCharsets.UTF_8));
            assertThrows(ServiceException.class, () -> helper.upload(file, "avatar"));
            assertFalse(Files.exists(root.resolve("upload/avatar")),
                    "超限的文件不应留下空目录以外的任何痕迹");
        }

        @Test
        @DisplayName("存储目录未配置时保存与删除都报配置错误")
        void blankStorageDirectoryRejected() {
            localFileConfig.setLocalAbsoluteDirectory("");
            ServiceException saveError = assertThrows(ServiceException.class,
                    () -> helper.save(abcStream(), "upload/", "a.txt"));
            assertEquals("请配置本地文件存储目录", saveError.getMessage(),
                    "空存储目录放行时会退化成文件系统根目录，文件直接落到根下");
            ServiceException deleteError =
                    assertThrows(ServiceException.class, () -> helper.delete("upload/a.txt"));
            assertEquals("请配置本地文件存储目录", deleteError.getMessage(),
                    "删除时的越权校验基准同样会退化成系统根目录");
        }

        @Test
        @DisplayName("存储根目录不存在时自动创建")
        void createsRootDirectory() throws IOException {
            Path missing = root.resolve("not-exist-yet");
            localFileConfig.setLocalAbsoluteDirectory(missing.toString());
            LocalFileHelper fresh = new LocalFileHelper();
            ReflectionTestUtils.setField(fresh, "localFileConfig", localFileConfig);
            ReflectionTestUtils.setField(fresh, "fileConfig", new FileConfig());
            fresh.save(abcStream(), "upload/", "a.txt");
            assertTrue(Files.exists(missing.resolve("upload/a.txt")),
                    "存储根目录配错成不存在的路径时应自动创建，而不是每次上传都失败");
        }
    }

    @Nested
    @DisplayName("平台标识与默认能力")
    class PlatformBasics {

        @Test
        @DisplayName("平台标识为 LOCAL")
        void key() {
            assertEquals("LOCAL", helper.getKey());
        }

        @Test
        @DisplayName("getUrl 默认原样返回路径")
        void url() {
            assertEquals("upload/avatar/a.png", helper.getUrl("upload/avatar/a.png", 3600),
                    "本地文件由 Web 容器直接映射，URL 就是相对路径，不存在签名过期");
        }

        @Test
        @DisplayName("download 未实现")
        void downloadUnsupported() {
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.download("upload/a.txt"));
            assertEquals("该平台暂不支持下载文件", exception.getMessage(),
                    "本地平台的下载是「读文件 + 走业务接口」，不适合放在这里实现");
        }

        @Test
        @DisplayName("存储目录的根路径来自配置而不是硬编码")
        void rootFromConfig() throws IOException {
            assertEquals(root.toString() + File.separator, localFileConfig.getLocalAbsoluteDirectory());
            helper.save(abcStream(), "upload/", "a.txt");
            assertTrue(Files.exists(root.resolve("upload/a.txt")));
        }
    }
}
