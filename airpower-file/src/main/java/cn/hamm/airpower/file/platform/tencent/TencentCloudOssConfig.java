package cn.hamm.airpower.file.platform.tencent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>文件配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file.tencent")
public class TencentCloudOssConfig {
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
     * 腾讯云 负载地址
     */
    private String region = "ap-shanghai";
}
