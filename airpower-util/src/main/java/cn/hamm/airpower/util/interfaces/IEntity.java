package cn.hamm.airpower.util.interfaces;

/**
 * <h1>实体接口</h1>
 *
 * @author Hamm.cn
 */
public interface IEntity<E extends IEntity<E>> {
    /**
     * <h2>获取 ID</h2>
     *
     * @return ID
     */
    Long getId();

    /**
     * <h2>设置 ID</h2>
     *
     * @param id ID
     * @return 实体
     */
    E setId(Long id);
}
