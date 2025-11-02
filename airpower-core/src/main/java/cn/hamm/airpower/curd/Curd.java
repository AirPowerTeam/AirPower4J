package cn.hamm.airpower.curd;

import cn.hamm.airpower.api.Extends;
import cn.hamm.airpower.dictionary.IDictionary;
import cn.hamm.airpower.reflect.ReflectUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.exception.ServiceError.API_SERVICE_UNSUPPORTED;

/**
 * <h1>增删改查接口枚举</h1>
 *
 * @author Hamm.cn
 * @apiNote 可通过 {@link Extends} 注解为子控制器的类标记需要继承或过滤父类控制器提供的这些方法
 */
@Getter
@AllArgsConstructor
public enum Curd implements IDictionary {
    /**
     * 添加
     */
    Add(1, "添加", "add"),

    /**
     * 删除
     */
    Delete(2, "删除", "delete"),

    /**
     * 禁用
     */
    Disable(3, "禁用", "disable"),

    /**
     * 启用
     */
    Enable(4, "启用", "enable"),

    /**
     * 查询详情
     */
    GetDetail(5, "查询详情", "getDetail"),

    /**
     * 列表查询
     */
    GetList(6, "列表查询", "getList"),

    /**
     * 分页查询
     */
    GetPage(7, "分页查询", "getPage"),

    /**
     * 修改
     */
    Update(8, "修改", "update"),

    /**
     * 创建导出任务
     */
    Export(9, "创建导出任务", "export"),

    /**
     * 查询异步导出结果
     */
    QueryExport(10, "查询异步导出结果", "queryExport");

    private final int key;
    private final String label;

    /**
     * 绑定方法的名称
     */
    private final String methodName;

    /**
     * 获取控制器的可用API列表
     *
     * @param clazz 类
     * @return 可用API列表
     */
    public static @NotNull List<Curd> getCurdList(Class<?> clazz) {
        List<Curd> excludeCurdList = getExcludeCurdList(clazz);
        List<Curd> list = new ArrayList<>(Arrays.stream(Curd.values()).filter(curd -> !excludeCurdList.contains(curd)).toList());
        Extends extend = clazz.getAnnotation(Extends.class);
        if (Objects.nonNull(extend)) {
            list.addAll(Arrays.stream(extend.value()).toList());
        }
        return list;
    }

    /**
     * 获取父类链中被排除的API列表
     *
     * @param clazz 类
     * @return 父类链中被排除的API列表
     */
    private static List<Curd> getExcludeCurdList(Class<?> clazz) {
        return getExcludeCurdList(clazz, new ArrayList<>());
    }

    /**
     * 获取父类链中被排除的API列表
     *
     * @param clazz     类
     * @param existList 排除列表
     * @return 排除列表
     */
    private static List<Curd> getExcludeCurdList(Class<?> clazz, List<Curd> existList) {
        if (ReflectUtil.isTheRootClass(clazz)) {
            return existList;
        }
        // 获取父类链中被排除过的
        Extends extend = clazz.getAnnotation(Extends.class);
        if (Objects.nonNull(extend)) {
            existList.addAll(Arrays.stream(extend.exclude()).toList());
        }
        return getExcludeCurdList(clazz.getSuperclass(), existList);
    }

    /**
     * 检查接口是否可用
     *
     * @param controller 控制器类
     * @param <T>        泛型
     */
    public <T extends CurdController<?, ?, ?>> void checkApiAvailable(@NotNull T controller) {
        List<Curd> curdList = getCurdList(controller.getClass());
        API_SERVICE_UNSUPPORTED.when(!curdList.contains(this));
    }
}
