package cn.hamm.airpower.file.platform.local;

import cn.hamm.airpower.core.FileUtil;
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
@ConfigurationProperties("airpower.file.local")
public class LocalFileConfig {
    /**
     * 上传文件目录
     */
    private String localAbsoluteDirectory = "/home/static/";

    /**
     * 获取文件存储目录
     *
     * @return 文件目录
     */
    public String getLocalAbsoluteDirectory() {
        return FileUtil.formatDirectory(localAbsoluteDirectory);
    }
}
