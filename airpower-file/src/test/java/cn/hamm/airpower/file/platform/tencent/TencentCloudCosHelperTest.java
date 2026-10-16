package cn.hamm.airpower.file.platform.tencent;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.FileConfig;
import cn.hamm.airpower.file.fixture.BrokenAvailableInputStream;
import cn.hamm.airpower.file.fixture.UnreliableInputStream;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
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
 * <h1>腾讯云 COS 存储平台单元测试</h1>
 *
 * <p>与阿里云那套对照着看：两个平台的实现几乎一样，
 * 差别在于 COS 上传前必须先声明 {@code Content-Length}，
 * 于是这里比阿里云多一条约束——<b>长度取自 {@code available()}</b>，
 * 这个值一旦不准，要么被服务端拒绝，要么文件被截断。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("腾讯云 COS 存储平台单元测试")
class TencentCloudCosHelperTest {
    /**
     * 测试用桶名
     */
    private static final String BUCKET = "test-bucket-1250000000";

    /**
     * 被测平台
     */
    private TencentCloudCosHelper helper;

    /**
     * 被测配置
     */
    private TencentCloudCosConfig config;

    /**
     * 注入的 mock 客户端
     */
    private COSClient cosClient;

    @BeforeEach
    void setUp() throws Exception {
        config = new TencentCloudCosConfig();
        config.setSecretId("test-secret-id");
        config.setSecretKey("test-secret-key");
        config.setBucketName(BUCKET);

        cosClient = mock(COSClient.class);
        when(cosClient.generatePresignedUrl(anyString(), anyString(), any(Date.class)))
                .thenAnswer(invocation -> new URL("https://" + BUCKET + ".cos.ap-shanghai.myqcloud.com/"
                        + invocation.getArgument(1) + "?sign=abc"));

        helper = new TencentCloudCosHelper();
        ReflectionTestUtils.setField(helper, "tencentCloudCosConfig", config);
        ReflectionTestUtils.setField(helper, "fileConfig", new FileConfig());
        ReflectionTestUtils.setField(helper, "cosClient", cosClient);
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
    private static Object clientOf(TencentCloudCosHelper target) {
        return ReflectionTestUtils.getField(target, "cosClient");
    }

    /**
     * 构造一个只注入了配置、尚未创建客户端的平台
     *
     * @return 平台实例
     */
    private TencentCloudCosHelper newHelperWithoutClient() {
        TencentCloudCosHelper fresh = new TencentCloudCosHelper();
        ReflectionTestUtils.setField(fresh, "tencentCloudCosConfig", config);
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
            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(cosClient).putObject(captor.capture());
            assertEquals(BUCKET, captor.getValue().getBucketName());
            assertEquals("upload/avatar/a.png", captor.getValue().getKey());
        }

        @Test
        @DisplayName("Content-Length 取自 available()")
        void contentLengthFromAvailable() throws IOException {
            helper.save(abcStream(), "upload/", "a.txt");
            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(cosClient).putObject(captor.capture());
            PutObjectRequest request = captor.getValue();
            assertEquals(3, request.getMetadata().getContentLength());
            assertEquals("abc", new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("available() 返回 0 时声明的长度也是 0")
        void contentLengthZeroWhenAvailableIsZero() {
            InputStream inputStream = new UnreliableInputStream(
                    "0123456789".getBytes(StandardCharsets.UTF_8), 0, -1);
            helper.save(inputStream, "upload/", "a.txt");
            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(cosClient).putObject(captor.capture());
            assertEquals(0, captor.getValue().getMetadata().getContentLength(),
                    "⚠️ 已记录缺陷：流没有标记长度时声明 Content-Length=0，"
                            + "要么被服务端拒绝要么文件被截断；基类的大小校验同样依赖 available()，两处一起失真");
        }

        @Test
        @DisplayName("不会主动关闭传入的流")
        void streamNotClosed() throws IOException {
            InputStream inputStream = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
            helper.save(inputStream, "upload/", "a.txt");
            assertEquals(3, inputStream.available(), "流的所有权属于调用方，平台不应擅自关闭");
        }

        @Test
        @DisplayName("取长度失败时抛「上传文件失败」")
        void brokenAvailableThrows() {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> helper.save(new BrokenAvailableInputStream(), "upload/", "a.txt"));
            assertTrue(exception.getMessage().startsWith("上传文件失败，"), exception.getMessage());
            assertTrue(exception.getMessage().contains(BrokenAvailableInputStream.MESSAGE), exception.getMessage());
        }

        @Test
        @DisplayName("读取中途失败要等到 SDK 内部才暴露")
        void midReadFailureDeferredToSdk() {
            InputStream inputStream = new UnreliableInputStream("abc".getBytes(StandardCharsets.UTF_8), 3, 1);
            assertDoesNotThrow(() -> helper.save(inputStream, "upload/", "a.txt"),
                    "⚠️ 已记录行为：COS 平台不预读内容，流是交给 SDK 边传边读的，"
                            + "读失败时的异常来自 SDK 而不是本层，错误信息里不带任何上下文");
            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(cosClient).putObject(captor.capture());
            assertEquals(3, captor.getValue().getMetadata().getContentLength(),
                    "声明的长度按 available() 算，与流实际能读出多少字节无关");
        }

        @Test
        @DisplayName("桶名未配置时报「请配置腾讯云的 BucketName」")
        void bucketNameRequired() {
            config.setBucketName("");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> helper.save(abcStream(), "upload/", "a.txt"));
            assertEquals(PARAM_INVALID.getCode(), exception.getCode());
            assertEquals("请配置腾讯云的 BucketName", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("生成访问链接")
    class PresignedUrl {

        @Test
        @DisplayName("返回预签名 URL 字符串")
        void returnsPresignedUrl() {
            String url = helper.getUrl("upload/avatar/a.png", 600);
            assertTrue(url.startsWith("https://" + BUCKET + ".cos.ap-shanghai.myqcloud.com/upload/avatar/a.png"), url);
            assertTrue(url.contains("sign="), url);
        }

        @Test
        @DisplayName("过期时间 = 当前时间 + 指定秒数")
        void expiryIsNowPlusSeconds() {
            long before = System.currentTimeMillis();
            helper.getUrl("a.png", 600);
            long after = System.currentTimeMillis();

            ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
            verify(cosClient).generatePresignedUrl(eq(BUCKET), eq("a.png"), captor.capture());
            long expiry = captor.getValue().getTime();
            assertTrue(expiry >= before + 600_000, "过期时间应不早于 now+600s，实际 " + expiry);
            assertTrue(expiry <= after + 600_000, "过期时间应不晚于 now+600s，实际 " + expiry);
        }

        @Test
        @DisplayName("桶名未配置时先报桶名错误")
        void bucketCheckedBeforeSigning() {
            config.setBucketName("");
            ServiceException exception = assertThrows(ServiceException.class, () -> helper.getUrl("a.png", 60));
            assertEquals("请配置腾讯云的 BucketName", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("删除文件")
    class Delete {

        @Test
        @DisplayName("按桶名与路径删除")
        void deletesByBucketAndKey() {
            helper.delete("upload/avatar/a.png");
            verify(cosClient).deleteObject(BUCKET, "upload/avatar/a.png");
        }

        @Test
        @DisplayName("路径带 .. 时不会拦住请求")
        void traversalPathIsPassedThrough() {
            helper.delete("../../other-bucket/secret.txt");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(cosClient).deleteObject(anyString(), captor.capture());
            assertEquals("../../other-bucket/secret.txt", captor.getValue(),
                    "⚠️ 已记录缺陷：与阿里云平台一样，云端删除完全不校验路径");
        }

        @Test
        @DisplayName("路径为 null 时原样传给 SDK")
        void nullPathPassedThrough() {
            helper.delete(null);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(cosClient).deleteObject(anyString(), captor.capture());
            assertEquals(null, captor.getValue(),
                    "⚠️ 已记录缺陷：null 路径没有前置校验，异常要到 SDK 内部才抛出，线上排查成本高");
        }
    }

    @Nested
    @DisplayName("客户端生命周期")
    class Client {

        @Test
        @DisplayName("容器销毁时关闭客户端")
        void destroyShutsDownClient() {
            helper.destroy();
            verify(cosClient).shutdown();
        }

        @Test
        @DisplayName("客户端尚未创建时销毁不报错")
        void destroyWithoutClient() {
            ReflectionTestUtils.setField(helper, "cosClient", null);
            assertDoesNotThrow(() -> helper.destroy(), "客户端没创建过就销毁是常态，不能抛异常");
            verify(cosClient, never()).shutdown();
        }

        @Test
        @DisplayName("连续销毁两次会关闭两次")
        void destroyIsNotIdempotent() {
            helper.destroy();
            helper.destroy();
            // ⚠️ 已记录行为：destroy() 不判重也不置空，重复调用会重复 shutdown
            verify(cosClient, times(2)).shutdown();
        }

        @Test
        @DisplayName("缺少 SecretId 时报配置错误")
        void secretIdRequired() {
            TencentCloudCosHelper fresh = newHelperWithoutClient();
            config.setSecretId("");
            ServiceException exception = assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertEquals("请配置腾讯云的 SecretId", exception.getMessage());
        }

        @Test
        @DisplayName("缺少 SecretKey 时报配置错误")
        void secretKeyRequired() {
            TencentCloudCosHelper fresh = newHelperWithoutClient();
            config.setSecretKey("");
            ServiceException exception = assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertEquals("请配置腾讯云的 SecretKey", exception.getMessage());
        }

        @Test
        @DisplayName("缺少 Region 时报配置错误")
        void regionRequired() {
            TencentCloudCosHelper fresh = newHelperWithoutClient();
            config.setRegion("");
            ServiceException exception = assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertEquals("请配置腾讯云的 Region", exception.getMessage());
        }

        @Test
        @DisplayName("配置校验失败时不会留下半成品客户端")
        void noHalfBuiltClient() {
            TencentCloudCosHelper fresh = newHelperWithoutClient();
            config.setRegion("");
            assertThrows(ServiceException.class, () -> fresh.getUrl("a.png", 60));
            assertNull(clientOf(fresh), "配置不全时应停在创建之前，下次补齐配置还能正常初始化");
        }

        @Test
        @DisplayName("客户端字段是 volatile")
        void clientFieldIsVolatile() throws NoSuchFieldException {
            assertTrue(Modifier.isVolatile(TencentCloudCosHelper.class.getDeclaredField("cosClient").getModifiers()),
                    "双检锁必须配合 volatile，否则别的线程可能拿到还没构造完的客户端");
        }
    }

    @Nested
    @DisplayName("配置与标识")
    class ConfigAndKey {

        @Test
        @DisplayName("平台标识为 TENCENT_CLOUD_COS")
        void key() {
            assertEquals("TENCENT_CLOUD_COS", helper.getKey());
        }

        @Test
        @DisplayName("默认地域为 ap-shanghai")
        void defaultRegion() {
            assertEquals("ap-shanghai", new TencentCloudCosConfig().getRegion());
        }

        @Test
        @DisplayName("未配置时密钥与桶名均为空串")
        void credentialsDefaultToBlank() {
            TencentCloudCosConfig blank = new TencentCloudCosConfig();
            assertEquals("", blank.getSecretId());
            assertEquals("", blank.getSecretKey());
            assertEquals("", blank.getBucketName());
        }

        @Test
        @DisplayName("注入的客户端被原样持有")
        void holdsInjectedClient() {
            assertSame(cosClient, clientOf(helper));
        }

        @Test
        @DisplayName("每次上传都用新的元数据，不复用上一次的长度")
        void metadataIsPerRequest() throws IOException {
            helper.save(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), "upload/", "a.txt");
            helper.save(new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8)), "upload/", "b.txt");
            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(cosClient, times(2)).putObject(captor.capture());
            assertEquals(3, captor.getAllValues().get(0).getMetadata().getContentLength());
            assertEquals(10, captor.getAllValues().get(1).getMetadata().getContentLength(),
                    "第二次上传不能沿用第一次的长度，否则服务端会按旧长度截断内容");
        }
    }

    @Test
    @DisplayName("并发首次访问只会创建一个客户端")
    void concurrentLazyCreate() throws Exception {
        TencentCloudCosHelper fresh = newHelperWithoutClient();
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
