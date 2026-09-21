package cn.hamm.airpower.file.platform.aliyun;

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
     * 阿里云 负载地址
     */
    private String endPoint = "oss-cn-hangzhou.aliyuncs.com";

    /**
     * 阿里云 Bucket
     */
    private String bucketName = "";
}
