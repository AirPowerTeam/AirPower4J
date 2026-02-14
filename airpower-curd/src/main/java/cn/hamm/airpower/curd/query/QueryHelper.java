package cn.hamm.airpower.curd.query;

import cn.hamm.airpower.core.ReflectUtil;
import cn.hamm.airpower.core.constant.Constant;
import cn.hamm.airpower.curd.CurdConfig;
import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.curd.Page;
import cn.hamm.airpower.curd.Sort;
import cn.hamm.airpower.curd.annotation.Search;
import cn.hamm.airpower.curd.annotation.SearchEmpty;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.criteria.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.data.domain.Sort.by;

/**
 * <h1>查询帮助类</h1>
 *
 * @author Hamm.cn
 */
@Service
public class QueryHelper {
    @Autowired
    private CurdConfig curdConfig;

    /**
     * 获取非空的分页对象
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @NotNull
    public final Page requirePageNonNull(Page page) {
        page = Objects.requireNonNullElse(page, new Page());
        if (Objects.isNull(page.getPageSize()) || page.getPageSize() <= 0) {
            page.setPageSize(curdConfig.getDefaultPageSize());
        }
        if (Objects.isNull(page.getPageNum()) || page.getPageNum() <= 0) {
            page.setPageNum(1);
        }
        return page;
    }

    /**
     * 创建分页对象
     *
     * @param page 分页对象
     * @param sort 排序对象
     * @return Spring 分页对象
     */
    @NotNull
    public Pageable createPageable(Page page, org.springframework.data.domain.Sort sort) {
        page = requirePageNonNull(page);
        int pageNumber = Math.max(0, page.getPageNum() - 1);
        int pageSize = Math.max(1, page.getPageSize());
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    /**
     * 获取非空的排序对象
     *
     * @param sort 排序对象
     * @return 排序对象
     */
    @NotNull
    public Sort requireSortNonNull(Sort sort) {
        sort = Objects.requireNonNullElse(sort, new Sort());
        if (!StringUtils.hasText(sort.getField())) {
            sort.setField(curdConfig.getDefaultSortField());
        }
        if (!Sort.ASC.equalsIgnoreCase(sort.getDirection())) {
            sort.setDirection(Sort.DESC);
        } else {
            sort.setDirection(Sort.ASC);
        }
        return sort;
    }

    /**
     * 创建排序对象
     *
     * @param sort 排序对象
     * @return Sort {@code Spring} 的排序对象
     */
    @NotNull
    public org.springframework.data.domain.Sort createSort(Sort sort) {
        sort = requireSortNonNull(sort);
        org.springframework.data.domain.Sort result;
        if (Sort.ASC.equalsIgnoreCase(sort.getDirection())) {
            // 如果明确是 ASC
            result = by(asc(sort.getField()));
        } else {
            // 否则默认 DESC
            result = by(desc(sort.getField()));
        }
        if (Constant.ID.equals(sort.getField())) {
            // 如果指定的是 ID，后续排序已无意义
            return result;
        }
        if (!CurdEntity.STRING_CREATE_TIME.equals(sort.getField())) {
            // 如果非创建时间排序，则自动追加一个创建时间排序
            result.and(by(desc(CurdEntity.STRING_CREATE_TIME)));
        }
        // 继续追加一个 ID 排序，解决创建时间相同的记录排序
        result.and(by(desc(Constant.ID)));
        return result;
    }


    /**
     * 创建排序对象
     *
     * @return Sort {@code Spring} 的排序对象
     */
    @NotNull
    public org.springframework.data.domain.Sort createSort() {
        return createSort(null);
    }

    /**
     * 获取查询条件列表
     *
     * @param root    {@code model}
     * @param builder {@code builder}
     * @param search  搜索实体
     * @param isEqual 是否强匹配
     * @return 搜索条件
     */
    @NotNull
    public List<Predicate> getPredicateList(
            @NotNull From<?, ?> root,
            @NotNull CriteriaBuilder builder,
            @NotNull Object search,
            boolean isEqual
    ) {
        List<Field> fields = ReflectUtil.getFieldList(search.getClass());
        List<Predicate> predicateList = new ArrayList<>();
        fields.forEach(field -> {
            Object fieldValue = ReflectUtil.getFieldValue(search, field);
            if (Objects.isNull(fieldValue)) {
                // 没有传入查询值 空字符串 跳过
                return;
            }
            SearchEmpty searchEmpty = ReflectUtil.getAnnotation(SearchEmpty.class, field);
            if (!StringUtils.hasText(fieldValue.toString())) {
                if (Objects.isNull(searchEmpty)) {
                    // 没有标记查询空字符串
                    return;
                }
                // 标记了 但不查询空字符串
                if (!searchEmpty.value()) {
                    return;
                }
            }
            OneToMany oneToMany = ReflectUtil.getAnnotation(OneToMany.class, field);
            if (Objects.nonNull(oneToMany)) {
                // 一对多的属性 不参与搜索
                return;
            }
            ManyToMany manyToMany = ReflectUtil.getAnnotation(ManyToMany.class, field);
            if (Objects.nonNull(manyToMany)) {
                // 多对多的属性 不参与搜索
                return;
            }
            Transient transientAnnotation = ReflectUtil.getAnnotation(Transient.class, field);
            if (Objects.nonNull(transientAnnotation)) {
                // 非数据库字段 不参与搜索
                return;
            }
            ManyToOne manyToOne = ReflectUtil.getAnnotation(ManyToOne.class, field);
            if (Objects.nonNull(manyToOne)) {
                // 标记了多对一注解 则直接认为是 Join 查询
                Join<?, ?> payload = root.join(field.getName(), JoinType.INNER);
                predicateList.addAll(getPredicateList(payload, builder, fieldValue, isEqual));
                return;
            }
            if (isEqual) {
                // 要求全强匹配
                predicateList.add(builder.equal(root.get(field.getName()), fieldValue));
                return;
            }

            Search searchAnnotation = ReflectUtil.getAnnotation(Search.class, field);
            // 没有标记搜索 则强匹配
            if (Objects.nonNull(searchAnnotation)) {
                // 标记了搜索 则模糊搜索
                if (searchAnnotation.fullLike()) {
                    predicateList.add(builder.like(root.get(field.getName()),
                            "%" + fieldValue + "%"));
                    return;
                }
                predicateList.add(builder.like(root.get(field.getName()),
                        fieldValue + "%"));
                return;
            }
            // 最后兜底还是强匹配
            predicateList.add(builder.equal(root.get(field.getName()), fieldValue));
        });
        return predicateList;
    }
}
