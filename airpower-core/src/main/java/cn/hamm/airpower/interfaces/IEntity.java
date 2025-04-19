package cn.hamm.airpower.interfaces;

/**
 * <h1>标准实体接口</h1>
 *
 * @author Hamm.cn
 */
public interface IEntity<E extends IEntity<E>> {
    /**
     * 获取主键 ID
     *
     * @return 主键 ID
     */
    Long getId();

    /**
     * 设置主键 ID
     *
     * @param id 主键 ID
     * @return 实体
     */
    E setId(Long id);
}
