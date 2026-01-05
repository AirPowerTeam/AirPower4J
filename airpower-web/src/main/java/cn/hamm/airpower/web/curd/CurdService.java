package cn.hamm.airpower.web.curd;

import cn.hamm.airpower.core.*;
import cn.hamm.airpower.core.constant.Constant;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.web.annotation.NullEnable;
import cn.hamm.airpower.web.annotation.Search;
import cn.hamm.airpower.web.annotation.SearchEmpty;
import cn.hamm.airpower.web.curd.query.PageData;
import cn.hamm.airpower.web.curd.query.QueryListRequest;
import cn.hamm.airpower.web.curd.query.QueryPageRequest;
import cn.hamm.airpower.web.curd.query.QueryPageResponse;
import cn.hamm.airpower.web.export.ExportHelper;
import cn.hamm.airpower.web.helper.TransactionHelper;
import cn.hamm.airpower.web.root.RootService;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static cn.hamm.airpower.web.exception.ServiceError.*;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.data.domain.Sort.by;

/**
 * <h1>实体根服务</h1>
 *
 * @param <E> 实体
 * @param <R> 数据源
 * @author Hamm.cn
 * @see #getList(QueryListRequest) 通用列表查询 <code>getList(QueryListRequest)</code>
 * @see #getPage(QueryPageRequest) 通用分页查询 <code>getPage(QueryPageRequest)</code>
 * @see #filter(CurdEntity) 实体强匹配列表搜索 <code>filter(CurdEntity)</code>
 * @see #query(CurdEntity) 实体模糊匹配列表搜索 <code>query(CurdEntity)</code>
 * @see #query(BiFunction) 列表自定义高阶查询 <code>selectList(BiFunction)</code>
 * @see #query(Page) 分页自定义高阶查询 <code>selectPage(BiFunction)</code>
 * @see #find(CurdEntity, org.springframework.data.domain.Sort, boolean)  私有落地 <code>find(CurdEntity, Sort, boolean)</code>
 * @see #repository 还实现不了？<code>repository</code> 给你，你自己来
 */
@Slf4j
public class CurdService<E extends CurdEntity<E>, R extends ICurdRepository<E>> extends RootService<E> {
    /**
     * 提交的数据不允许为空
     */
    private static final String DATA_REQUIRED = "提交的数据不允许为空";

    /**
     * 数据源
     */
    @Autowired(required = false)
    protected R repository;

    /**
     * 实体管理器
     */
    @PersistenceContext
    protected EntityManager entityManager;

    /**
     * 事务管理器
     */
    @Autowired
    protected TransactionHelper transactionHelper;

    /**
     * CURD 配置
     */
    @Autowired
    private CurdConfig curdConfig;

    /**
     * 添加一条数据 {@code 触发前后置}
     *
     * @param source 原始实体
     * @return 主键 ID
     * @apiNote 如需绕过前后置处理，请使用 {@link #addToDatabase(E)}
     * @see #beforeAdd(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterAdd(long, E)
     * @see #afterSaved(long, E)
     */
    public final long add(@NotNull E source) {
        source = beforeAdd(source);
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);

        // 新增不允许带主键
        source.setId(null);
        long id = addToDatabase(source);
        final E finalSource = source;

