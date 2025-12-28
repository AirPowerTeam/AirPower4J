package cn.hamm.airpower.tree;

import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.curd.CurdService;
import cn.hamm.airpower.curd.ICurdRepository;
import cn.hamm.airpower.reflect.ReflectUtil;
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
    public static <E extends CurdEntity<E> & ITree<E>> List<E> buildTreeList(List<E> list) {
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
    private static <E extends CurdEntity<E> & ITree<E>> List<E> buildTreeList(@NotNull List<E> list, Long parentId) {
        return list.stream()
                .filter(item -> Objects.equals(parentId, item.getParentId()))
                .peek(item -> item.setChildren(
                        buildTreeList(list, item.getId())
                ))
                .toList();
    }

    /**
     * 获取所有子节点
     *
     * @param service 服务
     * @param list    树结构列表
     * @return 包含所有直接点的树结构列表
     */
    @Contract("_, _ -> param2")
    public static <
            E extends CurdEntity<E> & ITree<E>,
            S extends CurdService<E, R>,
            R extends ICurdRepository<E>
            > @NotNull List<E> getAllChildren(@NotNull S service, @NotNull List<E> list) {
        list.forEach(item -> item.setChildren(getAllChildren(service, findByParentId(service, item.getId()))));
        return list;
    }

    /**
     * 根据父级 ID 获取所有子节点
     *
     * @param service  服务
     * @param parentId 父级 ID
     * @return 子节点列表
     */
    public static <
            E extends CurdEntity<E> & ITree<E>,
            S extends CurdService<E, R>,
            R extends ICurdRepository<E>
            > @NotNull List<E> findByParentId(@NotNull S service, Long parentId) {
        return service.filter(ReflectUtil.newInstance(service.getFirstParameterizedTypeClass()).setParentId(parentId));
    }

    /**
     * 删除前确认是否包含子节点数据
     *
     * @param service 服务
     * @param id      待删除的 ID
     */
    public static <
            E extends CurdEntity<E> & ITree<E>,
            S extends CurdService<E, R>,
            R extends ICurdRepository<E>
            > void ensureNoChildrenBeforeDelete(S service, long id) {
        FORBIDDEN_DELETE.when(!findByParentId(service, id).isEmpty(), "无法删除含有下级的数据，请先删除所有下级！");
    }

    /**
     * 获取指定父ID下的所有子 ID
     *
     * @param parentId 父 ID
     * @param service  服务类
     * @return ID 集合
     */
    public static <
            E extends CurdEntity<E> & ITree<E>,
            S extends CurdService<E, R>,
            R extends ICurdRepository<E>
            > @NotNull Set<Long> getChildrenIdList(
            long parentId,
            @NotNull S service
    ) {
        Set<Long> list = new HashSet<>();
        getChildrenIdList(parentId, service, list);
        return list;
    }

    /**
     * 获取指定父ID下的所有子 ID
     *
     * @param parentId 父 ID
     * @param service  服务类
     * @param list     集合
     */
    public static <
            E extends CurdEntity<E> & ITree<E>,
            S extends CurdService<E, R>,
            R extends ICurdRepository<E>
            > void getChildrenIdList(
            long parentId,
            @NotNull S service,
            @NotNull Set<Long> list
    ) {
        E parent = service.get(parentId);
        list.add(parent.getId());
        List<E> children;
        try {
            Class<E> entityClass = service.getFirstParameterizedTypeClass();
            children = service.filter(entityClass.getConstructor().newInstance().setParentId(parent.getId()));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        children.forEach(child -> getChildrenIdList(child.getId(), service, list));
    }
}
