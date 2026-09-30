package cn.hamm.airpower.curd.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>权限控制配置类</h1>
 *
 * @author Hamm.cn
 * @apiNote {@code login} 为 {@code false} 时 {@code authorize} 一定不生效，
 * 即未登录就没有身份可谈授权
 */
@Data
@Accessors(chain = true)
public class Access {
    /**
     * 需要登录
     */
    private boolean login = true;

    /**
     * 需要授权访问
     */
    private boolean authorize = true;
}