        // 新增完毕后的一些后置处理
        TaskUtil.run(
                () -> afterAdd(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
        return id;
    }

    /**
     * 添加到数据库 {@code 不触发前后置}
     *
     * @param source 原始实体
     * @return 添加的主键
     * @see #add(CurdEntity) 触发前后置的添加方法
     */
    public final long addToDatabase(@NotNull E source) {
        PARAM_MISSING.whenNotNull(source.getId(), String.format("添加失败，请不要传入%s的ID!", getEntityDescription()));
        return saveToDatabase(source, false);
    }

    /**
     * 添加并获取
     *
     * @param source 原始实体
     * @return 添加后的实体
     */
    public final @NotNull E addAndGet(@NotNull E source) {
        return get(add(source));
    }

    /**
     * 删除指定的数据
     *
     * @param id 主键
     * @see #beforeDelete(E)
     * @see #afterDelete(long)
     */
    public final void delete(long id) {
        E entity = get(id);
        beforeDelete(entity);
        if (isSoftDelete()) {
            updateToDatabase(getEntityInstance(id).setIsDisabled(true));
            TaskUtil.run(() -> afterDelete(id));
            return;
        }
        repository.deleteById(id);
        TaskUtil.run(() -> afterDelete(id));
    }

    /**
     * 修改一条已经存在的数据 {@code 触发前后置}
     *
     * @param source 修改的实体
     * @apiNote 如需绕过前后置处理，请使用 {@link #updateToDatabase(E)}
     * @see #beforeUpdate(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     */
    public final void update(@NotNull E source) {
        update(source, false);
    }

    /**
     * 修改一条已经存在的数据 {@code 触发前后置}
     *
     * @param source 原始实体
     * @apiNote 如需绕过前后置处理，请使用 {@link #updateToDatabase(E)}
     * @see #beforeUpdate(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     */
    public final void update(@NotNull E source, boolean withNull) {
        long id = source.getId();
        source = beforeUpdate(source);
        updateToDatabase(source, withNull);
        final E finalSource = source;
        TaskUtil.run(
                () -> afterUpdate(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
    }

    /**
     * 加锁更新指定 ID 的数据 {@code 不触发前后置}、{@code 加锁}
     *
     * @param id       主键 ID
     * @param consumer 可消费实体
     */
    public final void updateWithLock(long id, Consumer<E> consumer) {
        transactionHelper.run(() -> {
            E exist = getForUpdate(id);
            consumer.accept(exist);
            updateToDatabase(exist);
        });
    }

    /**
     * 更新到数据库 {@code 不触发前后置}
     *
     * @param source 原始实体
     * @see #update(CurdEntity) 触发前后置的修改方法
     */
    public final void updateToDatabase(@NotNull E source) {
        updateToDatabase(source, false);
    }

    /**
     * 更新到数据库 {@code 不触发前后置}
     *
     * @param source   原始实体
     * @param withNull 是否更新空值
     */
    public final void updateToDatabase(@NotNull E source, boolean withNull) {
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        PARAM_MISSING.whenNull(source.getId(), String.format("修改失败，请传入%s的ID!", getEntityDescription()));
        saveToDatabase(source, withNull);
    }

    /**
     * 启用指定的数据
     *
     * @param id 主键 ID
     * @see #beforeEnable(E)
     * @see #afterEnable(long)
     */
    public final void enable(long id) {
        E entity = get(id);
        beforeEnable(entity);
        updateToDatabase(getEntityInstance(id).setIsDisabled(false));
        TaskUtil.run(() -> afterEnable(id));
    }

    /**
     * 禁用指定的数据
     *
     * @param id 主键 ID
     * @see #beforeDisable(E)
     * @see #afterDisable(long)
     */
    public final void disable(long id) {
        E entity = get(id);
        beforeDisable(entity);
        updateToDatabase(getEntityInstance(id).setIsDisabled(true));
        TaskUtil.run(() -> afterDisable(id));
    }

    /**
     * 不分页查询数据
     *
     * @param queryListRequest 列表请求对象
     * @return List 数据
     * @see #beforeGetList(QueryListRequest)
     * @see #afterGetList(List)
     */
    public final @NotNull List<E> getList(QueryListRequest<E> queryListRequest) {
        queryListRequest = requireQueryRequestNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeGetList(queryListRequest);
        List<E> list = query(queryListRequest.getFilter(), queryListRequest.getSort());
        return afterGetList(list);
    }

    /**
     * 分页查询数据
     *
     * @param queryPageRequest 请求的分页对象
     * @return 分页查询列表
     * @see #beforeGetPage(QueryPageRequest)
     * @see #afterGetPage(QueryPageResponse)
     */
    public final @NotNull QueryPageResponse<E> getPage(QueryPageRequest<E> queryPageRequest) {
        return getPage(queryPageRequest, (list) -> list);
    }

    /**
     * 分页查询数据
     *
     * @param queryPageRequest 请求的分页对象
     * @param after            查询后的方法
     * @return 分页查询列表
     * @see #beforeGetPage(QueryPageRequest)
     * @see #afterGetPage(QueryPageResponse)
     */
    public final @NotNull <RES extends RootModel<RES>> QueryPageResponse<RES> getPage(
            QueryPageRequest<E> queryPageRequest,
            @NotNull Function<List<E>, List<RES>> after
    ) {
        queryPageRequest = requireQueryRequestNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        org.springframework.data.domain.Page<E> pageData = repository.findAll(
                createSpecification(queryPageRequest.getFilter(), false),
                createPageable(queryPageRequest.getPage(),
                        createSort(queryPageRequest.getSort())
                )
        );

        // 组装分页数据
        QueryPageResponse<E> queryPageResponse = QueryPageResponse.newInstance(pageData);
        queryPageResponse.setSort(queryPageRequest.getSort());
        queryPageResponse = afterGetPage(queryPageResponse);

        QueryPageResponse<RES> response = new QueryPageResponse<>();
        response.setPage(queryPageResponse.getPage())
                .setTotal(queryPageResponse.getTotal())
                .setPageCount(queryPageResponse.getPageCount())
                .setList(after.apply(queryPageResponse.getList()))
        ;
        response.setSort(queryPageResponse.getSort());
        return response;
    }

    /**
     * 根据 ID 查询对应的实体
     *
     * @param id 主键
     * @return 实体
     * @see #getMaybeNull(long)
     * @see #getWithEnable(long)
     */
    public final @NotNull E get(long id) {
        return afterGet(getById(id));
    }

    /**
     * 根据主键查询对应的实体
     *
     * @param id 主键
     * @return 实体
     * @apiNote 查不到返回 {@code null}，不抛异常
     */
    public final @Nullable E getMaybeNull(long id) {
        try {
            return get(id);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 加锁获取指定 ID 的数据用户更新
     *
     * @param id 主键 ID
     * @return 加锁后的数据
     * @see #updateWithLock(long, Consumer)
     */
    public final @NotNull E getForUpdate(long id) {
        entityManager.clear();
        E forUpdate = repository.getForUpdateById(id);
        DATA_NOT_FOUND.whenNull(forUpdate, String.format("没有查询到ID为%s的%s", id, getEntityDescription()));
        return forUpdate;
    }

    /**
     * 根据 ID 查询正常启用的实体
     *
     * @param id 主键
     * @return 实体
     * @see #get(long)
     * @see #getMaybeNull(long)
     */
    public final @NotNull E getWithEnable(long id) {
        E entity = get(id);
        FORBIDDEN_DISABLED.when(
                entity.getIsDisabled(),
                String.format(FORBIDDEN_DISABLED.getMessage(), id, getEntityDescription())
        );
        return entity;
    }

    /**
     * 自定义计算查询
     *
     * @param fieldName   字段名称
     * @param function    自定义计算
     * @param resultClass 结果类型
     * @param <FIELD>     结果类型
     * @return 计算结果
     */
    @SuppressWarnings("unused")
    public final <FIELD, RESULT> RESULT selectAndCalculate(
            @NotNull BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate,
            String fieldName,
            @NotNull BiFunction<CriteriaBuilder, Path<FIELD>, Selection<? extends RESULT>> function,
            Class<RESULT> resultClass,
            Class<FIELD> fieldClass
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<RESULT> query = builder.createQuery(resultClass);
        Root<E> root = query.from(getEntityClass());
        Path<FIELD> field = root.get(fieldName);
        query.select(function.apply(builder, field)).where(predicate.apply(root, builder));
        TypedQuery<RESULT> typedQuery = entityManager.createQuery(query);
        return typedQuery.getSingleResult();
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @return List 数据
     */
    public final @NotNull List<E> filter(E filter) {
        return filter(filter, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param sort   排序
     * @return List 数据
     */
    public final @NotNull List<E> filter(E filter, Sort sort) {
        return find(filter, createSort(sort), true);
    }

    /**
     * 全匹配查询数据
     *
     * @param page 分页对象
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filter(@NotNull Page page) {
        return filter(page, getEntityInstance());
    }

    /**
     * 全匹配查询数据
     *
     * @param page   分页对象
     * @param filter 过滤器
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filter(@NotNull Page page, @NotNull E filter) {
        return filter(page, filter, createSort(null));
    }

    /**
     * 全匹配查询数据
     *
     * @param page   分页对象
     * @param filter 过滤器
     * @param sort   排序
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filter(
            @NotNull Page page,
            @NotNull E filter,
            @NotNull org.springframework.data.domain.Sort sort
    ) {
        return find(page, filter, sort, true);
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(E filter) {
        return query(filter, new Sort());
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @param sort   排序
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(E filter, Sort sort) {
        return query(filter, createSort(sort));
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @param sort   排序
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(E filter, @NotNull org.springframework.data.domain.Sort sort) {
        return find(filter, sort, false);
    }

    /**
     * 查询数据
     *
     * @param predicate 查询条件
     * @return 查询结果数据列表
     */
    @SuppressWarnings("unused")
    public final @NotNull List<E> query(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate) {
        return query(predicate, new Sort());
    }

    /**
     * 查询数据
     *
     * @param predicate 查询条件
     * @param sort      排序
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate, Sort sort
    ) {
        return query(predicate, createSort(sort));
    }

    /**
     * 查询数据
     *
     * @param predicate 查询条件
     * @param sort      排序
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(
            BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate,
            org.springframework.data.domain.Sort sort
    ) {
        return repository.findAll(
                (root, criteriaQuery, builder) -> predicate.apply(root, builder),
                sort
        );
    }

    /**
     * 查询分页数据
     *
     * @param page 分页
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> query(
            @NotNull Page page
    ) {
        return query(page, getEntityInstance());
    }

    /**
     * 查询分页数据
     *
     * @param page   分页
     * @param filter 查询条件
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> query(
            @NotNull Page page,
            @NotNull E filter
    ) {
        return query(page, filter, createSort(null));
    }

    /**
     * 查询分页数据
     *
     * @param page      分页
     * @param predicate 查询条件
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> query(
            @NotNull Page page,
            BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate
    ) {
        return query(page, predicate, createSort(null));
    }

    /**
     * 查询分页数据
     *
     * @param page   分页
     * @param filter 查询条件
     * @param sort   排序
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> query(
            @NotNull Page page,
            @NotNull E filter, Sort sort
    ) {
        return query(page, filter, createSort(sort));
    }

    /**
     * 查询分页数据
     *
     * @param page      分页
     * @param predicate 查询条件
     * @param sort      排序
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> query(
            @NotNull Page page,
            BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate,
            Sort sort
    ) {
        return query(page, predicate, createSort(sort));
    }

    /**
     * 查询分页数据
     *
     * @param page   分页
     * @param filter 查询条件
     * @param sort   排序
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> query(
            @NotNull Page page,
            @NotNull E filter,
            @NotNull org.springframework.data.domain.Sort sort
    ) {
        return find(page, filter, sort, false);
    }

    /**
     * 查询分页数据
     *
     * @param page      分页
     * @param predicate 查询条件
     * @param sort      排序
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> query(
            @NotNull Page page,
            BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate,
            org.springframework.data.domain.Sort sort
    ) {
        org.springframework.data.domain.Page<E> pageData = repository.findAll(
                (root, criteriaQuery, builder) -> {
                    if (Objects.isNull(predicate)) {
                        return null;
                    }
                    return predicate.apply(root, builder);
                },
                createPageable(page, sort)
        );
        return PageData.newInstance(pageData);
    }

    /**
     * 创建导出任务
     *
     * @param queryPageRequest 请求查询的分页参数
     * @return 导出任务 ID
     */
    public final String createExportTask(QueryPageRequest<E> queryPageRequest) {
        final QueryPageRequest<E> finalQueryPageRequest = requireQueryRequestNonNullElse(queryPageRequest, new QueryPageRequest<>());
        String traceId = TraceUtil.getTraceId();
        return exportHelper.createExportTask(() -> {
            TraceUtil.setTraceId(traceId);
            ExportHelper.ExportFile exportFile = exportHelper.getExportFilePath("csv");
            // 获取导出字段列表
            List<Field> fieldList = CollectionUtil.getExportFieldList(getEntityClass());
            // 获取一行用作于表头
            List<String> rowList = CollectionUtil.getCsvHeaderList(fieldList);
            String headerString = String.join(CollectionUtil.CSV_COLUMN_DELIMITER, rowList);
            List<String> header = new ArrayList<>();
            header.add(headerString);
            // 保存表头到 CSV 文件
            saveCsvListToFile(exportFile, header);
            // 查询数据并保存到导出文件
            queryPageToSaveExportFile(finalQueryPageRequest, fieldList, exportFile);
            return exportFile.getRelativeFile();
        });
    }

    /**
     * 详情查询后置方法
     *
     * @param entity 查到的数据
     * @return 处理后的数据
     */
    protected E afterGet(@NotNull E entity) {
        return entity;
    }

    /**
     * 不分页查询后置方法
     *
     * @param list 查询到的数据
     * @return 处理后的数据
     * @see #getPage(QueryPageRequest)
     */
    protected @NotNull List<E> afterGetList(@NotNull List<E> list) {
        return list;
    }

    /**
     * 分页查询前置方法
     *
     * @param sourceRequestData 原始请求的数据
     * @return 处理后的请求数据
     */
    protected @NotNull QueryPageRequest<E> beforeGetPage(@NotNull QueryPageRequest<E> sourceRequestData) {
        return sourceRequestData;
    }

    /**
     * 分页查询后置方法
     *
     * @param queryPageResponse 查询到的数据
     * @return 处理后的数据
     */
    protected @NotNull QueryPageResponse<E> afterGetPage(@NotNull QueryPageResponse<E> queryPageResponse) {
        return queryPageResponse;
    }

    /**
     * 数据库操作前的 {@code 最后一次} 确认
     *
     * @return 当前实体
     */
    protected @NotNull E beforeSaveToDatabase(@NotNull E entity) {
        return entity;
    }

    /**
     * 添加搜索的查询条件
     *
     * @param root    {@code ROOT}
     * @param builder 参数构造器
     * @param search  原始查询对象
     * @return 查询条件列表
     * @apiNote 如需要删除自动添加的查询条件，请调用 {@link #beforeCreatePredicate(CurdEntity)}
     */
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<E> root,
            @NotNull CriteriaBuilder builder,
            @NotNull E search
    ) {
        return new ArrayList<>();
    }

    /**
     * 导出查询后置方法
     *
     * @param exportList 导出的数据列表
     * @return 处理后的数据列表
     */
    protected List<E> afterExportQuery(@NotNull List<E> exportList) {
        return exportList;
    }

    /**
     * 导出查询前置方法
     *
     * @param queryPageRequest 查询请求
     * @return 处理后的查询请求
     */
    protected QueryPageRequest<E> beforeExportQuery(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }

    /**
     * 添加前置方法
     *
     * @param source 原始实体
     * @return 处理后的实体
     */
    protected @NotNull E beforeAdd(@NotNull E source) {
        return source;
    }

    /**
     * 添加后置方法
     *
     * @param id     主键 ID
     * @param source 原始实体
     */
    protected void afterAdd(long id, @NotNull E source) {
    }

    /**
     * 修改前置方法
     *
     * @param source 原始实体
     * @return 处理后的实体
     */
    protected @NotNull E beforeUpdate(@NotNull E source) {
        return source;
    }

    /**
     * 修改后置方法
     *
     * <p>
     * 请不要在重写此方法后再次调用 {@link #update(E)} 以 {@code 避免循环调用}
     * </p>
     * <p>
     * 如需再次保存，请调用 {@link #updateToDatabase(E)}
     * </p>
     *
     * @param id     更新的 ID
     * @param source 更新前的数据
     */
    protected void afterUpdate(long id, @NotNull E source) {
    }

    /**
     * 保存后置方法
     *
     * @param id     主键 ID
     * @param source 保存前的原数据
     * @apiNote 添加或修改后最后触发
     */
    @SuppressWarnings("unused")
    protected void afterSaved(long id, @NotNull E source) {
    }

    /**
     * 禁用前置方法
     *
     * @param entity 禁用的数据
     */
    protected void beforeDisable(@NotNull E entity) {
    }

    /**
     * 禁用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings("unused")
    protected void afterDisable(long id) {
    }

    /**
     * 启用前置方法
     *
     * @param entity 启用的数据
     */
    @SuppressWarnings("unused")
    protected void beforeEnable(@NotNull E entity) {
    }

    /**
     * 启用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings("unused")
    protected void afterEnable(long id) {
    }

    /**
     * 删除前置方法
     *
     * @param entity 删除的数据
     */
    protected void beforeDelete(@NotNull E entity) {
    }

    /**
     * 是否是软删除
     */
    protected boolean isSoftDelete() {
        return curdConfig.getDisableAsDelete();
    }

    /**
     * 删除后置方法
     *
     * @param id 主键
     */
    @SuppressWarnings("unused")
    protected void afterDelete(long id) {
    }

    /**
     * 不分页查询前置方法
     *
     * @param sourceRequestData 查询条件
     * @return 处理后的查询条件
     * @see #getList(QueryListRequest)
     */
    protected @NotNull QueryListRequest<E> beforeGetList(@NotNull QueryListRequest<E> sourceRequestData) {
        return sourceRequestData;
    }

    /**
     * 在创建查询条件前调用
     *
     * @param filter 过滤器
     * @return 处理后的过滤器
     * @apiNote 此处理不影响 {@link #addSearchPredicate(Root, CriteriaBuilder, CurdEntity)} 的 {@code search} 参数
     */
    protected E beforeCreatePredicate(@NotNull E filter) {
        return filter;
    }

    /**
     * 添加查询条件 ({@code value} 不为 {@code null} 时)
     *
     * @param root          {@code ROOT}
     * @param predicateList 查询条件列表
     * @param fieldName     所属的字段名称
     * @param expression    表达式
     * @param value         条件的值
     */
    protected final <Y extends Comparable<? super Y>> void addPredicateNonNull(
            @NotNull Root<E> root,
            List<Predicate> predicateList,
            String fieldName,
            BiFunction<Expression<? extends Y>, Y, Predicate> expression,
            Y value) {
        if (Objects.nonNull(value)) {
            predicateList.add(expression.apply(root.get(fieldName), value));
        }
    }

    /**
     * 新建一个实体
     *
     * @return 实体
     */
    protected final @NotNull E getEntityInstance() {
        return ReflectUtil.newInstance(getEntityClass());
    }

    /**
     * 新建一个实体
     *
     * @param id 实体主键 ID
     * @return 实体
     */
    protected final @NotNull E getEntityInstance(long id) {
        return getEntityInstance().setId(id);
    }

    /**
     * 查询数据
     *
     * @param filter   过滤条件
     * @param sort     排序
     * @param isEquals 是否全匹配
     * @return 查询结果数据列表
     */
    private @NotNull List<E> find(E filter, org.springframework.data.domain.Sort sort, boolean isEquals) {
        return repository.findAll(
                createSpecification(filter, isEquals),
                sort
        );
    }


    /**
     * 查询分页数据
     *
     * @param page     分页
     * @param filter   查询条件
     * @param sort     排序
     * @param isEquals 是否全匹配
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> find(
            @NotNull Page page,
            E filter,
            @NotNull org.springframework.data.domain.Sort sort,
            boolean isEquals
    ) {
        final E finalFilter = requireFilterNonNull(filter);
        org.springframework.data.domain.Page<E> pageData = repository.findAll(
                (root, criteriaQuery, criteriaBuilder) ->
                        createPredicate(
                                root,
                                criteriaQuery,
                                criteriaBuilder,
                                finalFilter,
                                isEquals,
                                null,
                                null
                        ),
                createPageable(page, sort)
        );
        return PageData.newInstance(pageData);
    }

    /**
     * 验证非空查询请求
     *
     * @param queryListRequest 查询请求
     * @param newInstance      新实例
     * @return 检查后的查询请求
     */
    private <Q extends QueryListRequest<E>> @NotNull Q requireQueryRequestNonNullElse(
            Q queryListRequest, Q newInstance) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, newInstance);
        queryListRequest.setFilter(requireFilterNonNull(queryListRequest.getFilter()));
        queryListRequest.setSort(requireSortNonNull(queryListRequest.getSort()));
        if (queryListRequest instanceof QueryPageRequest<?> queryPageRequest) {
            queryPageRequest.setPage(requirePageNonNull(queryPageRequest.getPage()));
        }
        return queryListRequest;
    }

    /**
     * 验证非空过滤器请求
     *
     * @param filter 过滤器
     * @return 检查后的过滤器
     */
    @Contract("!null -> param1")
    private E requireFilterNonNull(E filter) {
        return Objects.requireNonNullElse(filter, getEntityInstance());
    }

    /**
     * 根据主键查询对应的实体
     *
     * @param id 主键
     * @return 实体
     */
    private @NotNull E getById(Long id) {
        String description = getEntityDescription();
        PARAM_MISSING.whenNull(id, String.format("查询失败，请传入%s的ID！", description));
        entityManager.clear();
        Optional<E> optional = repository.findById(id);
        if (optional.isEmpty()) {
            throw new ServiceException(DATA_NOT_FOUND, String.format("没有查询到ID为%s的%s", id, description));
        }
        E entity = optional.get();
        if (isSoftDelete()) {
            // 软删除
            DATA_NOT_FOUND.when(entity.getIsDisabled(), String.format("ID为%s的%s已被删除", id, description));
        }
        return entity;
    }

    /**
     * 保存到数据库
     *
     * @param entity   待保存实体
     * @param withNull 是否保存空值
     * @return 保存的主键
     * @see #addToDatabase(CurdEntity)
     * @see #updateToDatabase(CurdEntity)
     */
    private long saveToDatabase(@NotNull E entity, boolean withNull) {
        checkUnique(entity);
        entity.setUpdateTime(System.currentTimeMillis());
        if (Objects.isNull(entity.getId())) {
            // 设置当前时间为创建时间
            entity.setCreateTime(System.currentTimeMillis())
                    .setIsDisabled(false);
            // 新增
            return saveToDatabase(entity);
        }
        // 更新 不允许修改创建时间
        entity.setCreateTime(null);
        // 有ID 走修改 且不允许修改下列字段
        E existEntity = getById(entity.getId());
        entity = withNull ? entity : getEntityForUpdate(entity, existEntity);
        return saveToDatabase(entity);
    }

    /**
     * 保存并强刷到数据库
     *
     * @param entity 保存的实体
     * @return 保存的主键
     * @apiNote 仅供 {@link #saveToDatabase(E, boolean)} 调用
     */
    private long saveToDatabase(@NotNull E entity) {
        E target = getEntityInstance();
        BeanUtils.copyProperties(entity, target);
        target = beforeSaveToDatabase(target);
        target = repository.saveAndFlush(target);
        return target.getId();
    }

    /**
     * 获取用于更新的实体
     *
     * @param source 来源实体
     * @param exist  已存在实体
     * @return 目标实体
     */
    @Contract("_, _ -> param2")
    private @NotNull E getEntityForUpdate(@NotNull E source, @NotNull E exist) {
        desensitize(source);
        String[] ignoreProperties = getUpdateIgnoreFields(source);
        BeanUtils.copyProperties(source, exist, ignoreProperties);
        return exist;
    }

    /**
     * 获取实体类
     *
     * @return 实体类
     */
    private @NotNull Class<E> getEntityClass() {
        return getFirstParameterizedTypeClass();
    }

    /**
     * 判断是否唯一
     *
     * @param entity 实体
     */
    private void checkUnique(@NotNull E entity) {
        List<Field> fields = ReflectUtil.getFieldList(getEntityClass());
        fields.forEach(field -> {
            Column annotation = ReflectUtil.getAnnotation(Column.class, field);
            if (Objects.isNull(annotation)) {
                // 不是数据库列 不校验
                return;
            }
            if (!annotation.unique()) {
                // 没有标唯一 不校验
                return;
            }
            Object fieldValue = ReflectUtil.getFieldValue(entity, field);
            if (Objects.isNull(fieldValue)) {
                // 没有值 不校验
                return;
            }
            E search = getEntityInstance();
            ReflectUtil.setFieldValue(search, field, fieldValue);
            Example<E> example = Example.of(search);
            Optional<E> exist = repository.findOne(example);
            if (exist.isEmpty()) {
                // 没查到 不校验
                return;
            }
            if (Objects.nonNull(entity.getId()) && Objects.equals(exist.get().getId(), entity.getId())) {
                // 修改自己 不校验
                return;
            }
            FORBIDDEN_EXIST.show(String.format("%s (%s) 已经存在，请修改后重新提交！",
                    ReflectUtil.getDescription(field), fieldValue)
            );
        });
    }

    /**
     * 获取忽略更新的字段名称列表
     *
     * @param source 来源对象
     * @return 需要忽略更新的属性列表
     */
    private String @NotNull [] getUpdateIgnoreFields(@NotNull E source) {
        // 获取 Bean
        BeanWrapper srcBean = new BeanWrapperImpl(source);
        List<String> ignoreList = new ArrayList<>();
        Arrays.stream(srcBean.getPropertyDescriptors()).map(PropertyDescriptor::getName).forEach(name -> {
            // 获取属性的 Field
            Field field = ReflectUtil.getField(name, source.getClass());
            if (Objects.isNull(field)) {
                // 获取属性失败，允许更新
                return;
            }
            NullEnable nullEnable = ReflectUtil.getAnnotation(NullEnable.class, field);
            if (Objects.nonNull(nullEnable) && nullEnable.value()) {
                // 允许更新 null
                return;
            }
            if (Objects.isNull(srcBean.getPropertyValue(name))) {
                // 没有值 忽略更新
                ignoreList.add(name);
            }
        });
        return ignoreList.toArray(new String[0]);
    }

    /**
     * 创建排序对象
     *
     * @param sort 排序对象
     * @return Sort {@code Spring} 的排序对象
     */
    private @NotNull org.springframework.data.domain.Sort createSort(Sort sort) {
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
     * 创建分页对象
     *
     * @param page 分页对象
     * @return Spring 分页对象
     */
    private @NotNull Pageable createPageable(Page page, org.springframework.data.domain.Sort sort) {
        page = requirePageNonNull(page);
        int pageNumber = Math.max(0, page.getPageNum() - 1);
        int pageSize = Math.max(1, page.getPageSize());
        return PageRequest.of(pageNumber, pageSize, sort);
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
    private @NotNull List<jakarta.persistence.criteria.Predicate> getPredicateList(
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

    /**
     * 添加创建时间和更新时间的查询条件
     *
     * @param root          {@code ROOT}
     * @param builder       参数构造器
     * @param search        原始查询对象
     * @param predicateList 查询条件列表
     */
    private void addCreateAndUpdateTimePredicate(
            @NotNull Root<E> root, @NotNull CriteriaBuilder builder,
            @NotNull E search, @NotNull List<Predicate> predicateList
    ) {
        addPredicateNonNull(root, predicateList,
                CurdEntity.STRING_CREATE_TIME, builder::greaterThanOrEqualTo, search.getCreateTimeFrom()
        );
        addPredicateNonNull(root, predicateList,
                CurdEntity.STRING_CREATE_TIME, builder::lessThan, search.getCreateTimeTo()
        );
        addPredicateNonNull(root, predicateList,
                CurdEntity.STRING_UPDATE_TIME, builder::greaterThanOrEqualTo, search.getUpdateTimeFrom()
        );
        addPredicateNonNull(root, predicateList,
                CurdEntity.STRING_UPDATE_TIME, builder::lessThan, search.getUpdateTimeTo()
        );
    }

    /**
     * 创建查询对象
     *
     * @param filter  过滤器对象
     * @param isEqual 是否强匹配
     * @return 查询对象
     */
    @Contract(pure = true)
    private @NotNull Specification<E> createSpecification(E filter, boolean isEqual) {
        final E finalFilter = requireFilterNonNull(filter);
        return (root, criteriaQuery, criteriaBuilder) ->
                createPredicate(
                        root,
                        criteriaQuery,
                        criteriaBuilder,
                        finalFilter,
                        isEqual,
                        this::beforeCreatePredicate,
                        (f, predicateList) -> {
                            // 添加更多自定义查询条件
                            predicateList.addAll(addSearchPredicate(root, criteriaBuilder, finalFilter));
                            // 添加修改时间和创建时间的区间查询
                            addCreateAndUpdateTimePredicate(root, criteriaBuilder, finalFilter, predicateList);
                            if (isSoftDelete()) {
                                // 过滤软删除的数据
                                addPredicateNonNull(root, predicateList, CurdEntity.STRING_IS_DISABLED, criteriaBuilder::equal, false);
                            }
                        }
                );
    }

    /**
     * 创建查询的 {@code Predicate}
     *
     * @param root             {@code model}
     * @param criteriaQuery    {@code query}
     * @param builder          {@code builder}
     * @param sourceFilter     过滤器实体
     * @param isEqual          是否强匹配
     * @param before           创建查询条件前对实体进行处理
     * @param addMorePredicate 添加更多查询条件(原始条件，已有条件，处理后的条件)
     * @return 查询条件
     */
    private @Nullable Predicate createPredicate(
            @NotNull Root<E> root, CriteriaQuery<?> criteriaQuery,
            @NotNull CriteriaBuilder builder,
            E sourceFilter,
            boolean isEqual,
            Function<E, E> before,
            BiConsumer<E, List<Predicate>> addMorePredicate

    ) {
        if (Objects.isNull(criteriaQuery)) {
            return null;
        }
        E lastFilter = sourceFilter;
        if (Objects.nonNull(before)) {
            lastFilter = before.apply(sourceFilter.copy());
        }
        List<Predicate> predicateList = getPredicateList(root, builder, lastFilter, isEqual);
        if (Objects.nonNull(addMorePredicate)) {
            // 需要添加自定义处理条件
            addMorePredicate.accept(sourceFilter, predicateList);
        }
        Predicate[] predicates = new Predicate[predicateList.size()];
        criteriaQuery.where(builder.and(predicateList.toArray(predicates)));
        return criteriaQuery.getRestriction();
    }

    /**
     * 保存 CSV 数据
     *
     * @param exportFile 导出文件
     * @param valueList  数据列表
     */
    private void saveCsvListToFile(@NotNull ExportHelper.ExportFile exportFile, List<String> valueList) {
        String rowString = String.join(CollectionUtil.CSV_ROW_DELIMITER, valueList);
        // 写入文件
        FileUtil.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), rowString + CollectionUtil.CSV_ROW_DELIMITER, StandardOpenOption.APPEND);
    }

    /**
     * 分页查询导出数据
     *
     * @param queryPageRequest 查询对象
     */
    private void queryPageToSaveExportFile(QueryPageRequest<E> queryPageRequest, List<Field> fieldList, ExportHelper.ExportFile exportFile) {
        queryPageRequest = beforeExportQuery(queryPageRequest);
        QueryPageResponse<E> page = getPage(queryPageRequest);
        log.info("导出查询第 {} 页，本页 {} 条", page.getPage().getPageNum(), page.getList().size());
        // 当前页查到的数据列表
        List<E> list = page.getList();
        list = afterExportQuery(list);

        // 获取 CSV 值列表
        List<String> valuelist = CollectionUtil.getCsvValueList(list, fieldList);

        // 保存 CSV 数据
        saveCsvListToFile(exportFile, valuelist);

        if (page.getPage().getPageNum() < page.getPageCount()) {
            // 继续分页
            queryPageRequest.getPage().setPageNum(page.getPage().getPageNum() + 1);
            queryPageToSaveExportFile(queryPageRequest, fieldList, exportFile);
        }
    }

    /**
     * 获取实体描述
     *
     * @return 实体描述
     */
    private String getEntityDescription() {
        return ReflectUtil.getDescription(getEntityClass());
    }

    /**
     * 获取非空的分页对象
     *
     * @param page 分页对象
     * @return 分页对象
     */
    private @NotNull Page requirePageNonNull(Page page) {
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
     * 获取非空的排序对象
     *
     * @param sort 排序对象
     * @return 排序对象
     */
    private @NotNull Sort requireSortNonNull(Sort sort) {
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
}
