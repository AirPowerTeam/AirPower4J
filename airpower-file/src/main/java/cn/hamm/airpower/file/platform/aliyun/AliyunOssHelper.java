package cn.hamm.airpower.file.platform.aliyun;

import cn.hamm.airpower.core.DateTimeUtil;
import cn.hamm.airpower.file.AbstractFilePlatformFactory;
import cn.hamm.airpower.file.FilePlatform;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>阿里云 OSS 存储平台</h1>
 *
 * @author Hamm.cn
 */
@FilePlatform("ALIYUN_OSS")
public class AliyunOssHelper extends AbstractFilePlatformFactory {
    /**
     * 阿里云OSS配置
     */
    @Autowired
    private AliyunOssConfig aliyunOssConfig;

    /**
     * OSS 客户端
     *
     * @apiNote 双重检查锁延迟创建，{@code volatile} 保证其他线程能看到完整构造的实例
     */
    private volatile OSS ossClient;

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
     * 删除文件
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
        String bucketName = aliyunOssConfig.getBucketName();
        PARAM_INVALID.whenEmpty(bucketName, "请配置阿里云的 BucketName");
        return bucketName;
    }

    /**
     * 保存文件
     *
     * @param inputStream 文件输入流
     * @param directory   目录
     * @param fileName    文件名
     * @apiNote 整流一次性读入内存，大文件慎用
     */
    @Override
    public void save(@NotNull InputStream inputStream, String directory, String fileName) {
        try {
            getClient().putObject(getBucketName(), directory + fileName, new ByteArrayInputStream(inputStream.readAllBytes()));
        } catch (IOException e) {
            throw new RuntimeException("上传文件失败，" + e.getMessage());
        }
    }

    /**
     * 获取 OSS 客户端
     *
     * @return OSS 客户端
     * @apiNote 双重检查锁延迟创建为单例；缺少 AccessKeyId / AccessKeySecret / Endpoint 时抛异常
     */
    private OSS getClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    String accessKeyId = aliyunOssConfig.getAccessKeyId();
                    PARAM_INVALID.whenEmpty(accessKeyId, "请配置阿里云的 AccessKeyId");

                    String accessKeySecret = aliyunOssConfig.getAccessKeySecret();
                    PARAM_INVALID.whenEmpty(accessKeySecret, "请配置阿里云的 AccessKeySecret");

                    CredentialsProvider credentialsProvider =
                            new DefaultCredentialProvider(accessKeyId, accessKeySecret);

                    String endpoint = aliyunOssConfig.getEndPoint();
                    PARAM_INVALID.whenEmpty(endpoint, "请配置阿里云的 Endpoint");

                    ossClient = new OSSClientBuilder().build(endpoint, credentialsProvider);
                }
            }
        }
        return ossClient;
    }

    /**
     * Spring 容器销毁时关闭 OSS Client
     */
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}