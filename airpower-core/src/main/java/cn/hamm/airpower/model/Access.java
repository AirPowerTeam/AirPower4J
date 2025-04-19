package cn.hamm.airpower.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>权限控制配置类</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class Access {
    /**
     * 需要登录
     */
    private boolean login = false;

    /**
     * 需要授权访问
     */
    private boolean authorize = false;
}
