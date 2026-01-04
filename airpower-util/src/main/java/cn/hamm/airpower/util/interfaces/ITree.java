package cn.hamm.airpower.util.interfaces;

import java.util.List;

/**
 * <h1>标准树接口</h1>
 *
 * @author Hamm.cn
 */
public interface ITree<E extends ITree<E>> extends IEntity<E> {
    /**
     * 获取树的父级 ID
     *
     * @return 父级 ID
     */
    Long getParentId();

    /**
     * 设置父级 ID
     *
     * @param parentId 设置父级 ID
     * @return 树实体
     */
    E setParentId(Long parentId);

    /**
     * 获取树的子集列表
     *
     * @return 树的子集
     */
    List<E> getChildren();

    /**
     * 设置树的子集列表
     *
     * @param children 子集
     * @return 树实体
     */
    @SuppressWarnings("UnusedReturnValue")
    E setChildren(List<E> children);
}
