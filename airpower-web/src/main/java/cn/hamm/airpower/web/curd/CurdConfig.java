package cn.hamm.airpower.web.curd;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.web.curd.CurdEntity.STRING_ID;

/**
 * <h1>全局默认配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
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
    private String defaultSortField = STRING_ID;

    /**
     * 使用禁用标识删除(软删除默认实现)
     *
     * @apiNote 如开启此项，则被删除的数据会被标记为已禁用，且在列表查询、详情查询时均被过滤掉。
     */
    private Boolean disableAsDelete = false;
}
