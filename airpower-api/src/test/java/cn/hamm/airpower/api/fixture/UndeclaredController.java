package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.curd.CurdController;

/**
 * <h2>未标注 {@code @Extends} 的测试控制器</h2>
 *
 * <p>刻意<b>不写</b> {@code @Extends}：新规则下一个接口都不开放，也不生成权限。
 * 用于验证「默认拒绝」。</p>
 *
 * @author Hamm.cn
 */
@Api("undeclared")
public class UndeclaredController extends CurdController<DemoEntity, DemoService, DemoRepository> {
}
