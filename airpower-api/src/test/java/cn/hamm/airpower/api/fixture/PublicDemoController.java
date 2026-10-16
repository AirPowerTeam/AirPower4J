package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.curd.CurdController;
import cn.hamm.airpower.api.permission.Permission;

/**
 * <h2>免登录的测试控制器</h2>
 *
 * <p>{@link CurdController} 基类上标了 {@link Permission}（默认登录 + 授权），
 * 子控制器要放开必须显式覆盖。</p>
 *
 * @author Hamm.cn
 */
@Api("public")
@Permission(login = false, authorize = false)
public class PublicDemoController extends CurdController<DemoEntity, DemoService, DemoRepository> {
}
