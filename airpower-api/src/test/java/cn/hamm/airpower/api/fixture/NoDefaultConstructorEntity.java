package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.curd.CurdEntity;
import cn.hamm.airpower.core.reflect.Description;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * <h2>没有无参构造的实体</h2>
 *
 * <p>{@link CurdEntity#copy()} / {@code copyOnlyId()} 依赖无参构造，
 * 本类用于钉住「子类漏写无参构造」时的报错路径。</p>
 *
 * @author Hamm.cn
 */
@Entity
@Table(name = "no_default_constructor")
@Description("无默认构造实体")
@Getter
public class NoDefaultConstructorEntity extends CurdEntity<NoDefaultConstructorEntity> {

    /**
     * 普通列
     */
    @Description("名称")
    @Column(columnDefinition = "varchar(64) comment '名称'")
    private String name;

    /**
     * 只有带参构造，反射实例化必然失败
     *
     * @param name 名称
     */
    public NoDefaultConstructorEntity(String name) {
        this.name = name;
    }
}
