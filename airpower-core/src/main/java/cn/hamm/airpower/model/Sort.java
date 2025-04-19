package cn.hamm.airpower.model;

import cn.hamm.airpower.annotation.Description;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>查询排序</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
@Description("查询排序对象")
public class Sort {
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
