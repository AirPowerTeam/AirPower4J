package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.curd.Curd;
import cn.hamm.airpower.api.curd.CurdController;
import cn.hamm.airpower.api.curd.Extends;

/**
 * <h2>白名单控制器</h2>
 *
 * <p>只声明 {@link Curd#Add} 与 {@link Curd#GetDetail}。
 * 这是白名单真正生效的场景：其余接口在运行期会被拒绝，也不会生成权限。</p>
 *
 * @author Hamm.cn
 */
@Api("readOnly")
@Extends({Curd.Add, Curd.GetDetail})
public class ReadOnlyController extends CurdController<DemoEntity, DemoService, DemoRepository> {
}
