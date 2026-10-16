package cn.hamm.airpower.file.platform.aliyun;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.FileConfig;
import cn.hamm.airpower.file.fixture.UnreliableInputStream;
import com.aliyun.oss.OSS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <h2>阿里云 OSS 存储平台单元测试</h2>
 *
 * <p>不连真实 OSS：把 SDK 客户端换成 mock 注入到私有字段，
 * 只验证「平台有没有按配置把请求发对」——Bucket 名、对象 Key、
 * 预签名过期时间这三样最容易在换桶、换目录时配错。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("阿里云 OSS 存储平台单元测试")
class AliyunOssHelperTest {
    /**
     * 测试用桶名
     */
    private static final String BUCKET = "test-bucket";

    /**
     * 被测平台
     */
    private AliyunOssHelper helper;

    /**
     * 被测配置
     */
    private AliyunOssConfig config;

    /**
     * 注入的 mock 客户端
     */
    private OSS ossClient;

    @BeforeEach
    void setUp() throws Exception {
        config = new AliyunOssConfig();
        config.setAccessKeyId("test-access-key-id");
        config.setAccessKeySecret("test-access-key-secret");
        config.setBucketName(BUCKET);

        ossClient = mock(OSS.class);
        when(ossClient.generatePresignedUrl(anyString(), anyString(), any(Date.class)))
                .thenAnswer(invocation -> new URL("https://" + BUCKET + ".oss.aliyuncs.com/"
                        + invocation.getArgument(1) + "?Expires=1&OSSAccessKeyId=test&Signature=abc"));

        helper = new AliyunOssHelper();
        ReflectionTestUtils.setField(helper, "aliyunOssConfig", config);
        ReflectionTestUtils.setField(helper, "fileConfig", new FileConfig());
        ReflectionTestUtils.setField(helper, "ossClient", ossClient);
    }

