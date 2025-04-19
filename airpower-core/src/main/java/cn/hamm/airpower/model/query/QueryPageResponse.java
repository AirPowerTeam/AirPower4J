package cn.hamm.airpower.model.query;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.model.Page;
import cn.hamm.airpower.model.Sort;
import cn.hamm.airpower.root.RootModel;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>分页查询响应类</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
@Description("分页查询响应类")
public class QueryPageResponse<M extends RootModel<M>> {
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
     * 排序信息
     */
    @Description("排序信息")
    private Sort sort = new Sort();

    /**
     * 获取新的实例
     *
     * @param page Spring 分页数据
     * @param <M>  实体类型
     * @return 实例
     */
    public static <M extends RootModel<M>> QueryPageResponse<M> newInstance(org.springframework.data.domain.@NotNull Page<M> page) {
        return new QueryPageResponse<M>()
                .setList(page.getContent())
                .setTotal(Math.toIntExact(page.getTotalElements()))
                .setPageCount(page.getTotalPages())
                .setPage(new Page()
                        .setPageSize(page.getPageable().getPageSize())
                        .setPageNum(page.getPageable().getPageNumber() + 1)
                );
    }
}
