package cn.hamm.airpower.curd;

/**
 * <h1>实体的操作标准接口</h1>
 *
 * @author Hamm.cn
 */
public interface ICurdAction {
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
}
