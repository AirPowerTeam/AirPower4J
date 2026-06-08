package cn.hamm.airpower.curd.base;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.ReflectUtil;
import cn.hamm.airpower.core.TaskUtil;
import cn.hamm.airpower.core.TraceUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.curd.annotation.NullEnable;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.helper.TransactionHelper;
import cn.hamm.airpower.curd.model.query.*;
import cn.hamm.airpower.curd.service.RootService;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.Null;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.domain.Specification;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

import static cn.hamm.airpower.exception.Errors.*;

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
 * @see #repository 还实现不了？<code>repository</code> 给你，你自己来
 */
@Slf4j
public class CurdService<E extends CurdEntity<E>, R extends ICurdRepository<E>> extends RootService<E> {
    /**
     * 提交的数据不允许为空
     */
    private static final String DATA_REQUIRED = "提交的数据不允许为空";
    /**
     * 实体管理器
     */
    @PersistenceContext
    protected EntityManager entityManager;
    /**
     * 数据源
     */
    @Autowired(required = false)
    protected R repository;
    /**
     * 事务管理器
     */
    @Autowired
    protected TransactionHelper transactionHelper;

    /**
     * 查询辅助
     */
    @Autowired
    private QueryHelper queryHelper;

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
     * @param source   原始实体
     * @param withNull 是否允许修改为 null
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
    public final @NotNull QueryPageResponse<E> getPage(
            QueryPageRequest<E> queryPageRequest
    ) {
        queryPageRequest = requireQueryRequestNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        PageData<E> pageData = queryPage(queryPageRequest.getPage(), queryPageRequest.getFilter(), queryPageRequest.getSort());
        // 组装分页数据
        QueryPageResponse<E> queryPageResponse = QueryPageResponse.from(pageData);
        queryPageResponse.setSort(queryPageRequest.getSort());
        queryPageResponse = afterGetPage(queryPageResponse);
        return queryPageResponse;
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
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @return List 数据
     */
    public final @NotNull List<E> filter(@Nullable E filter) {
        return filter(filter, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param sort   排序
     * @return List 数据
     */
    public final @NotNull List<E> filter(@Nullable E filter, @Nullable Sort sort) {
        return find(filter, sort, true);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filterPage(@Nullable E filter) {
        return filterPage(filter, null, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param page   分页对象
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filterPage(@Null E filter, @Nullable Page page) {
        return filterPage(filter, page, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param page   分页对象
     * @param sort   排序
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> filterPage(
            @Nullable E filter,
            @Nullable Page page,
            @Nullable Sort sort
    ) {
        return find(filter, page, sort, true);
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(@Nullable E filter) {
        return query(filter, null);
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @param sort   排序
     * @return 查询结果数据列表
     */
    public final @NotNull List<E> query(E filter, @Nullable Sort sort) {
        return find(filter, sort, false);
    }

    /**
     * 查询分页数据
     *
     * @param page 分页
     * @return 查询结果数据分页对象
     */
    @SuppressWarnings("unused")
    public final @NotNull PageData<E> queryPage(
            @NotNull Page page
    ) {
        return queryPage(page, getEntityInstance());
    }

    /**
     * 查询分页数据
     *
     * @param page   分页
     * @param filter 查询条件
     * @return 查询结果数据分页对象
     */
    public final @NotNull PageData<E> queryPage(
            @NotNull Page page,
            @NotNull E filter
    ) {
        return queryPage(page, filter, null);
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
    public final @NotNull PageData<E> queryPage(
            @NotNull Page page,
            @NotNull E filter,
            @Nullable Sort sort
    ) {
        return find(filter, page, sort, false);
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
            ExportHelper.saveCsvListToFile(exportFile, header);
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
    @SuppressWarnings({"unused", "EmptyMethod"})
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
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterDisable(long id) {
    }

    /**
     * 启用前置方法
     *
     * @param entity 启用的数据
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeEnable(@NotNull E entity) {
    }

    /**
     * 启用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
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
     * 删除后置方法
     *
     * @param id 主键
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
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
    private @NotNull List<E> find(@Nullable E filter, @Nullable Sort sort, boolean isEquals) {
        return repository.findAll(
                createSpecification(filter, isEquals),
                queryHelper.createSort(sort)
        );
    }

    /**
     * 查询分页数据
     *
     * @param filter   查询条件
     * @param page     分页
     * @param sort     排序
     * @param isEquals 是否全匹配
     * @return 查询结果数据分页对象
     */
    private @NotNull PageData<E> find(
            @Nullable E filter,
            @Nullable Page page,
            @Nullable Sort sort,
            boolean isEquals
    ) {
        return PageData.newInstance(repository.findAll(
                createSpecification(filter, isEquals),
                queryHelper.createPageable(page, sort)
        ));
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
        queryListRequest.setSort(queryHelper.requireSortNonNull(queryListRequest.getSort()));
        if (queryListRequest instanceof QueryPageRequest<?> queryPageRequest) {
            queryPageRequest.setPage(queryHelper.requirePageNonNull(queryPageRequest.getPage()));
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
        return optional.get();
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
     * 创建查询对象
     *
     * @param filter  过滤器对象
     * @param isEqual 是否强匹配
     * @return 查询对象
     */
    @Contract(pure = true)
    private @NotNull Specification<E> createSpecification(@Nullable E filter, boolean isEqual) {
        return (root, criteriaQuery, criteriaBuilder) ->
                createPredicate(
                        root,
                        criteriaQuery,
                        criteriaBuilder,
                        filter,
                        isEqual
                );
    }

    /**
     * 创建查询的条件
     *
     * @param root          {@code model}
     * @param criteriaQuery {@code query}
     * @param builder       {@code builder}
     * @param filter        过滤器实体
     * @param isEqual       是否强匹配
     * @return 查询条件
     */
    private @Nullable Predicate createPredicate(
            @NotNull Root<E> root, CriteriaQuery<?> criteriaQuery,
            @NotNull CriteriaBuilder builder,
            @Nullable E filter,
            boolean isEqual

    ) {
        if (Objects.isNull(criteriaQuery)) {
            return null;
        }
        List<Predicate> predicateList = queryHelper.getPredicateList(root, builder, filter, isEqual);
        Predicate[] predicates = new Predicate[predicateList.size()];
        criteriaQuery.where(builder.and(predicateList.toArray(predicates)));
        return criteriaQuery.getRestriction();
    }

    /**
     * 分页查询导出数据
     *
     * @param queryPageRequest 查询对象
     */
    private void queryPageToSaveExportFile(QueryPageRequest<E> queryPageRequest, List<Field> fieldList, ExportHelper.ExportFile exportFile) {
        queryPageRequest = beforeExportQuery(queryPageRequest);
        PageData<E> page = queryPage(queryPageRequest.getPage(), queryPageRequest.getFilter(), queryPageRequest.getSort());
        String description = getEntityDescription();
        log.info("导出{} 查询第 {} 页，本页 {} 条", description, page.getPage().getPageNum(), page.getList().size());
        // 当前页查到的数据列表
        List<E> list = page.getList();
        list = afterExportQuery(list);

        // 获取 CSV 值列表
        List<String> valuelist = CollectionUtil.getCsvValueList(list, fieldList);

        // 保存 CSV 数据
        ExportHelper.saveCsvListToFile(exportFile, valuelist);

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
}
