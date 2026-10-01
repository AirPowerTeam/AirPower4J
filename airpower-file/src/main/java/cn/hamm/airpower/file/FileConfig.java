package cn.hamm.airpower.file;

import cn.hamm.airpower.core.FileUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.core.FileUtil.FILE_SCALE;

/**
 * <h1>文件模块配置</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file")
public class FileConfig {
    /**
     * 上传文件最大大小，单位字节
     *
     * @apiNote 默认 10MB
     */
    private long uploadMaxSize = FILE_SCALE * FILE_SCALE * 10;

    /**
     * 上传文件的根目录（相对目录均挂在其下）
     */
    private String uploadDirectory = "upload";

    /**
     * 默认文件存储平台
     *
     * @apiNote 需与平台类上 {@link FilePlatform#value()} 一致
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
