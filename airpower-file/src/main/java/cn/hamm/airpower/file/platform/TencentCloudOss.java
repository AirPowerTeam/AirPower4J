package cn.hamm.airpower.file.platform;

import cn.hamm.airpower.core.DateTimeUtil;
import cn.hamm.airpower.file.FileConfig;
import cn.hamm.airpower.file.FileHelper;
import cn.hamm.airpower.file.IFilePlatform;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;


/**
 * <h1>阿里云OSS</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class TencentCloudOss implements IFilePlatform {
    @Autowired
    private FileConfig fileConfig;

    /**
     * volatile：防止指令重排序
     */
    private volatile COSClient cosClient;

    /**
     * 获取文件 URL
     *
     * @param path 文件路径
     * @return 文件 URL
     */
    @Override
    public String getUrl(String path) {
        String url = getClient().generatePresignedUrl(getBucketName(), path, DateTimeUtil.addDays(7)).toString();
        cosClient.shutdown();
        return url;
    }

    private String getBucketName() {
        String aliyunBucketName = fileConfig.getTencentBucketName();
        PARAM_INVALID.whenEmpty(aliyunBucketName, "请配置腾讯云的 BucketName");
        return aliyunBucketName;
    }

    /**
     * <h1>保存文件</h1>
     */
    @Override
    public void save(@NotNull MultipartFile multipartFile, String directory, String fileName) {
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(getBucketName(), directory + fileName, FileHelper.multipartFileToFile(multipartFile));
            getClient().putObject(putObjectRequest);
        } catch (IOException e) {
            throw new RuntimeException("上传文件失败，" + e.getMessage());
        }
    }

    /**
     * 获取 OSS Client（单例 + 线程安全）
     */
    private COSClient getClient() {
        if (cosClient == null) {
            synchronized (this) {
                if (cosClient == null) {
                    String tencentSecretId = fileConfig.getTencentSecretId();
                    PARAM_INVALID.whenEmpty(tencentSecretId, "请配置腾讯云的 SecretId");

                    String tencentSecretKey = fileConfig.getTencentSecretKey();
                    PARAM_INVALID.whenEmpty(tencentSecretKey, "请配置腾讯云的 SecretKey");

                    COSCredentials credentials = new BasicCOSCredentials(tencentSecretId, tencentSecretKey);

                    String tencentRegion = fileConfig.getTencentRegion();
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
     * Spring 容器销毁时关闭 OSS Client
     */
    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }
}