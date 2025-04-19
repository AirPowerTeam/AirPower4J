package cn.hamm.airpower.interfaces;

/**
 * <h1>实体的操作标准接口</h1>
 *
 * @author Hamm.cn
 */
public interface IEntityAction {
    /**
     * ID 必须传入的场景
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

    /**
     * 当查询详情时
     */
    interface WhenGetDetail {
    }

    /**
     * 分页查询
     */
    interface WhenGetPage {
    }

    /**
     * 不分页查询
     */
    interface WhenGetList {
    }
}
