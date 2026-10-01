package cn.hamm.airpower.curd.config;

import cn.hamm.airpower.curd.permission.PermissionUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.airpower.curd.base.CurdEntity.STRING_ID;

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
     * 默认分页条数（客户端未传或传了非法值时使用）
     */
    private int defaultPageSize = 20;

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
        PermissionUtil.setBasePackageName(basePackageName);
        PermissionUtil.setPermissionWithPackage(permissionWithPackage);
    }
}
