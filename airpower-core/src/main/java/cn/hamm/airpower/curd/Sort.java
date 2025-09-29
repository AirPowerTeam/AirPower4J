package cn.hamm.airpower.curd;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.root.RootModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>查询排序</h1>
 *
 * @author Hamm.cn
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
