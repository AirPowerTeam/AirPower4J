package cn.hamm.airpower.api.access;

import cn.hamm.airpower.core.datetime.DateTimeUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h2>授权相关配置文件</h2>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.curd.access")
public class AccessConfig {

    /**
     * 身份令牌有效期，单位：秒。默认一天
     */
    private long authorizeExpireSecond = DateTimeUtil.SECOND_PER_DAY;
}
