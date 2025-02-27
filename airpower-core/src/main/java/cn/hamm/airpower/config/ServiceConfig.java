package cn.hamm.airpower.config;

import cn.hamm.airpower.util.DateTimeUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import static cn.hamm.airpower.config.Constant.*;

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
     * <h3>多数据源数据库前缀</h3>
     */
    private String databasePrefix = "tenant_";

    /**
     * <h3>默认服务 {@code ID}</h3>
     */
    private int serviceId = ZERO_INT;

    /**
     * <h3>{@code AccessToken} 的密钥</h3>
     */
    private String accessTokenSecret = AIRPOWER;

    /**
     * <h3>默认分页条数</h3>
     */
    private int defaultPageSize = 20;

    /**
     * <h3>服务全局拦截</h3>
     */
    private boolean isServiceRunning = true;

    /**
     * <h3>是否开启缓存</h3>
     */
    private boolean cache = false;

    /**
     * <h3>缓存过期时间</h3>
     */
    private int cacheExpireSecond = DateTimeUtil.SECOND_PER_MINUTE;

    /**
     * <h3>默认排序字段</h3>
     */
    private String defaultSortField = CREATE_TIME_FIELD;

    /**
     * <h3>身份令牌 {@code header} 的 {@code key}</h3>
     */
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;

    /**
     * <h3>身份令牌有效期</h3>
     */
    private long authorizeExpireSecond = DateTimeUtil.SECOND_PER_DAY;

    /**
     * <h3>多租户的 {@code header} 的 {@code key}</h3>
     */
    private String tenantHeader = "tenant-code";

    /**
     * <h3>导出文件的目录</h3>
     *
     * @apiNote 请 {@code 不要} 使用 {@code /} 结尾
     */
    private String exportFilePath = "";

    /**
     * <h3>是否开启调试模式</h3>
     *
     * @apiNote 调试模式打开，控制台将输出部分错误堆栈信息等
     */
    private boolean debug = true;

    /**
     * <h3>{@code MQTT} 配置</h3>
     */
    private MqttConfig mqtt = new MqttConfig();

    /**
     * <h3>{@code Cookie} 配置</h3>
     */
    private CookieConfig cookie = new CookieConfig();
}
