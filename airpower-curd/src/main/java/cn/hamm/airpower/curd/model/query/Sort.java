package cn.hamm.airpower.curd.model.query;

import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.annotation.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>查询排序</h1>
 *
 * @author Hamm.cn
 * @apiNote {@code field} 取实体属性名，最终拼进 {@code ORDER BY}；
 * 只认 {@link #ASC}，其他取值一律按 {@link #DESC} 处理
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Description("查询排序对象")
public class Sort extends RootModel<Sort> {
    /**
     * 升序
     */
    public static final String ASC = "asc";

    /**
     * 降序
     */
    public static final String DESC = "desc";

    /**
     * 排序字段
     */
    @Description("排序字段")
    private String field;

    /**
     * 排序方法
     */
    @Description("排序方向")
    private String direction;
}
