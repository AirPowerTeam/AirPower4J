package cn.hamm.airpower.root;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.annotation.ExcelColumn;
import cn.hamm.airpower.annotation.ReadOnly;
import cn.hamm.airpower.annotation.Search;
import cn.hamm.airpower.config.Constant;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.interfaces.IEntity;
import cn.hamm.airpower.interfaces.IEntityAction;
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

import java.io.Serializable;

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
@Description(Constant.EMPTY_STRING)
@Slf4j
@SuppressWarnings("unchecked")
public class RootEntity<E extends RootEntity<E>> extends RootModel<E>
        implements Serializable, IEntity<E>, IEntityAction {
    @Description("主键ID")
    @Id
    @Search(Search.Mode.EQUALS)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, columnDefinition = "bigint UNSIGNED comment 'ID'")
    @Min(value = 0, message = "ID必须大于{value}")
    @ExcelColumn(ExcelColumn.Type.NUMBER)
    @NotNull(groups = {WhenUpdate.class, WhenIdRequired.class}, message = "ID不能为空")
    private Long id;

    @Description("是否禁用")
    @ReadOnly
    @Search(Search.Mode.EQUALS)
    @Column(columnDefinition = "tinyint UNSIGNED default 0 comment '是否禁用'")
    @ExcelColumn(ExcelColumn.Type.BOOLEAN)
    private Boolean isDisabled;

    @Description("创建时间")
    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '创建时间'")
    @ExcelColumn(ExcelColumn.Type.DATETIME)
    private Long createTime;

    @Description("修改时间")
    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '修改时间'")
    @ExcelColumn(ExcelColumn.Type.DATETIME)
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
     * <h2>设置 {@code ID}</h2>
     *
     * @param id {@code ID}
     * @return 实体
     */
    @Override
    public E setId(Long id) {
        this.id = id;
        return (E) this;
    }

    /**
     * <h2>设置是否禁用</h2>
     *
     * @param isDisabled 禁用
     * @return 实体
     */
    public E setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
        return (E) this;
    }

    /**
     * <h2>设置创建时间</h2>
     *
     * @param createTime 创建时间
     * @return 实体
     */
    public E setCreateTime(Long createTime) {
        this.createTime = createTime;
        return (E) this;
    }

    /**
     * <h2>设置更新时间</h2>
     *
     * @param updateTime 更新时间
     * @return 实体
     */
    public E setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
        return (E) this;
    }

    /**
     * <h2>获取简单实体对象</h2>
     *
     * @apiNote 舍弃一些基础数据
     */
    public void excludeBaseData() {
        setCreateTime(null)
                .setUpdateTime(null)
                .setIsDisabled(null);
    }

    /**
     * <h2>复制一个只包含 {@code ID} 的实体</h2>
     *
     * @return 只复制 {@code ID} 的实体
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
}
