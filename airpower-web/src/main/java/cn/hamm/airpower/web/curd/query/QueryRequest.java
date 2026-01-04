package cn.hamm.airpower.web.curd.query;

import cn.hamm.airpower.util.RootModel;
import cn.hamm.airpower.util.annotation.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>查询请求</h1>
 *
 * @param <M> 数据模型
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Description("查询请求")
public class QueryRequest<M extends RootModel<M>> extends RootModel<QueryRequest<M>> {
    /**
     * 搜索过滤器
     */
    @Description("过滤器")
    private M filter = null;
}
