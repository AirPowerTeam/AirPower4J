package cn.hamm.airpower.curd.query;

import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>分页数据</h1>
 *
 * @param <M> 模型
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class PageData<M extends RootModel<M>> extends RootModel<M> {
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
     */
    public static <M extends RootModel<M>> @NotNull PageData<M> newInstance(org.springframework.data.domain.@NotNull Page<M> page) {
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
