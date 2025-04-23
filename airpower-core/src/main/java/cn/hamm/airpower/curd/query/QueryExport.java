package cn.hamm.airpower.curd.query;

import cn.hamm.airpower.annotation.Description;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * <h1>查询导出结果模型</h1>
 *
 * @author Hamm.cn
 */
@Data
public class QueryExport {
    @NotBlank(message = "文件Code不能为空")
    @Description("文件Code")
    private String fileCode;
}
