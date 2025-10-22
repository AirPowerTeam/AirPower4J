package cn.hamm.airpower.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>文件导出配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.curd.export")
public class ExportConfig {
    /**
     * 生成文件的目录
     */
    private String saveFilePath = "";
}
