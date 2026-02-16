package cn.hamm.airpower.curd.config;

import cn.hamm.airpower.core.DateTimeUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>授权相关配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.curd.access")
public class AccessConfig {

    /**
     * 身份令牌有效期
     */
    private long authorizeExpireSecond = DateTimeUtil.SECOND_PER_DAY;
}
