package cn.hamm.airpower.curd;

import cn.hamm.airpower.api.Extends;
import cn.hamm.airpower.dictionary.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

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
     * 检查接口是否可用
     *
     * @param controller 控制器类
     * @param <T>        泛型
     */
    public <T extends CurdController<?, ?, ?>> void checkApiAvailable(@NotNull T controller) {
        Extends extendsApi = controller.getClass().getAnnotation(Extends.class);
        if (Objects.isNull(extendsApi)) {
            // 没配置
            return;
        }
        List<Curd> whiteList = Arrays.asList(extendsApi.value());
        List<Curd> blackList = Arrays.asList(extendsApi.exclude());
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
