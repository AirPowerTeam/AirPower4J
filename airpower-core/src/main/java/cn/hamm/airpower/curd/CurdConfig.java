package cn.hamm.airpower.curd;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static cn.hamm.airpower.curd.CurdEntity.STRING_CREATE_TIME;

/**
 * <h1>全局默认配置文件</h1>
 *
 * @author Hamm.cn
 */
@Component
@Data
@Accessors(chain = true)
@Configuration
@ConfigurationProperties("airpower.curd")
public class CurdConfig {
    /**
     * 默认分页条数
     */
    private int defaultPageSize = 20;

    /**
     * 默认排序字段
     */
    private String defaultSortField = STRING_CREATE_TIME;
}
