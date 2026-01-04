package cn.hamm.airpower.web.curd.query;

import cn.hamm.airpower.util.RootModel;
import cn.hamm.airpower.util.annotation.Description;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <h1>查询导出结果模型</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QueryExport extends RootModel<QueryExport> {
    @NotBlank(message = "文件 Code 不能为空")
    @Description("文件 Code")
    private String fileCode;
}
