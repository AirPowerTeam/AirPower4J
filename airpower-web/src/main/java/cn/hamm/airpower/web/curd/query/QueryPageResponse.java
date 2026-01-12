package cn.hamm.airpower.web.curd.query;

import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.web.curd.Page;
import cn.hamm.airpower.web.curd.Sort;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>分页查询响应类</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@Description("分页查询响应类")
public class QueryPageResponse<M extends RootModel<M>> extends PageData<M> {
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
    public static <M extends RootModel<M>> @NotNull QueryPageResponse<M> newInstance(org.springframework.data.domain.@NotNull Page<M> page) {
        QueryPageResponse<M> queryPageResponse = new QueryPageResponse<>();
        queryPageResponse.setList(page.getContent())
                .setTotal(Math.toIntExact(page.getTotalElements()))
                .setPageCount(page.getTotalPages())
                .setPage(new Page()
                        .setPageSize(page.getPageable().getPageSize())
                        .setPageNum(page.getPageable().getPageNumber() + 1)
                );
        return queryPageResponse;
    }

    /**
     * 获取新的实例
     *
     * @param response 响应
     * @param <M>      实体类型
     * @return 响应
     */
    public static <M extends RootModel<M>> @NotNull QueryPageResponse<M> from(@NotNull PageData<M> response) {
        QueryPageResponse<M> queryPageResponse = new QueryPageResponse<>();
        queryPageResponse.setList(response.getList())
                .setTotal(response.getTotal())
                .setPageCount(response.getPageCount())
                .setPage(response.getPage());
        return queryPageResponse;
    }

    /**
     * 获取新的实例
     *
     * @param response 响应
     * @param sort     排序信息
     * @param <M>      实体类型
     * @return 实例
     */
    public static <M extends RootModel<M>> @NotNull QueryPageResponse<M> from(@NotNull PageData<M> response, @NotNull Sort sort) {
        QueryPageResponse<M> queryPageResponse = from(response);
        queryPageResponse.setSort(sort);
        return queryPageResponse;
    }
}
