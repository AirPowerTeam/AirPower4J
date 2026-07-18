package cn.hamm.airpower.file.platform;

import cn.hamm.airpower.core.DateTimeUtil;
import cn.hamm.airpower.file.FileConfig;
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

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>阿里云OSS</h1>
 *
 * @author Hamm.cn
 */
@Component
public class AliyunOss implements IFilePlatform {
    @Autowired
    private FileConfig fileConfig;
    /**
     * volatile：防止指令重排序
     */
    private volatile OSS ossClient;

    /**
     * 获取文件 URL
     *
     * @param path 文件路径
     * @return 文件 URL
     */
    @Override
    public String getUrl(String path) {
        String url = getClient().generatePresignedUrl(getBucketName(), path, DateTimeUtil.addDays(7)).toString();
        ossClient.shutdown();
        return url;
    }

    private String getBucketName() {
        String aliyunBucketName = fileConfig.getAliyunBucketName();
        PARAM_INVALID.whenEmpty(aliyunBucketName, "请配置阿里云的 BucketName");
        return aliyunBucketName;
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
                    String accessKeyId = fileConfig.getAliyunAccessKeyId();
                    PARAM_INVALID.whenEmpty(accessKeyId, "请配置阿里云的 AccessKeyId");

                    String accessKeySecret = fileConfig.getAliyunAccessKeySecret();
                    PARAM_INVALID.whenEmpty(accessKeySecret, "请配置阿里云的 AccessKeySecret");

                    CredentialsProvider credentialsProvider =
                            new DefaultCredentialProvider(accessKeyId, accessKeySecret);

                    String endpoint = fileConfig.getAliyunEndPoint();
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