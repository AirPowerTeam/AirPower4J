package cn.hamm.airpower.web.file;

import cn.hamm.airpower.util.FileUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>文件存储配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.file")
public class FileConfig {
    /**
     * 上传文件目录
     */
    private String fileDirectory = "/home/static/";

    /**
     * 导出文件目录
     */
    private String exportDirectory = "export";
    /**
     * 上传文件目录
     */
    private String uploadDirectory = "upload";

    /**
     * 获取文件目录
     *
     * @return 文件目录
     */
    public String getFileDirectory() {
        return FileUtil.formatDirectory(fileDirectory);
    }

    /**
     * 获取导出文件目录
     *
     * @return 导出文件目录
     */
    public String getExportDirectory() {
        return FileUtil.formatDirectory(exportDirectory);
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
