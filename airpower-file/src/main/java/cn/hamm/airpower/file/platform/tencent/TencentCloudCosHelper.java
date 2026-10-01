package cn.hamm.airpower.file.platform.tencent;

import cn.hamm.airpower.core.DateTimeUtil;
import cn.hamm.airpower.file.AbstractFilePlatformFactory;
import cn.hamm.airpower.file.FilePlatform;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>腾讯云 COS 存储平台</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@FilePlatform("TENCENT_CLOUD_COS")
public class TencentCloudCosHelper extends AbstractFilePlatformFactory {
    /**
     * 腾讯云 COS 配置
     */
    @Autowired
    private TencentCloudCosConfig tencentCloudCosConfig;

    /**
     * COS 客户端
     *
     * @apiNote 双重检查锁延迟创建，{@code volatile} 保证其他线程能看到完整构造的实例
     */
    private volatile COSClient cosClient;

    /**
     * 保存文件
     *
     * @param inputStream 文件输入流
     * @param directory   文件目录
     * @param fileName    文件名
     * @apiNote COS 要求上传前就声明 Content-Length，故先取 {@code available()}
     */
    @Override
    public void save(@NotNull InputStream inputStream, String directory, String fileName) {
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(inputStream.available());
            PutObjectRequest putObjectRequest = new PutObjectRequest(getBucketName(), directory + fileName, inputStream, meta);
            getClient().putObject(putObjectRequest);
        } catch (IOException e) {
            throw new RuntimeException("上传文件失败，" + e.getMessage());
        }
    }

    /**
     * 获取文件 URL
     *
     * @param path   文件路径
     * @param second 过期时间（秒）
     * @return 预签名的文件 URL
     * @apiNote 私有 Bucket 下 URL 必须带签名，{@code second} 到期后链接即失效
     */
    @Override
    public String getUrl(String path, int second) {
        return getClient().generatePresignedUrl(getBucketName(), path,
                DateTimeUtil.addSeconds(new Date(), second)
        ).toString();
    }

    /**
     * 从文件平台删除文件
     *
     * @param path 文件路径
     */
    @Override
    public void delete(String path) {
        getClient().deleteObject(getBucketName(), path);
    }

    /**
     * 获取 Bucket 名称
     *
     * @return Bucket 名称
     * @apiNote 未配置时抛异常
     */
    private String getBucketName() {
        String bucketName = tencentCloudCosConfig.getBucketName();
        PARAM_INVALID.whenEmpty(bucketName, "请配置腾讯云的 BucketName");
        return bucketName;
    }

    /**
     * 获取 COS 客户端
     *
     * @return COS 客户端
     * @apiNote 双重检查锁延迟创建为单例；缺少 SecretId / SecretKey / Region 时抛异常
     */
    private COSClient getClient() {
        if (cosClient == null) {
            synchronized (this) {
                if (cosClient == null) {
                    String tencentSecretId = tencentCloudCosConfig.getSecretId();
                    PARAM_INVALID.whenEmpty(tencentSecretId, "请配置腾讯云的 SecretId");

                    String tencentSecretKey = tencentCloudCosConfig.getSecretKey();
                    PARAM_INVALID.whenEmpty(tencentSecretKey, "请配置腾讯云的 SecretKey");

                    COSCredentials credentials = new BasicCOSCredentials(tencentSecretId, tencentSecretKey);

                    String tencentRegion = tencentCloudCosConfig.getRegion();
                    PARAM_INVALID.whenEmpty(tencentRegion, "请配置腾讯云的 Region");
                    Region region = new Region(tencentRegion);
                    ClientConfig clientConfig = new ClientConfig(region);
                    cosClient = new COSClient(credentials, clientConfig);
                }
            }
        }
        return cosClient;
    }

    /**
     * Spring 容器销毁时关闭 COS Client
     */
    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }
}