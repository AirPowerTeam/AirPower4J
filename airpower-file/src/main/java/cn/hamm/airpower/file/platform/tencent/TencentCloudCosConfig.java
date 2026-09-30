package cn.hamm.airpower.file.platform.tencent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>腾讯云 COS 存储配置</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file.tencent")
public class TencentCloudCosConfig {
    /**
     * 腾讯云 SecretId
     */
    private String secretId = "";

    /**
     * 腾讯云 SecretKey
     */
    private String secretKey = "";

    /**
     * 腾讯云 Bucket
     */
    private String bucketName = "";

    /**
     * 腾讯云地域
     */
    private String region = "ap-shanghai";
}
