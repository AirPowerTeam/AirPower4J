package cn.hamm.airpower.api.query.page;

import cn.hamm.airpower.api.query.request.QueryListRequest;
import cn.hamm.airpower.core.common.RootModel;
import cn.hamm.airpower.core.reflect.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <h2>分页查询请求</h2>
 *
 * @param <M> 数据模型
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Description("查询分页请求")
public class QueryPageRequest<M extends RootModel> extends QueryListRequest<M> {
    /**
     * 分页信息
     */
    @Description("分页信息")
    private Page page = new Page();
}
