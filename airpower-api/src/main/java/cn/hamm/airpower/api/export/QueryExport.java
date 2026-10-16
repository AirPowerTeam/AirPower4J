package cn.hamm.airpower.api.export;

import cn.hamm.airpower.core.common.RootModel;
import cn.hamm.airpower.core.reflect.Description;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <h2>查询导出结果模型</h2>
 *
 * @author Hamm.cn
 * @apiNote {@code fileCode} 是创建导出任务时返回的随机编码，是取回导出结果的唯一凭证
 */
@EqualsAndHashCode(callSuper = false)
@Data
public class QueryExport extends RootModel {
    @NotBlank(message = "文件 Code 不能为空")
    @Description("文件 Code")
    private String fileCode;
}
