package cn.hamm.airpower.curd.config;

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
     * 导出分页大小
     */
    private Integer exportPageSize = 5000;

    /**
     * 导出文件路径
     */
    private String exportPath = "";
}
