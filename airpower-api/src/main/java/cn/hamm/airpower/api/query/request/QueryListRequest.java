package cn.hamm.airpower.api.query.request;

import cn.hamm.airpower.api.query.sort.Sort;
import cn.hamm.airpower.core.common.RootModel;
import cn.hamm.airpower.core.reflect.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <h1>不分页查询请求</h1>
 *
 * @param <M> 数据模型
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Description("查询列表请求")
public class QueryListRequest<M extends RootModel> extends QueryRequest<M> {
    /**
     * 排序对象
     */
    @Description("排序对象")
    private Sort sort;
}