    /**
     * 构造内容为 {@code abc} 的流
     *
     * @return 输入流
     */
    private static InputStream abcStream() {
        return new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 读取私有客户端字段
     *
     * @param target 平台实例
     * @return 客户端，可能为 null
     */
    private static Object clientOf(AliyunOssHelper target) {
        return ReflectionTestUtils.getField(target, "ossClient");
    }

    /**
     * 构造一个只注入了配置、尚未创建客户端的平台
     *
     * @return 平台实例
     */
    private AliyunOssHelper newHelperWithoutClient() {
        AliyunOssHelper fresh = new AliyunOssHelper();
        ReflectionTestUtils.setField(fresh, "aliyunOssConfig", config);
        ReflectionTestUtils.setField(fresh, "fileConfig", new FileConfig());
        return fresh;
    }

    @Nested
    @DisplayName("保存文件")
    class Save {

        @Test
        @DisplayName("对象 Key 为「目录 + 文件名」")
        void keyIsDirectoryAndFileName() {
            helper.save(abcStream(), "upload/avatar/", "a.png");
            verify(ossClient).putObject(eq(BUCKET), eq("upload/avatar/a.png"), any(InputStream.class));
        }

        @Test
        @DisplayName("整流读入内存后再上传")
        void readsWholeStream() {
            helper.save(abcStream(), "upload/", "a.txt");
            ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
            verify(ossClient).putObject(eq(BUCKET), eq("upload/a.txt"), captor.capture());
            try {
                assertEquals("abc", new String(captor.getValue().readAllBytes(), StandardCharsets.UTF_8),
                        "⚠️ 已记录行为：整个文件先进内存，大文件上传会直接顶到堆上限");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Test
        @DisplayName("流读取失败时抛「上传文件失败」")
        void brokenStreamThrows() {
            InputStream inputStream = new UnreliableInputStream("abc".getBytes(StandardCharsets.UTF_8), 3, 1);
            RuntimeException exception =
                    assertThrows(RuntimeException.class, () -> helper.save(inputStream, "upload/", "a.txt"));
            assertTrue(exception.getMessage().startsWith("上传文件失败，"), exception.getMessage());
        }

        @Test
        @DisplayName("桶名未配置时报「请配置阿里云的 BucketName」")
        void bucketNameRequired() {
            config.setBucketName("");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> helper.save(abcStream(), "upload/", "a.txt"));
            assertEquals(PARAM_INVALID.getCode(), exception.getCode());
            assertEquals("请配置阿里云的 BucketName", exception.getMessage());
        }

        @Test
        @DisplayName("桶名是纯空白时同样视为未配置")
        void blankBucketNameRejected() {
            config.setBucketName("   ");
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.delete("a.txt"));
            assertEquals("请配置阿里云的 BucketName", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("生成访问链接")
    class PresignedUrl {

        @Test
        @DisplayName("返回预签名 URL 字符串")
        void returnsPresignedUrl() {
            String url = helper.getUrl("upload/avatar/a.png", 600);
            assertTrue(url.startsWith("https://" + BUCKET + ".oss.aliyuncs.com/upload/avatar/a.png"), url);
            assertTrue(url.contains("Signature="), url);
        }

        @Test
        @DisplayName("过期时间 = 当前时间 + 指定秒数")
        void expiryIsNowPlusSeconds() {
            long before = System.currentTimeMillis();
            helper.getUrl("a.png", 600);
            long after = System.currentTimeMillis();

            ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
            verify(ossClient).generatePresignedUrl(eq(BUCKET), eq("a.png"), captor.capture());
            long expiry = captor.getValue().getTime();
            assertTrue(expiry >= before + 600_000, "过期时间应不早于 now+600s，实际 " + expiry);
            assertTrue(expiry <= after + 600_000, "过期时间应不晚于 now+600s，实际 " + expiry);
        }

        @Test
        @DisplayName("过期秒数为 0 时链接生成即过期")
        void zeroExpiry() {
            long before = System.currentTimeMillis();
            helper.getUrl("a.png", 0);
            ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
            verify(ossClient).generatePresignedUrl(anyString(), anyString(), captor.capture());
            assertTrue(captor.getValue().getTime() <= before,
                    "⚠️ 已记录行为：second 不做下界校验，传 0 或负数会生成一个当场就失效的链接，且不报错");
        }

        @Test
        @DisplayName("桶名未配置时先报桶名错误")
        void bucketCheckedBeforeSigning() {
            config.setBucketName("");
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.getUrl("a.png", 60));
            assertEquals("请配置阿里云的 BucketName", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("删除文件")
    class Delete {

        @Test
        @DisplayName("按桶名与路径删除")
        void deletesByBucketAndKey() {
            helper.delete("upload/avatar/a.png");
            verify(ossClient).deleteObject(BUCKET, "upload/avatar/a.png");
        }

        @Test
        @DisplayName("路径为空串时不会拦住请求")
        void blankPathIsPassedThrough() {
            helper.delete("");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(ossClient).deleteObject(anyString(), captor.capture());
            assertEquals("", captor.getValue(),
                    "⚠️ 已记录缺陷：删除路径不做任何校验，空路径会被原样发给 OSS；"
                            + "本地平台有校验而云端平台没有，两个平台行为不一致");
        }

        @Test
        @DisplayName("路径带 .. 时不会拦住请求")
        void traversalPathIsPassedThrough() {
            helper.delete("../../other-bucket/secret.txt");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(ossClient).deleteObject(anyString(), captor.capture());
            assertEquals("../../other-bucket/secret.txt", captor.getValue(),
                    "⚠️ 已记录缺陷：云端删除不校验路径，路径完全由调用方负责；"
                            + "一旦上层把用户输入拼进 path，就可能删到别的对象");
        }
    }

    @Nested
    @DisplayName("客户端生命周期")
    class Client {

        @Test
        @DisplayName("容器销毁时关闭客户端")
        void destroyShutsDownClient() {
            helper.destroy();
            verify(ossClient).shutdown();
        }

        @Test
        @DisplayName("客户端尚未创建时销毁不报错")
        void destroyWithoutClient() {
            ReflectionTestUtils.setField(helper, "ossClient", null);
            assertDoesNotThrow(() -> helper.destroy(),
                    "客户端没创建过就销毁是常态（容器启动失败、配置为空等），不能抛异常");
            verify(ossClient, never()).shutdown();
        }

        @Test
        @DisplayName("连续销毁两次会关闭两次")
        void destroyIsNotIdempotent() {
            helper.destroy();
            helper.destroy();
            // ⚠️ 已记录行为：destroy() 不判重也不置空，重复调用会重复 shutdown
            verify(ossClient, times(2)).shutdown();
        }

        @Test
        @DisplayName("缺少 AccessKeyId 时报配置错误")
        void accessKeyIdRequired() {
            AliyunOssHelper fresh = newHelperWithoutClient();
            config.setAccessKeyId("");
            ServiceException exception = assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertEquals("请配置阿里云的 AccessKeyId", exception.getMessage());
        }

        @Test
        @DisplayName("缺少 AccessKeySecret 时报配置错误")
        void accessKeySecretRequired() {
            AliyunOssHelper fresh = newHelperWithoutClient();
            config.setAccessKeySecret("");
            ServiceException exception = assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertEquals("请配置阿里云的 AccessKeySecret", exception.getMessage());
        }

        @Test
        @DisplayName("缺少 Endpoint 时报配置错误")
        void endpointRequired() {
            AliyunOssHelper fresh = newHelperWithoutClient();
            config.setEndPoint("");
            ServiceException exception = assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertEquals("请配置阿里云的 Endpoint", exception.getMessage());
        }

        @Test
        @DisplayName("配置校验失败时不会留下半成品客户端")
        void noHalfBuiltClient() {
            AliyunOssHelper fresh = newHelperWithoutClient();
            config.setAccessKeyId("");
            assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertNull(clientOf(fresh), "配置不全时应停在创建之前，下次补齐配置还能正常初始化");
        }

        @Test
        @DisplayName("客户端字段是 volatile")
        void clientFieldIsVolatile() throws NoSuchFieldException {
            assertTrue(Modifier.isVolatile(AliyunOssHelper.class.getDeclaredField("ossClient").getModifiers()),
                    "双检锁必须配合 volatile，否则别的线程可能拿到还没构造完的客户端");
        }
    }

    @Nested
    @DisplayName("配置与标识")
    class ConfigAndKey {

        @Test
        @DisplayName("平台标识为 ALIYUN_OSS")
        void key() {
            assertEquals("ALIYUN_OSS", helper.getKey());
        }

        @Test
        @DisplayName("默认 endpoint 不带协议前缀")
        void defaultEndpoint() {
            assertEquals("oss-cn-hangzhou.aliyuncs.com", new AliyunOssConfig().getEndPoint(),
                    "endpoint 只写域名；写成 https://xxx 会让 SDK 拼出非法地址");
        }

        @Test
        @DisplayName("未配置时 AccessKey 与 Bucket 均为空串")
        void credentialsDefaultToBlank() {
            AliyunOssConfig blank = new AliyunOssConfig();
            assertEquals("", blank.getAccessKeyId());
            assertEquals("", blank.getAccessKeySecret());
            assertEquals("", blank.getBucketName(),
                    "空串是「未配置」的判定依据，配置类不用 null 是为了让报错信息里出现的也是可读的值");
        }

        @Test
        @DisplayName("注入的客户端被原样持有")
        void holdsInjectedClient() {
            assertSame(ossClient, clientOf(helper));
        }
    }

    @Test
    @DisplayName("并发首次访问只会创建一个客户端")
    void concurrentLazyCreate() throws Exception {
        AliyunOssHelper fresh = newHelperWithoutClient();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        fresh.getUrl("a.png", 60);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发访问应在 30 秒内完成");
        } finally {
            pool.shutdownNow();
        }
        Object client = clientOf(fresh);
        assertNotNull(client, "并发访问后客户端应被创建");
        assertSame(client, clientOf(fresh), "所有线程看到的必须是同一个实例");
        fresh.destroy();
    }
}
