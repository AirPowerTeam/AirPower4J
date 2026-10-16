package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.curd.Curd;
import cn.hamm.airpower.api.curd.CurdController;
import cn.hamm.airpower.api.curd.Extends;

/**
 * <h2>父子声明冲突的控制器</h2>
 *
 * <p>子类只声明 {@link Curd#Add}，父类声明了全部接口。
 * 用于验证「距离最近的 {@code @Extends} 说了算，<b>不做合并</b>」：
 * 子类并没有因为父类声明得更全就获得删除权限。</p>
 *
 * @author Hamm.cn
 */
@Api("narrow")
@Extends(Curd.Add)
public class NarrowChildController extends DemoController {
}
