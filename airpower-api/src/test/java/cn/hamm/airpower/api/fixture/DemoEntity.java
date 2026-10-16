package cn.hamm.airpower.api.fixture;

import cn.hamm.airpower.api.curd.CurdEntity;
import cn.hamm.airpower.api.curd.NullEnable;
import cn.hamm.airpower.api.query.search.Search;
import cn.hamm.airpower.api.query.search.SearchEmpty;
import cn.hamm.airpower.core.desensitize.Desensitize;
import cn.hamm.airpower.core.desensitize.DesensitizeType;
import cn.hamm.airpower.core.reflect.Description;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * <h2>测试用实体</h2>
 *
 * <p>刻意把「影响行为的每一种字段注解」都放齐，这样反射驱动的代码
 * （唯一校验、脱敏、{@code null} 覆盖、搜索条件构建）都能在同一个类上被覆盖到。</p>
 *
 * @author Hamm.cn
 */
@Entity
@Table(name = "demo")
@Description("演示实体")
@Getter
@Setter
@Accessors(chain = true)
public class DemoEntity extends CurdEntity<DemoEntity> {

    /**
     * 唯一列：触发 {@code checkUnique}
     */
    @Description("用户名")
    @Column(unique = true, columnDefinition = "varchar(64) comment '用户名'")
    private String username;

    /**
     * 普通列
     */
    @Description("昵称")
    @Column(columnDefinition = "varchar(64) comment '昵称'")
    private String nickname;

    /**
     * 非唯一列
     */
    @Description("备注")
    @Column(columnDefinition = "varchar(255) comment '备注'")
    private String remark;

    /**
     * 显式允许写入 {@code null} 的列
     */
    @Description("可空备注")
    @NullEnable
    @Column(columnDefinition = "varchar(255) comment '可空备注'")
    private String nullableNote;

    /**
     * 脱敏列：整段替换为符号
     */
    @Description("密码")
    @Desensitize(value = DesensitizeType.CUSTOM, replace = true, symbol = "***")
    @Column(columnDefinition = "varchar(255) comment '密码'")
    private String password;

    /**
     * 脱敏列：包含符号即视为掩码
     */
    @Description("手机号")
    @Desensitize(value = DesensitizeType.MOBILE, replace = false, symbol = "*")
    @Column(columnDefinition = "varchar(32) comment '手机号'")
    private String mobile;

    /**
     * 左模糊搜索列
     */
    @Description("关键字")
    @Search
    @Column(columnDefinition = "varchar(64) comment '关键字'")
    private String keyword;

    /**
     * 全模糊搜索列
     */
    @Description("全文关键字")
    @Search(fullLike = true)
    @Column(columnDefinition = "varchar(64) comment '全文关键字'")
    private String fullKeyword;

    /**
     * 参与「筛空值」判断的列
     */
    @SearchEmpty
    @Description("可空关键字")
    @Column(columnDefinition = "varchar(64) comment '可空关键字'")
    private String emptyProbe;

    /**
     * 非数据库列：任何查询都不应参与
     */
    @Description("非数据库字段")
    @Transient
    private String transientField;

    /**
     * 多对一：查询时会走 {@code INNER JOIN}
     */
    @Description("上级")
    @ManyToOne
    private DemoEntity parent;

    /**
     * 一对多：不参与搜索
     */
    @Description("下级")
    @OneToMany
    private List<DemoEntity> children;
}
