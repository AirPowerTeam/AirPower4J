package cn.hamm.airpower.file.platform.aliyun;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>阿里云 OSS 存储配置</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file.aliyun")
public class AliyunOssConfig {
    /**
     * 阿里云 AccessKeyId
     */
    private String accessKeyId = "";

    /**
     * 阿里云 AccessKeySecret
     */
    private String accessKeySecret = "";

    /**
     * 阿里云接入地址
     *
     * @apiNote 填写域名即可，不要带 {@code http://} 前缀与 Bucket 名
     */
    private String endPoint = "oss-cn-hangzhou.aliyuncs.com";

    /**
     * 阿里云 Bucket
     */
    private String bucketName = "";
}
