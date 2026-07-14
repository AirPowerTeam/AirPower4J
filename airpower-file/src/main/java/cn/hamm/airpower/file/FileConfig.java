package cn.hamm.airpower.file;

import cn.hamm.airpower.core.FileUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.core.FileUtil.FILE_SCALE;

/**
 * <h1>文件配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file")
public class FileConfig {
    /**
     * 上传文件最大大小
     */
    private long uploadMaxSize = FILE_SCALE * FILE_SCALE * 10;

    /**
     * 上传平台
     */
    private FilePlatform filePlatform = FilePlatform.LOCAL;

    /**
     * 上传文件目录
     */
    private String localAbsoluteDirectory = "/home/static/";

    /**
     * 上传文件目录
     */
    private String uploadDirectory = "upload";

    /**
     * 阿里云 AccessKeyId
     */
    private String aliyunAccessKeyId = "";

    /**
     * 阿里云 AccessKeySecret
     */
    private String aliyunAccessKeySecret = "";

    /**
     * 阿里云 负载地址
     */
    private String aliyunEndPoint = "oss-cn-hangzhou.aliyuncs.com";

    /**
     * 阿里云 Bucket
     */
    private String aliyunBucketName = "airpower";

    /**
     * 腾讯云 SecretKey
     */
    private String tencentSecretKey = "";

    /**
     * 腾讯云 SecretId
     */
    private String tencentSecretId = "";

    /**
     * 腾讯云 Bucket
     */
    private String tencentBucketName = "";

    /**
     * 腾讯云 负载地址
     */
    private String tencentRegion = "ap-shanghai";

    /**
     * 获取文件存储目录
     *
     * @return 文件目录
     */
    public String getLocalAbsoluteDirectory() {
        return FileUtil.formatDirectory(localAbsoluteDirectory);
    }

    /**
     * 获取上传文件目录
     *
     * @return 上传文件目录
     */
    public String getUploadDirectory() {
        return FileUtil.formatDirectory(uploadDirectory);
    }
}
