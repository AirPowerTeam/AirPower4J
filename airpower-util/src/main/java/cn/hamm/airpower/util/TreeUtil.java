package cn.hamm.airpower.util;

import cn.hamm.airpower.util.exception.ServiceException;
import cn.hamm.airpower.util.interfaces.IEntity;
import cn.hamm.airpower.util.interfaces.ITree;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * <h1>树结构处理工具类</h1>
 *
 * @author Hamm.cn
 */
public class TreeUtil {
    /**
     * 根节点 ID
     */
    public static final long ROOT_ID = 0L;

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private TreeUtil() {
    }

    /**
     * 生成树结构
     *
     * @param list 原始数据列表
     * @param <E>  泛型
     * @return 树结构数组
     */
    public static <E extends IEntity<E> & ITree<E>> @Unmodifiable @NotNull List<E> buildTreeList(List<E> list) {
        return buildTreeList(list, ROOT_ID);
    }

    /**
     * 生成树结构
     *
     * @param list     原始数据列表
     * @param parentId 父级 ID
     * @param <E>      泛型
     * @return 数结构数组
     */
    private static <E extends IEntity<E> & ITree<E>> @Unmodifiable @NotNull List<E> buildTreeList(@NotNull List<E> list, long parentId) {
        return list.stream()
                .filter(item -> Objects.equals(parentId, item.getParentId()))
                .peek(item -> item.setChildren(
                        buildTreeList(list, item.getId())
                ))
                .toList();
    }

    /**
     * 根据父级 ID 获取所有子节点
     *
     * @param parentId 父级 ID
     * @return 子节点列表
     */
    public static <
            E extends IEntity<E> & ITree<E>
            > @NotNull List<E> findByParentId(@Nullable Long parentId, @NotNull Function<Long, List<E>> function) {
        if (Objects.isNull(parentId)) {
            parentId = ROOT_ID;
        }
        List<E> apply = function.apply(parentId);
        if (Objects.isNull(apply)) {
            return List.of();
        }
        return apply;
    }

    /**
     * 删除前确认是否包含子节点数据
     *
     * @param id       待删除的 ID
     * @param function 获取子节点的函数
     */
    public static <
            E extends IEntity<E> & ITree<E>
            > void ensureNoChildrenBeforeDelete(long id, @NotNull Function<Long, List<E>> function) {
        List<E> apply = function.apply(id);
        if (Objects.isNull(apply)) {
            return;
        }
        if (!apply.isEmpty()) {
            throw new ServiceException("无法删除含有下级的数据，请先删除所有下级！");
        }
    }

    /**
     * 获取指定父ID下的所有子 ID
     *
     * @param parentId 父 ID
     */
    public static <
            E extends IEntity<E> & ITree<E>
            > @NotNull Set<Long> getChildrenIdList(
            long parentId,
            @NotNull Function<Long, List<E>> function
    ) {
        Set<Long> list = new HashSet<>();
        List<E> children = function.apply(parentId);
        if (Objects.isNull(children)) {
            children = List.of();
        }
        children.stream().map(IEntity::getId).forEach(id -> {
            list.add(id);
            list.addAll(getChildrenIdList(id, function));
        });
        return list;
    }
}
