package cn.hamm.airpower.api.curd;

import cn.hamm.airpower.core.reflect.ReflectUtil;
import cn.hamm.airpower.core.dictionary.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static cn.hamm.airpower.exception.Errors.API_SERVICE_UNSUPPORTED;

/**
 * <h1>增删改查接口枚举</h1>
 *
 * @author Hamm.cn
 * @apiNote 可通过 {@link Extends} 注解显式声明控制器开放哪些接口；
 * 未标注即全部拒绝，详见 {@link Extends}
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
     * 获取控制器的可用 API 列表
     *
     * @param clazz 类
     * @return 可用 API 列表，未标注 {@link Extends} 时返回空列表
     * @apiNote 沿继承链自下而上查找，<b>第一个</b>带 {@link Extends} 的类说了算，
     * 其更上层是否标注一律不再影响结果。整条链都没有标注时返回空列表——
     * 「漏标注」的后果是接口不可用（开发期即暴露），而不是误开放（上线后才发现）
     */
    public static @NotNull List<Curd> getCurdList(@NotNull Class<?> clazz) {
        Class<?> current = clazz;
        // 不含 Object：没有任何业务控制器直接继承它，且 Object 上不可能有 @Extends
        while (Objects.nonNull(current) && !ReflectUtil.isTheRootClass(current)) {
            Extends extend = current.getAnnotation(Extends.class);
            if (Objects.nonNull(extend)) {
                // 找到即返回：子类的声明完整替换父类的，不做合并
                return Arrays.stream(extend.value()).distinct().toList();
            }
            current = current.getSuperclass();
        }
        return List.of();
    }

    /**
     * 检查接口是否可用
     *
     * @param controller 控制器类
     * @param <T>        泛型
     * @apiNote 未在 {@link Extends} 中声明的接口在运行期调用会被拒绝
     */
    public <T extends CurdController<?, ?, ?>> void checkApiAvailable(@NotNull T controller) {
        List<Curd> curdList = getCurdList(controller.getClass());
        API_SERVICE_UNSUPPORTED.when(!curdList.contains(this));
    }
}
