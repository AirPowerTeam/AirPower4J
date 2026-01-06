package cn.hamm.airpower.web.curd;

import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Meta;
import cn.hamm.airpower.core.annotation.ReadOnly;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.interfaces.IEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * <h1>实体根类</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@MappedSuperclass
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@DynamicInsert
@DynamicUpdate
@Description("实体")
@Slf4j
@SuppressWarnings("unchecked")
public class CurdEntity<E extends CurdEntity<E>> extends RootModel<E>
        implements Serializable, ICurdAction, IEntity<E> {
    /**
     * 主键字段名
     */
    public static final String STRING_ID = "id";

    /**
     * 创建时间字段名
     */
    public static final String STRING_CREATE_TIME = "createTime";

    /**
     * 修改时间字段名
     */
    public static final String STRING_UPDATE_TIME = "updateTime";

    /**
     * 被禁用字段名
     */
    public static final String STRING_IS_DISABLED = "isDisabled";

    @Description("ID")
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(nullable = false, columnDefinition = "bigint UNSIGNED comment 'ID'")
    @Min(value = 0, message = "ID必须大于{value}")
    @NotNull(groups = {WhenUpdate.class, WhenIdRequired.class}, message = "ID 不能为空")
    @Meta
    private Long id;

    @Description("是否禁用")
    @ReadOnly
    @Column(columnDefinition = "bit(1) default 0 comment '是否禁用'")
    private Boolean isDisabled;

    @Description("创建时间")
    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '创建时间'")
    private Long createTime;

    @Description("修改时间")
    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '修改时间'")
    private Long updateTime;

    @Transient
    @Description("创建时间开始")
    private Long createTimeFrom;

    @Transient
    @Description("创建时间结束")
    private Long createTimeTo;

    @Transient
    @Description("修改时间开始")
    private Long updateTimeFrom;

    @Transient
    @Description("修改时间结束")
    private Long updateTimeTo;

    /**
     * 设置 ID
     *
     * @param id ID
     * @return 实体
     */
    @Override
    public E setId(Long id) {
        this.id = id;
        return (E) this;
    }

    /**
     * 设置是否禁用
     *
     * @param isDisabled 禁用
     * @return 实体
     */
    public E setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
        return (E) this;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     * @return 实体
     */
    public E setCreateTime(Long createTime) {
        this.createTime = createTime;
        return (E) this;
    }

    /**
     * 设置更新时间
     *
     * @param updateTime 更新时间
     * @return 实体
     */
    public E setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
        return (E) this;
    }

    /**
     * 获取简单实体对象
     *
     * @apiNote 舍弃一些基础数据
     */
    public void excludeBaseData() {
        setCreateTime(null)
                .setUpdateTime(null)
                .setIsDisabled(null);
    }

    /**
     * 复制一个只包含 ID 的实体
     *
     * @return 只复制 ID 的实体
     */
    public final @org.jetbrains.annotations.NotNull E copyOnlyId() {
        try {
            E target = (E) getClass().getConstructor().newInstance();
            return target.setId(getId());
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 复制一个新实体
     *
     * @return 返回实例
     */
    public final @org.jetbrains.annotations.NotNull E copy() {
        try {
            E target = (E) getClass().getConstructor().newInstance();
            BeanUtils.copyProperties(this, target);
            return target;
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }
}
