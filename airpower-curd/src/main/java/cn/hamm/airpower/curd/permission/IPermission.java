package cn.hamm.airpower.curd.permission;

import cn.hamm.airpower.core.interfaces.ITree;

/**
 * <h1>权限实体接口</h1>
 *
 * @param <E> 权限实体类型
 * @author Hamm
 * @apiNote 权限为两级结构：一级是控制器，二级是控制器方法
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
     * @apiNote 形如 {@code user:query}，前段由类的全限定名派生，全局唯一
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
