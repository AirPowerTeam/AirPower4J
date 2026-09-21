package cn.hamm.airpower.file.platform.aliyun;

import cn.hamm.airpower.core.DateTimeUtil;
import cn.hamm.airpower.file.IFilePlatform;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>阿里云OSS</h1>
 *
 * @author Hamm.cn
 */
@Component
public class AliyunOss implements IFilePlatform {
    /**
     * 阿里云OSS配置
     */
    @Autowired
    private AliyunOssConfig aliyunOssConfig;

    /**
     * volatile：防止指令重排序
     */
    private volatile OSS ossClient;

    /**
     * 获取文件 URL
     *
     * @param path   文件路径
     * @param second 过期时间（秒）
     * @return 文件 URL
     */
    @Override
    public String getUrl(String path, int second) {
        return getClient().generatePresignedUrl(getBucketName(), path,
                DateTimeUtil.addSeconds(new Date(), second)
        ).toString();
    }

    /**
     * 获取 BucketName
     */
    private String getBucketName() {
        String bucketName = aliyunOssConfig.getBucketName();
        PARAM_INVALID.whenEmpty(bucketName, "请配置阿里云的 BucketName");
        return bucketName;
    }

    /**
     * <h1>保存文件</h1>
     */
    @Override
    public void save(@NotNull MultipartFile multipartFile, String directory, String fileName) {
        try {
            getClient().putObject(getBucketName(), directory + fileName, new ByteArrayInputStream(multipartFile.getInputStream().readAllBytes()));
        } catch (IOException e) {
            throw new RuntimeException("上传文件失败，" + e.getMessage());
        }
    }

    /**
     * 获取 OSS Client（单例 + 线程安全）
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

    @Override
    public String getKey() {
        return "ALIYUN_OSS";
    }
}