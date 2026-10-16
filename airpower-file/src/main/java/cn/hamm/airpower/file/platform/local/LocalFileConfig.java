package cn.hamm.airpower.file.platform.local;

import cn.hamm.airpower.core.file.FileUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h2>本地存储配置</h2>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file.local")
public class LocalFileConfig {
    /**
     * 文件存储的绝对目录
     */
    private String localAbsoluteDirectory = "/home/static/";

    /**
     * 获取文件存储目录
     *
     * @return 文件目录
     * @apiNote 一定以分隔符结尾；配置为空时直接报错，否则
     * {@code formatDirectory("")} 会得到文件系统根目录，
     * 存储目录与删除时的越权校验基准都会退化成系统根
     */
    public String getLocalAbsoluteDirectory() {
        PARAM_INVALID.whenEmpty(localAbsoluteDirectory, "请配置本地文件存储目录");
        return FileUtil.formatDirectory(localAbsoluteDirectory);
    }
}
