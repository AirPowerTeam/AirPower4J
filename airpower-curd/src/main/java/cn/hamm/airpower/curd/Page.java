package cn.hamm.airpower.curd;

import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.annotation.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>分页类</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Description("分页类")
public class Page extends RootModel<Page> {
    /**
     * 当前页码
     */
    @Description("当前页码")
    private Integer pageNum = 1;

    /**
     * 分页条数
     */
    @Description("分页条数")
    private Integer pageSize;
}
