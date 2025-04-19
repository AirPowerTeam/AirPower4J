package cn.hamm.airpower.config;

import cn.hamm.airpower.util.DateTimeUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import static cn.hamm.airpower.root.RootEntity.STRING_CREATE_TIME;

/**
 * <h1>全局默认配置文件</h1>
 *
 * @author Hamm.cn
 */
@Component
@Data
@Accessors(chain = true)
@Configuration
@ConfigurationProperties("airpower")
public class ServiceConfig {
    /**
     * {@code AccessToken} 的密钥
     */
    private String accessTokenSecret;

    /**
     * 默认分页条数
     */
    private int defaultPageSize = 20;

    /**
     * 服务全局拦截
     */
    private boolean isServiceRunning = true;

    /**
     * 缓存过期时间
     */
    private int cacheExpireSecond = DateTimeUtil.SECOND_PER_MINUTE;

    /**
     * 默认排序字段
     */
    private String defaultSortField = STRING_CREATE_TIME;

    /**
     * 身份令牌 header 的 key
     */
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;

    /**
     * 身份令牌有效期
     */
    private long authorizeExpireSecond = DateTimeUtil.SECOND_PER_DAY;

    /**
     * 生成文件的目录
     */
    private String saveFilePath = "";
}
