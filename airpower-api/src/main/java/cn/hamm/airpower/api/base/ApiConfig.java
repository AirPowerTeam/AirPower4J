package cn.hamm.airpower.api.base;

import cn.hamm.airpower.api.RequestUtil;
import cn.hamm.airpower.api.permission.PermissionUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import static cn.hamm.airpower.api.curd.CurdEntity.STRING_ID;

/**
 * <h1>全局默认配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.api")
public class ApiConfig {
    /**
     * 输出请求包体日志
     *
     * @apiNote 如配置为 <code>false</code>, 则全局不会输出请求包体日志
     */
    private Boolean requestLog = true;

    /**
     * 输入到日志的请求头列表
     */
    private String[] requestLogHeaders = {HttpHeaders.AUTHORIZATION, HttpHeaders.REFERER, HttpHeaders.USER_AGENT};

    /**
     * 输出响应包体日志
     *
     * @apiNote 如配置为 <code>false</code>, 则全局不会输出响应包体日志
     */
    private Boolean responseLog = true;

    /**
     * {@code AccessToken} 的密钥
     */
    private String accessTokenSecret;

    /**
     * 身份令牌所在的请求参数名或请求头名
     */
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;

    /**
     * 可信代理头
     *
     * @apiNote 留空表示不信任任何代理头，来源 IP 一律取 TCP 对端地址。
     * 该头可被客户端伪造，代理侧必须强制覆盖客户端传入的同名头，否则 IP 白名单、限流都可被绕过
     */
    private String trustProxyHeader = "";

    /**
     * 默认分页条数（客户端未传或传了非法值时使用）
     */
    private int defaultPageSize = 20;

    /**
     * 请求体缓存最大值
     */
    private int maxRequestCacheLimit = 256 * 1024;

    /**
     * 默认排序字段
     */
    private String defaultSortField = STRING_ID;

    /**
     * 最小分页条数，客户端传入的更小值会被抬升到此值
     */
    private int minPageSize = 5;

    /**
     * 最大分页条数，客户端传入的更大值会被压到此值，防止一次拉爆内存
     */
    private int maxPageSize = 1000;

    /**
     * 权限标识的包名前缀，扫描权限时从此包名之后开始截取
     */
    private String basePackageName = "";

    /**
     * 是否用全限定类名派生权限标识（保证跨模块唯一）。
     * 关掉后仅用类名，多模块存在同名控制器时会撞标识
     */
    private Boolean permissionWithPackage = true;


    /**
     * 将配置同步到权限工具类
     *
     * @apiNote 必须在权限扫描之前执行，故放在 {@link PostConstruct} 而不是在使用时读取
     */
    @PostConstruct
    public void apply() {
        RequestUtil.setTrustProxyHeader(trustProxyHeader);
        PermissionUtil.setBasePackageName(basePackageName);
        PermissionUtil.setPermissionWithPackage(permissionWithPackage);
    }
}
