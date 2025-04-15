package cn.hamm.airpower.interfaces;

import java.util.List;

/**
 * <h1>标准树接口</h1>
 *
 * @author Hamm.cn
 */
public interface ITree<E extends ITree<E>> extends IEntity<E> {
    /**
     * <h3>获取树的父级 {@code ID}</h3>
     *
     * @return 父级 {@code ID}
     */
    Long getParentId();

    /**
     * <h3>设置父级 {@code ID}</h3>
     *
     * @param parentId 设置父级 {@code ID}
     * @return 树实体
     */
    E setParentId(Long parentId);

    /**
     * <h3>获取树的子集列表</h3>
     *
     * @return 树的子集
     */
    List<E> getChildren();

    /**
     * <h3>设置树的子集列表</h3>
     *
     * @param children 子集
     * @return 树实体
     */
    @SuppressWarnings("UnusedReturnValue")
    E setChildren(List<E> children);
}
