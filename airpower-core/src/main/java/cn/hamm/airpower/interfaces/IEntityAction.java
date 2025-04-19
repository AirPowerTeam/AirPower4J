package cn.hamm.airpower.interfaces;

/**
 * <h1>实体的操作标准接口</h1>
 *
 * @author Hamm.cn
 */
public interface IEntityAction {
    /**
     * <h3>{@code ID} 必须传入的场景</h3>
     */
    interface WhenIdRequired {
    }

    /**
     * <h3>当添加时</h3>
     */
    interface WhenAdd {
    }

    /**
     * <h3>当更新时</h3>
     */
    interface WhenUpdate {
    }

    /**
     * <h3>当查询详情时</h3>
     */
    interface WhenGetDetail {
    }

    /**
     * <h3>分页查询</h3>
     */
    interface WhenGetPage {
    }

    /**
     * <h3>不分页查询</h3>
     */
    interface WhenGetList {
    }
}
