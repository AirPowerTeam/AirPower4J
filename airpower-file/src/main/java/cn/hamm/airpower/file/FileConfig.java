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
     * 上传文件目录
     */
    private String uploadDirectory = "upload";

    /**
     * 默认文件存储平台
     */
    private String defaultPlatform = "LOCAL";

    /**
     * 获取上传文件目录
     *
     * @return 上传文件目录
     */
    public String getUploadDirectory() {
        return FileUtil.formatDirectory(uploadDirectory);
    }
}
