package cn.hamm.airpower.curd.base;

/**
 * <h1>实体校验分组接口</h1>
 *
 * @author Hamm.cn
 * @apiNote 内部分组接口用于 {@code @Validated} 的校验分组：同一个实体在新增、修改、
 * 详情等场景下必填的字段不同，通过分组区分，避免维护两套 DTO
 */
public interface ICurdAction {
    /**
     * ID 必须传入的场景（详情、修改、删除、启用、禁用）
     */
    interface WhenIdRequired {
    }

    /**
     * 当添加时
     */
    interface WhenAdd {
    }

    /**
     * 当更新时
     */
    interface WhenUpdate {
    }
}
