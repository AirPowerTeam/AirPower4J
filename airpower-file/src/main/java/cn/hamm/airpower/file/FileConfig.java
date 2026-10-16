package cn.hamm.airpower.file;

import cn.hamm.airpower.core.file.FileUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.core.file.FileUtil.FILE_SCALE;
import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

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
     * @apiNote 一定以分隔符结尾；配置为空时直接报错，
     * 否则 {@code formatDirectory("")} 会得到文件系统根目录，文件直接落到根下
     */
    public String getUploadDirectory() {
        PARAM_INVALID.whenEmpty(uploadDirectory, "请配置上传文件的根目录");
        return FileUtil.formatDirectory(uploadDirectory);
    }
}
