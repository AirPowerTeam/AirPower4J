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
     * 导出文件根路径，不配则导出时报错
     *
     * @apiNote 实际文件落在 {@code 根路径/日期/文件名} 下，日期目录由生成时自动推导
     */
    private String exportPath = "";
}
