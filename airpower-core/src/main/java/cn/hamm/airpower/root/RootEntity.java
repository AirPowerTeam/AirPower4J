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

import static cn.hamm.airpower.annotation.ExcelColumn.Type.*;
import static cn.hamm.airpower.annotation.Search.Mode.EQUALS;
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
@Description(Constant.STRING_EMPTY)
@Slf4j
@SuppressWarnings("unchecked")
public class RootEntity<E extends RootEntity<E>> extends RootModel<E>
        implements Serializable, IEntity<E>, IEntityAction {
    /**
     * <h3>主键 {@code ID} 字段名</h3>
     */
    public static final String STRING_ID = "id";

    /**
     * <h3>创建时间字段名 {@code createTime}</h3>
     */
    public static final String STRING_CREATE_TIME = "createTime";

    /**
     * <h3>修改时间字段名 {@code updateTime}</h3>
     */
    public static final String STRING_UPDATE_TIME = "updateTime";

    @Description("主键ID")
    @Id
    @Search(EQUALS)
    @GeneratedValue(strategy = IDENTITY)
    @Column(nullable = false, columnDefinition = "bigint UNSIGNED comment 'ID'")
    @Min(value = 0, message = "ID必须大于{value}")
    @ExcelColumn(NUMBER)
    @NotNull(groups = {WhenUpdate.class, WhenIdRequired.class}, message = "ID不能为空")
    private Long id;

    @Description("是否禁用")
    @ReadOnly
    @Search(EQUALS)
    @Column(columnDefinition = "tinyint UNSIGNED default 0 comment '是否禁用'")
    @ExcelColumn(BOOLEAN)
    private Boolean isDisabled;

    @Description("创建时间")
    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '创建时间'")
    @ExcelColumn(DATETIME)
    private Long createTime;

    @Description("修改时间")
    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '修改时间'")
    @ExcelColumn(DATETIME)
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
     * <h3>设置 {@code ID}</h3>
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
     * <h3>设置是否禁用</h3>
     *
     * @param isDisabled 禁用
     * @return 实体
     */
    public E setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
        return (E) this;
    }

    /**
     * <h3>设置创建时间</h3>
     *
     * @param createTime 创建时间
     * @return 实体
     */
    public E setCreateTime(Long createTime) {
        this.createTime = createTime;
        return (E) this;
    }

    /**
     * <h3>设置更新时间</h3>
     *
     * @param updateTime 更新时间
     * @return 实体
     */
    public E setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
        return (E) this;
    }

    /**
     * <h3>获取简单实体对象</h3>
     *
     * @apiNote 舍弃一些基础数据
     */
    public void excludeBaseData() {
        setCreateTime(null)
                .setUpdateTime(null)
                .setIsDisabled(null);
    }

    /**
     * <h3>复制一个只包含 {@code ID} 的实体</h3>
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
