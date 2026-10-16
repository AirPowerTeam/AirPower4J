package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.curd.Curd;
import cn.hamm.airpower.api.curd.CurdController;
import cn.hamm.airpower.api.curd.Extends;

/**
 * <h2>开放全部接口的测试控制器</h2>
 *
 * <p>新规则下必须<b>显式写全</b>，不再有「不写就全开」的默认。</p>
 *
 * @author Hamm.cn
 */
@Api("demo")
@Extends({
        Curd.Add,
        Curd.Delete,
        Curd.Disable,
        Curd.Enable,
        Curd.GetDetail,
        Curd.GetList,
        Curd.GetPage,
        Curd.Update,
        Curd.Export,
        Curd.QueryExport
})
public class DemoController extends CurdController<DemoEntity, DemoService, DemoRepository> {
}
