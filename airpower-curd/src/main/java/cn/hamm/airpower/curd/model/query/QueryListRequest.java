package cn.hamm.airpower.curd.model.query;

import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.annotation.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <h1>查询列表请求</h1>
 *
 * @param <M> 数据模型
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Description("查询列表请求")
public class QueryListRequest<M extends RootModel<M>> extends QueryRequest<M> {
    /**
     * 排序对象
     */
    @Description("排序对象")
    private Sort sort;
}
