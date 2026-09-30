package cn.hamm.airpower.open;

import cn.hamm.airpower.core.RootModel;

/**
 * <h1>Open API 业务数据模型基类</h1>
 * {@link OpenRequest#parse} 反序列化后的业务对象应继承此类。
 *
 * @author Hamm.cn
 *
 * @param <M> 模型自身类型
 */
public class OpenBaseModel<M extends OpenBaseModel<M>> extends RootModel<M> {

}
