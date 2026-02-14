package cn.hamm.airpower.curd.permission;

import cn.hamm.airpower.core.interfaces.ITree;

/**
 * <h1>权限接口</h1>
 *
 * @author Hamm
 * @see PermissionUtil#scanPermission
 */
public interface IPermission<E extends IPermission<E>> extends ITree<E> {
    /**
     * 获取权限的名称
     *
     * @return 权限名称
     */
    String getName();

    /**
     * 设置权限名称
     *
     * @param name 权限名称
     * @return 权限实体
     */
    E setName(String name);

    /**
     * 获取权限标识
     *
     * @return 权限标识
     */
    String getIdentity();

    /**
     * 设置权限标识
     *
     * @param identity 权限标识
     * @return 权限实体
     */
    E setIdentity(String identity);
}
