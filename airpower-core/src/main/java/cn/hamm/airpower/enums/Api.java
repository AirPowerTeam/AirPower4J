package cn.hamm.airpower.enums;

import cn.hamm.airpower.annotation.Extends;
import cn.hamm.airpower.interfaces.IDictionary;
import cn.hamm.airpower.root.RootEntityController;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.exception.ServiceError.API_SERVICE_UNSUPPORTED;

/**
 * <h1>父类接口枚举</h1>
 *
 * @author Hamm.cn
 * @apiNote 可通过 {@link Extends} 注解为子控制器的类标记需要继承或过滤父类控制器提供的这些方法
 */
@Getter
@AllArgsConstructor
public enum Api implements IDictionary {
    /**
     * <h3>添加</h3>
     */
    Add(1, "添加", "add"),

    /**
     * <h3>删除</h3>
     */
    Delete(2, "删除", "delete"),

    /**
     * <h3>禁用</h3>
     */
    Disable(3, "禁用", "disable"),

    /**
     * <h3>启用</h3>
     */
    Enable(4, "启用", "enable"),

    /**
     * <h3>查询详情</h3>
     */
    GetDetail(5, "查询详情", "getDetail"),

    /**
     * <h3>列表查询</h3>
     */
    GetList(6, "列表查询", "getList"),

    /**
     * <h3>分页查询</h3>
     */
    GetPage(7, "分页查询", "getPage"),

    /**
     * <h3>修改</h3>
     */
    Update(8, "修改", "update"),

    /**
     * <h3>创建导出任务</h3>
     */
    Export(9, "创建导出任务", "export"),

    /**
     * <h3>查询异步导出结果</h3>
     */
    QueryExport(10, "查询异步导出结果", "queryExport");

    private final int key;
    private final String label;

    /**
     * <h3>绑定方法的名称</h3>
     */
    private final String methodName;

    /**
     * <h3>检查接口是否可用</h3>
     *
     * @param controller 控制器类
     * @param <T>        泛型
     */
    public <T extends RootEntityController<?, ?, ?>> void checkApiAvailable(@NotNull T controller) {
        Extends extendsApi = controller.getClass().getAnnotation(Extends.class);
        if (Objects.isNull(extendsApi)) {
            // 没配置
            return;
        }
        List<Api> whiteList = Arrays.asList(extendsApi.value());
        List<Api> blackList = Arrays.asList(extendsApi.exclude());
        if (whiteList.isEmpty() && blackList.isEmpty()) {
            // 配了个寂寞
            return;
        }
        if (whiteList.contains(this)) {
            // 在白名单里
            return;
        }
        if (blackList.isEmpty() || !blackList.contains(this)) {
            // 不在黑名单里
            return;
        }
        API_SERVICE_UNSUPPORTED.show();
    }
}
