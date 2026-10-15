package cn.hamm.airpower.api.query.page;

import cn.hamm.airpower.core.reflect.Description;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>分页请求参数</h1>
 *
 * @author Hamm.cn
 * @apiNote {@code pageNum} 从 1 开始，转换为 Spring Data 的 {@code Pageable} 时才减 1
 */
@Data
@Accessors(chain = true)
@Description("分页类")
public class Page {
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
