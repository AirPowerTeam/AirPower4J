package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.curd.CurdService;

/**
 * <h2>测试用服务</h2>
 *
 * <p>实体类型必须写在 {@code extends CurdService} 的第一段泛型里，
 * {@code getFirstParameterizedTypeClass()} 依赖这一点解析出实体类。</p>
 *
 * @author Hamm.cn
 */
public class DemoService extends CurdService<DemoEntity, DemoRepository> {
}
