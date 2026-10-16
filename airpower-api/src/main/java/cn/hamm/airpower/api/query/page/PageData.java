package cn.hamm.airpower.api.query.page;

import cn.hamm.airpower.core.common.RootModel;
import cn.hamm.airpower.core.reflect.Description;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <h2>分页数据</h2>
 *
 * @param <M> 模型
 * @author Hamm.cn
 * @apiNote 对外页码从 1 开始，与请求参数保持一致
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Accessors(chain = true)
public class PageData<M extends RootModel> extends RootModel {
    /**
     * 总数量
     */
    @Description("总数量")
    private int total = 0;

    /**
     * 总页数
     */
    @Description("总页数")
    private int pageCount = 0;

    /**
     * 数据信息
     */
    @Description("数据列表")
    private List<M> list = new ArrayList<>();

    /**
     * 分页信息
     */
    @Description("分页信息")
    private Page page = new Page();

    /**
     * 获取新的实例
     *
     * @param page Spring 分页数据
     * @param <M>  实体类型
     * @return 实例
     * @apiNote Spring Data 的页码从 0 开始，这里 {@code +1} 转成对外的 1 开始；
     * 总数超过 {@code int} 范围会抛 {@link ArithmeticException}
     */
    public static <M extends RootModel> @NotNull PageData<M> newInstance(org.springframework.data.domain.@NotNull Page<M> page) {
        PageData<M> pageData = new PageData<>();
        pageData.setList(page.getContent())
                .setTotal(Math.toIntExact(page.getTotalElements()))
                .setPageCount(page.getTotalPages())
                .setPage(new Page()
                        .setPageSize(page.getPageable().getPageSize())
                        .setPageNum(page.getPageable().getPageNumber() + 1)
                );
        return pageData;
    }
}
