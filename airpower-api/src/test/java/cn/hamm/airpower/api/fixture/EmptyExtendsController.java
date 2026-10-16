package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.curd.Curd;
import cn.hamm.airpower.api.curd.CurdController;
import cn.hamm.airpower.api.curd.Extends;

/**
 * <h2>空声明的测试控制器</h2>
 *
 * <p>{@code @Extends} 留空等价于「不开放任何接口」。
 * 写它的意义在于：这是一个<b>显式</b>的决定，与 {@link UndeclaredController}
 * 那种「忘了写」在运行期表现相同，但意图不同。</p>
 *
 * @author Hamm.cn
 */
@Api("empty")
@Extends
public class EmptyExtendsController extends CurdController<DemoEntity, DemoService, DemoRepository> {
}
