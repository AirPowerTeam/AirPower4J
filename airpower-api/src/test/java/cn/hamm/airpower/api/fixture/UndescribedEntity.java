package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.curd.CurdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <h2>没有 {@code @Description} 的测试实体</h2>
 *
 * <p>{@link ReflectUtil#getDescription} 会沿继承链向上找，
 * 所以本类拿到的是 {@code CurdEntity} 上的 {@code @Description("实体")}，
 * 而不是自己的简单类名。</p>
 *
 * @author Hamm.cn
 */
@Entity
@Table(name = "undescribed")
@Getter
@Setter
@Accessors(chain = true)
public class UndescribedEntity extends CurdEntity<UndescribedEntity> {

    /**
     * 普通列
     */
    @Column(columnDefinition = "varchar(64) comment '名称'")
    private String name;
}
