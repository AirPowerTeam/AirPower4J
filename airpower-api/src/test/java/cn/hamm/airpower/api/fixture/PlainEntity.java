package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.curd.CurdEntity;
import cn.hamm.airpower.core.reflect.Description;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * <h2>无唯一列的测试实体</h2>
 *
 * <p>用于验证「没有任何 {@code @Column(unique = true)}」时唯一校验是空转的。</p>
 *
 * @author Hamm.cn
 */
@Entity
@Table(name = "plain")
@Description("普通实体")
@Getter
@Setter
public class PlainEntity extends CurdEntity<PlainEntity> {

    /**
     * 普通列
     */
    @Description("名称")
    @Column(columnDefinition = "varchar(64) comment '名称'")
    private String name;
}
