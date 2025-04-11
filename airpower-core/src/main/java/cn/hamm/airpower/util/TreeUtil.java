package cn.hamm.airpower.util;

import cn.hamm.airpower.interfaces.ITree;
import cn.hamm.airpower.root.RootEntity;
import cn.hamm.airpower.root.RootRepository;
import cn.hamm.airpower.root.RootService;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hamm.airpower.exception.ServiceError.FORBIDDEN_DELETE;

/**
 * <h1>树结构处理工具类</h1>
 *
 * @author Hamm.cn
 */
public class TreeUtil {
    /**
     * <h3>根节点ID</h3>
     */
    public static final long ROOT_ID = 0L;

    /**
     * <h3>禁止外部实例化</h3>
     */
    @Contract(pure = true)
    private TreeUtil() {
    }

    /**
     * <h3>生成树结构</h3>
     *
     * @param list 原始数据列表
     * @param <E>  泛型
     * @return 树结构数组
     */
    public static <E extends ITree<E>> List<E> buildTreeList(List<E> list) {
        return buildTreeList(list, ROOT_ID);
    }

    /**
     * <h3>生成树结构</h3>
     *
     * @param list     原始数据列表
     * @param parentId 父级 {@code ID}
     * @param <E>      泛型
     * @return 数结构数组
     */
    private static <E extends ITree<E>> List<E> buildTreeList(@NotNull List<E> list, Long parentId) {
        return list.stream()
                .filter(item -> Objects.equals(parentId, item.getParentId()))
                .peek(item -> item.setChildren(
                        buildTreeList(list, item.getId())
                ))
                .toList();
    }

    /**
     * <h3>获取所有子节点</h3>
     *
     * @param service 服务
     * @param list    树结构列表
     * @return 包含所有直接点的树结构列表
     */
    @Contract("_, _ -> param2")
    public static <
            E extends RootEntity<E> & ITree<E>,
            S extends RootService<E, R>,
            R extends RootRepository<E>
            > @NotNull List<E> getAllChildren(@NotNull S service, @NotNull List<E> list) {
        list.forEach(item -> item.setChildren(getAllChildren(service, findByParentId(service, item.getId()))));
        return list;
    }

    /**
     * <h3>根据父级ID获取所有子节点</h3>
     *
     * @param service  服务
     * @param parentId 父级ID
     * @return 子节点列表
     */
    public static <
            E extends RootEntity<E> & ITree<E>,
            S extends RootService<E, R>,
            R extends RootRepository<E>
            > @NotNull List<E> findByParentId(@NotNull S service, Long parentId) {
        return service.filter(ReflectUtil.newInstance(service.getEntityClass()).setParentId(parentId));
    }

    /**
     * <h3>删除前确认是否包含子节点数据</h3>
     *
     * @param service 服务
     * @param id      待删除的ID
     */
    public static <
            E extends RootEntity<E> & ITree<E>,
            S extends RootService<E, R>,
            R extends RootRepository<E>
            > void ensureNoChildrenBeforeDelete(S service, long id) {
        FORBIDDEN_DELETE.when(!findByParentId(service, id).isEmpty(), "无法删除含有下级的数据，请先删除所有下级！");
    }

    /**
     * <h3>获取指定父ID下的所有子ID</h3>
     *
     * @param parentId    父ID
     * @param service     服务类
     * @param entityClass 实体类
     * @param <T>         实体类型
     * @return ID集合
     */
    public static <T extends RootEntity<T> & ITree<T>> @NotNull Set<Long> getChildrenIdList(
            long parentId,
            @NotNull RootService<T, ?> service,
            @NotNull Class<T> entityClass
    ) {
        Set<Long> list = new HashSet<>();
        getChildrenIdList(parentId, service, entityClass, list);
        return list;
    }

    /**
     * <h3>获取指定父ID下的所有子ID</h3>
     *
     * @param parentId    父ID
     * @param service     服务类
     * @param entityClass 实体类型
     * @param list        集合
     * @param <T>         实体类型
     */
    public static <T extends RootEntity<T> & ITree<T>> void getChildrenIdList(
            long parentId,
            @NotNull RootService<T, ?> service,
            @NotNull Class<T> entityClass,
            @NotNull Set<Long> list
    ) {
        T parent = service.get(parentId);
        list.add(parent.getId());
        List<T> children;
        try {
            children = service.filter(entityClass.getConstructor().newInstance().setParentId(parent.getId()));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        children.forEach(child -> getChildrenIdList(child.getId(), service, entityClass, list));
    }
}
