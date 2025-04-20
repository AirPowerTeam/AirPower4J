package cn.hamm.airpower.redis;

import cn.hamm.airpower.datetime.DateTimeUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * <h1>Redis配置文件</h1>
 *
 * @author Hamm.cn
 */
@Component
@Data
@Accessors(chain = true)
@Configuration
@ConfigurationProperties("airpower.redis")
public class RedisConfig {
    /**
     * 缓存过期时间
     */
    private int cacheExpireSecond = DateTimeUtil.SECOND_PER_MINUTE;

    /**
     * 生成文件的目录
     */
    private String saveFilePath = "";
}
