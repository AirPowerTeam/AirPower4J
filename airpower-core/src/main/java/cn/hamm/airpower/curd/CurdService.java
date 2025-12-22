package cn.hamm.airpower.curd;

import cn.hamm.airpower.annotation.NullEnable;
import cn.hamm.airpower.annotation.Search;
import cn.hamm.airpower.annotation.SearchEmpty;
import cn.hamm.airpower.curd.query.QueryListRequest;
import cn.hamm.airpower.curd.query.QueryPageRequest;
import cn.hamm.airpower.curd.query.QueryPageResponse;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.export.ExportHelper;
import cn.hamm.airpower.file.FileUtil;
import cn.hamm.airpower.helper.TransactionHelper;
import cn.hamm.airpower.reflect.ReflectUtil;
import cn.hamm.airpower.root.RootService;
import cn.hamm.airpower.util.CollectionUtil;
import cn.hamm.airpower.util.TaskUtil;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static cn.hamm.airpower.exception.ServiceError.*;

/**
 * <h1>实体根服务</h1>
 *
 * @param <E> 实体
 * @param <R> 数据源
 * @author Hamm.cn
 */
@SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection"})
@Slf4j
public class CurdService<E extends CurdEntity<E>, R extends ICurdRepository<E>> extends RootService<E> {
    /**
     * 提交的数据不允许为空
     */
    private static final String DATA_REQUIRED = "提交的数据不允许为空";

    /**
     * 数据源
     */
    @Autowired
    protected R repository;

    /**
     * 实体管理器
     */
    @Autowired
    protected EntityManager entityManager;

    /**
     * CURD配置
     */
    @Autowired
    private CurdConfig curdConfig;

    /**
     * 保存CSV数据
     *
     * @param exportFile 导出文件
     * @param valueList  数据列表
     */
    private static void saveCsvListToFile(@NotNull ExportHelper.ExportFile exportFile, List<String> valueList) {
        String rowString = String.join(CollectionUtil.CSV_ROW_DELIMITER, valueList);
        // 写入文件
        FileUtil.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), rowString + CollectionUtil.CSV_ROW_DELIMITER, StandardOpenOption.APPEND);
    }

    /**
     * 创建导出任务
     *
     * @param queryPageRequest 请求查询的分页参数
     * @return 导出任务ID
     */
    public final String createExportTask(QueryPageRequest<E> queryPageRequest) {
        return exportHelper.createExportTask(() -> {
            ExportHelper.ExportFile exportFile = exportHelper.getExportFilePath("csv");
            List<Field> fieldList = CollectionUtil.getExportFieldList(getFirstParameterizedTypeClass());
            List<String> rowList = CollectionUtil.getCsvHeaderList(fieldList);
            String headerString = String.join(CollectionUtil.CSV_COLUMN_DELIMITER, rowList);
            List<String> header = new ArrayList<>();
            header.add(headerString);
            saveCsvListToFile(exportFile, header);
            queryPageToSaveExportFile(queryPageRequest, fieldList, exportFile);
            return exportFile.getRelativeFile();
        });
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
        List<String> valuelist = CollectionUtil.getCsvValueList(list, fieldList);
        saveCsvListToFile(exportFile, valuelist);

        // 继续分页
        if (page.getPage().getPageNum() < page.getPageCount()) {
            queryPageRequest.getPage().setPageNum(page.getPage().getPageNum() + 1);
            queryPageToSaveExportFile(queryPageRequest, fieldList, exportFile);
        }
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
     * 添加一条数据
     *
     * @param source 原始实体
     * @return 保存后的主键 ID
     * @see #beforeAdd(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterAdd(long, E)
     * @see #afterSaved(long, E)
     */
    public final long add(@NotNull E source) {
        source.setIsDisabled(false).setCreateTime(System.currentTimeMillis());
        source = beforeAdd(source);
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        source.setId(null);
        E finalSource = source;
        long id = saveToDatabaseIgnoreNull(source);
        TaskUtil.run(() -> afterAdd(id, finalSource));
        return id;
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
     * 修改一条已经存在的数据
     *
     * @param source 保存的实体
     * @see #beforeUpdate(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     * @see #updateWithNull(E)
     */
    public final void update(@NotNull E source) {
        updateToDatabase(false, source);
    }

    /**
     * 修改一条已经存在的数据
     *
     * @param source 保存的实体
     * @apiNote 此方法的 {@code null} 属性依然会被更新到数据库
     * @see #beforeUpdate(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     * @see #update(E)
     */
    public final void updateWithNull(@NotNull E source) {
        updateToDatabase(true, source);
    }

    /**
     * 修改后置方法
     *
     * <p>
     * 请不要在重写此方法后再次调用 {@link #update(E)  } 与 {@link #updateWithNull(E)} 以 {@code 避免循环} 调用
     * </p>
     * <p>
     * 如需再次保存，请调用 {@link #updateToDatabase(E)}
     * </p>
     *
     * @param id     主键 ID
     * @param source 原始实体
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
    @SuppressWarnings("EmptyMethod")
    protected void afterSaved(long id, @NotNull E source) {

    }

    /**
     * 禁用前置方法
     *
     * @param id 主键 ID
     */
    protected void beforeDisable(long id) {
    }

    /**
     * 禁用指定的数据
     *
     * @param id 主键 ID
     * @see #beforeDisable(long)
     * @see #afterDisable(long)
     */
    public final void disable(long id) {
        beforeDisable(id);
        disableById(id);
        TaskUtil.run(() -> afterDisable(id));
    }

    /**
     * 禁用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings("EmptyMethod")
    protected void afterDisable(long id) {
    }

    /**
     * 启用前置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings("EmptyMethod")
    protected void beforeEnable(long id) {
    }

    /**
     * 启用指定的数据
     *
     * @param id 主键 ID
     * @see #beforeEnable(long)
     * @see #afterEnable(long)
     */
    public final void enable(long id) {
        beforeEnable(id);
        enableById(id);
        TaskUtil.run(() -> afterEnable(id));
    }

    /**
     * 启用后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings("EmptyMethod")
    protected void afterEnable(long id) {
    }

    /**
     * 删除前置方法
     *
     * @param id 主键 ID
     */
    protected void beforeDelete(long id) {
    }

    /**
     * 删除指定的数据
     *
     * @param id 主键 ID
     * @see #beforeDelete(long)
     * @see #afterDelete(long)
     */
    public final void delete(long id) {
        beforeDelete(id);
        deleteById(id);
        TaskUtil.run(() -> afterDelete(id));
    }

    /**
     * 删除后置方法
     *
     * @param id 主键 ID
     */
    @SuppressWarnings("EmptyMethod")
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
     * 不分页查询数据
     *
     * @param queryListRequest 列表请求对象
     * @return List数据
     * @see #beforeGetList(QueryListRequest)
     * @see #afterGetList(List)
     */
    public final @NotNull List<E> getList(QueryListRequest<E> queryListRequest) {
        queryListRequest = requireWithFilterNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeGetList(queryListRequest);
        List<E> list = query(queryListRequest);
        return afterGetList(list);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @return List数据
     * @see #filter(CurdEntity)
     * @see #filter(CurdEntity, Sort)
     * @see #query(CurdEntity)
     * @see #query(CurdEntity, Sort)
     * @see #query(QueryListRequest)
     */
    public final @NotNull List<E> filter(@Nullable E filter) {
        return filter(filter, null);
    }

    /**
     * 全匹配查询数据
     *
     * @param filter 过滤器
     * @param sort   排序
     * @return List数据
     * @see #filter(CurdEntity)
     * @see #filter(CurdEntity, Sort)
     * @see #query(CurdEntity)
     * @see #query(CurdEntity, Sort)
     * @see #query(QueryListRequest)
     */
    public final @NotNull List<E> filter(@Nullable E filter, @Nullable Sort sort) {
        return find(filter, sort, true);
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
     * 根据 ID 查询对应的实体
     *
     * @param id 主键 ID
     * @return 实体
     * @see #getMaybeNull(long)
     * @see #getWithEnable(long)
     */
    public final @NotNull E get(long id) {
        return afterGet(getById(id));
    }

    /**
     * 根据 ID 查询正常启用的实体
     *
     * @param id 主键 ID
     * @return 实体
     * @see #get(long)
     * @see #getMaybeNull(long)
     */
    public final @NotNull E getWithEnable(long id) {
        E entity = get(id);
        FORBIDDEN_DISABLED.when(
                entity.getIsDisabled(),
                String.format(FORBIDDEN_DISABLED.getMessage(), id, ReflectUtil.getDescription(getFirstParameterizedTypeClass()))
        );
        return entity;
    }

    /**
     * 根据 ID 查询对应的实体(可能为{@code null})
     *
     * @param id 主键 ID
     * @return 实体
     * @apiNote 查不到返回 {@code null}，不抛异常
     * @see #get(long)
     */
    public final @Nullable E getMaybeNull(long id) {
        return afterGet(getByIdMaybeNull(id));
    }

    /**
     * 详情查询后置方法
     *
     * @param result 查到的数据
     * @return 处理后的数据
     */
    protected E afterGet(E result) {
        return result;
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
    public final <FIELD, RESULT> RESULT selectAndCalculate(
            @NotNull BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate,
            String fieldName,
            @NotNull BiFunction<CriteriaBuilder, Path<FIELD>, Selection<? extends RESULT>> function,
            Class<RESULT> resultClass,
            Class<FIELD> fieldClass
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<RESULT> query = builder.createQuery(resultClass);
        Root<E> root = query.from(getFirstParameterizedTypeClass());
        Path<FIELD> field = root.get(fieldName);
        query.select(function.apply(builder, field)).where(predicate.apply(root, builder));
        TypedQuery<RESULT> typedQuery = entityManager.createQuery(query);
        return typedQuery.getSingleResult();
    }

    /**
     * 查询数据
     *
     * @param predicate 查询条件
     * @return 查询结果数据列表
     * @see #filter(CurdEntity)
     * @see #query(CurdEntity)
     * @see #query(QueryListRequest)
     * @see #find(CurdEntity, Sort, boolean)
     * @see #selectList(BiFunction)
     * @see #selectPage(BiFunction)
     */
    public final @NotNull List<E> selectList(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate) {
        return selectList(predicate, null);
    }

    /**
     * 查询数据
     *
     * @param predicate 查询条件
     * @param sort      排序
     * @return 查询结果数据列表
     * @see #filter(CurdEntity)
     * @see #query(CurdEntity)
     * @see #query(QueryListRequest)
     * @see #find(CurdEntity, Sort, boolean)
     * @see #selectList(BiFunction)
     * @see #selectPage(BiFunction)
     */
    public final @NotNull List<E> selectList(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate, @Nullable Sort sort) {
        return repository.findAll(
                (root, criteriaQuery, builder) -> predicate.apply(root, builder),
                createSort(sort)
        );
    }

    /**
     * 查询分页数据
     *
     * @param predicate 查询条件
     * @return 查询结果数据分页对象
     * @see #filter(CurdEntity)
     * @see #query(CurdEntity)
     * @see #query(QueryListRequest)
     * @see #find(CurdEntity, Sort, boolean)
     * @see #selectList(BiFunction)
     * @see #selectPage(BiFunction)
     */
    public final @NotNull QueryPageResponse<E> selectPage(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate) {
        return selectPage(predicate, null, null);
    }

    /**
     * 查询分页数据
     *
     * @param predicate 查询条件
     * @param sort      排序
     * @return 查询结果数据分页对象
     * @see #filter(CurdEntity)
     * @see #query(CurdEntity)
     * @see #query(QueryListRequest)
     * @see #find(CurdEntity, Sort, boolean)
     * @see #selectList(BiFunction)
     * @see #selectPage(BiFunction)
     */
    public final @NotNull QueryPageResponse<E> selectPage(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate, @Nullable Sort sort) {
        return selectPage(predicate, null, sort);
    }

    /**
     * 查询分页数据
     *
     * @param predicate 查询条件
     * @param page      分页
     * @return 查询结果数据分页对象
     * @see #filter(CurdEntity)
     * @see #query(CurdEntity)
     * @see #query(QueryListRequest)
     * @see #find(CurdEntity, Sort, boolean)
     * @see #selectList(BiFunction)
     * @see #selectPage(BiFunction)
     */
    public final @NotNull QueryPageResponse<E> selectPage(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate, @Nullable Page page) {
        return selectPage(predicate, page, null);
    }

    /**
     * 查询分页数据
     *
     * @param predicate 查询条件
     * @param page      分页
     * @param sort      排序
     * @return 查询结果数据分页对象
     * @see #filter(CurdEntity)
     * @see #query(CurdEntity)
     * @see #query(QueryListRequest)
     * @see #find(CurdEntity, Sort, boolean)
     * @see #selectList(BiFunction)
     * @see #selectPage(BiFunction)
     */
    public final @NotNull QueryPageResponse<E> selectPage(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> predicate, @Nullable Page page, @Nullable Sort sort) {
        QueryPageRequest<E> queryPageRequest = new QueryPageRequest<>();
        queryPageRequest.setPage(page);
        queryPageRequest.setSort(sort);
        org.springframework.data.domain.Page<E> pageData = repository.findAll(
                (root, criteriaQuery, builder) -> predicate.apply(root, builder),
                createPageable(queryPageRequest)
        );
        // 组装分页数据
        QueryPageResponse<E> queryPageResponse = QueryPageResponse.newInstance(pageData);
        queryPageResponse.setSort(queryPageRequest.getSort());
        return queryPageResponse;
    }

    /**
     * 分页查询数据
     *
     * @param queryPageRequest 请求的 {@code request} 对象
     * @return 分页查询列表
     * @see #beforeGetPage(QueryPageRequest)
     * @see #afterGetPage(QueryPageResponse)
     */
    public final @NotNull QueryPageResponse<E> getPage(@Nullable QueryPageRequest<E> queryPageRequest) {
        queryPageRequest = requireWithFilterNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        org.springframework.data.domain.Page<E> pageData = repository.findAll(
                createSpecification(queryPageRequest.getFilter(), false), createPageable(queryPageRequest)
        );
        // 组装分页数据
        QueryPageResponse<E> queryPageResponse = QueryPageResponse.newInstance(pageData);
        queryPageResponse.setSort(queryPageRequest.getSort());
        return afterGetPage(queryPageResponse);
    }

    /**
     * 禁用指定的数据
     *
     * @param id 主键 ID
     * @apiNote 不建议直接调用, 请优先使用前后置方法
     * @see #beforeDisable(long)
     * @see #afterDisable(long)
     */
    protected final void disableById(long id) {
        E entity = get(id);
        saveToDatabaseIgnoreNull(entity.setIsDisabled(true));
    }

    /**
     * 启用指定的数据
     *
     * @param id 主键 ID
     * @apiNote 不建议直接调用, 请优先使用前后置方法
     * @see #beforeEnable(long)
     * @see #afterEnable(long)
     */
    protected final void enableById(long id) {
        E entity = get(id);
        saveToDatabaseIgnoreNull(entity.setIsDisabled(false));
    }

    /**
     * 删除指定的数据
     *
     * @param id 主键 ID
     * @apiNote 不建议直接调用, 请优先使用前后置方法
     * @see #beforeDelete(long)
     * @see #afterDelete(long)
     */
    protected final void deleteById(long id) {
        repository.deleteById(id);
    }

    /**
     * 更新到数据库 {@code 不触发前后置}
     *
     * @param source 原始实体
     * @see #update(E)
     * @see #updateWithNull(E)
     */
    protected final void updateToDatabase(@NotNull E source) {
        updateToDatabase(source, false);
    }

    /**
     * 更新到数据库 {@code 触发前后置}
     *
     * @param source   原始实体
     * @param withNull 是否更新空值
     * @apiNote 请注意，此方法不会触发前后置
     * @see #update(E)
     * @see #updateWithNull(E)
     */
    protected final void updateToDatabase(@NotNull E source, boolean withNull) {
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        PARAM_MISSING.whenNull(source.getId(), String.format(
                "修改失败，请传入%s的ID!",
                ReflectUtil.getDescription(getFirstParameterizedTypeClass())
        ));
        saveToDatabase(source, withNull);
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
     * 模糊匹配查询数据
     *
     * @param queryListRequest 查询请求
     * @return 查询结果数据列表
     * @see #filter(CurdEntity)
     * @see #filter(CurdEntity, Sort)
     * @see #query(CurdEntity)
     * @see #query(CurdEntity, Sort)
     * @see #query(QueryListRequest)
     */
    public final @NotNull List<E> query(@NotNull QueryListRequest<E> queryListRequest) {
        return find(queryListRequest.getFilter(), queryListRequest.getSort(), false);
    }

    /**
     * 模糊匹配查询数据
     *
     * @param filter 过滤条件
     * @return 查询结果数据列表
     * @see #filter(CurdEntity)
     * @see #filter(CurdEntity, Sort)
     * @see #query(CurdEntity)
     * @see #query(CurdEntity, Sort)
     * @see #query(QueryListRequest)
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
     * @see #filter(CurdEntity)
     * @see #filter(CurdEntity, Sort)
     * @see #query(CurdEntity)
     * @see #query(CurdEntity, Sort)
     * @see #query(QueryListRequest)
     */
    public final @NotNull List<E> query(@Nullable E filter, @Nullable Sort sort) {
        return find(filter, sort, false);
    }

    /**
     * 查询数据
     *
     * @param filter   过滤条件
     * @param sort     排序
     * @param isEquals 是否全匹配
     * @return 查询结果数据列表
     * @see #filter(CurdEntity)
     * @see #filter(CurdEntity, Sort)
     * @see #query(CurdEntity)
     * @see #query(CurdEntity, Sort)
     * @see #query(QueryListRequest)
     */
    private @NotNull List<E> find(@Nullable E filter, @Nullable Sort sort, boolean isEquals) {
        filter = Objects.requireNonNullElse(filter, ReflectUtil.newInstance(getFirstParameterizedTypeClass()));
        return repository.findAll(
                createSpecification(filter, isEquals),
                createSort(sort)
        );
    }

    /**
     * 验证非空查询请求且非空过滤器请求
     *
     * @param queryListRequest 查询请求
     * @param newInstance      新实例
     * @return 检查后的查询请求
     */
    private <Q extends QueryListRequest<E>> @NotNull Q requireWithFilterNonNullElse(
            Q queryListRequest, Q newInstance) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, newInstance);
        queryListRequest.setFilter(Objects.requireNonNullElse(
                queryListRequest.getFilter(),
                ReflectUtil.newInstance(getFirstParameterizedTypeClass()))
        );
        return queryListRequest;
    }

    /**
     * 更新到数据库
     *
     * @param withNull 是否更新 {@code null} 属性
     * @param source   原始数据
     */
    private void updateToDatabase(boolean withNull, @NotNull E source) {
        long id = source.getId();
        source = beforeUpdate(source);
        updateToDatabase(source, withNull);
        E finalSource = source;
        TaskUtil.run(
                () -> afterUpdate(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
    }

    @Autowired
    private TransactionHelper transactionHelper;

    /**
     * 根据 ID 查询对应的实体
     *
     * @param id 主键 ID
     * @return 实体
     */
    private @NotNull E getById(Long id) {
        PARAM_MISSING.whenNull(id, String.format(
                "查询失败，请传入%s的ID！",
                ReflectUtil.getDescription(getFirstParameterizedTypeClass())
        ));
        entityManager.clear();
        Optional<E> optional = repository.findById(id);
        if (optional.isEmpty()) {
            throw new ServiceException(DATA_NOT_FOUND, String.format("没有查询到ID为%s的%s", id, ReflectUtil.getDescription(getFirstParameterizedTypeClass())));
        }
        return optional.get();
    }

    /**
     * 加锁更新数据
     *
     * @param id       主键 ID
     * @param consumer 更新方法
     */
    public final void updateWithLock(Long id, Consumer<E> consumer) {
        transactionHelper.run(() -> {
            E forUpdate = getForUpdate(id);
            consumer.accept(forUpdate);
            updateToDatabase(forUpdate);
        });
    }

    /**
     * 加锁查询实体
     *
     * @param id 主键 ID
     * @return 实体
     */
    public final @NotNull E getForUpdate(Long id) {
        PARAM_MISSING.whenNull(id, String.format(
                "查询失败，请传入%s的ID！",
                ReflectUtil.getDescription(getFirstParameterizedTypeClass())
        ));
        entityManager.clear();
        E forUpdate = repository.getForUpdateById(id);
        DATA_NOT_FOUND.whenNull(forUpdate, String.format("没有查询到ID为%s的%s", id, ReflectUtil.getDescription(getFirstParameterizedTypeClass())));
        return forUpdate;
    }

    /**
     * 根据 ID 查询对应的实体
     *
     * @param id 主键 ID
     * @return 实体
     * @apiNote 查不到返回 {@code null}，不抛异常
     */
    private @Nullable E getByIdMaybeNull(long id) {
        try {
            return get(id);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 保存到数据库
     *
     * @param entity 待保存实体
     * @return 实体ID
     */
    private long saveToDatabaseIgnoreNull(@NotNull E entity) {
        return saveToDatabase(entity, false);
    }

    /**
     * 保存到数据库
     *
     * @param entity   待保存实体
     * @param withNull 是否保存空值
     * @return 实体ID
     */
    private long saveToDatabase(@NotNull E entity, boolean withNull) {
        checkUnique(entity);
        entity.setUpdateTime(System.currentTimeMillis());
        if (Objects.isNull(entity.getId())) {
            // 新增
            return saveAndFlush(entity);
        }
        // 有ID 走修改 且不允许修改下列字段
        E existEntity = getById(entity.getId());
        entity = withNull ? entity : getEntityForUpdate(entity, existEntity);
        return saveAndFlush(entity);
    }

    /**
     * 保存并强刷到数据库
     *
     * @param entity 保存的实体
     * @return 实体ID
     * @apiNote 仅供 {@link #saveToDatabase(E, boolean)} 调用
     */
    private long saveAndFlush(@NotNull E entity) {
        E target = ReflectUtil.newInstance(getFirstParameterizedTypeClass());
        BeanUtils.copyProperties(entity, target);
        target = beforeSaveToDatabase(target);
        target = repository.saveAndFlush(target);
        return target.getId();
    }

    /**
     * 获取用于更新的实体
     *
     * @param sourceEntity 来源实体
     * @param exist        已存在实体
     * @return 目标实体
     */
    @Contract("_, _ -> param2")
    protected @NotNull E getEntityForUpdate(@NotNull E sourceEntity, @NotNull E exist) {
        String[] updateFieldNames = getUpdateFieldNames(sourceEntity);
        BeanUtils.copyProperties(sourceEntity, exist, updateFieldNames);
        return desensitize(exist);
    }

    /**
     * 判断是否唯一
     *
     * @param entity 实体
     */
    private void checkUnique(@NotNull E entity) {
        List<Field> fields = ReflectUtil.getFieldList(getFirstParameterizedTypeClass());
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
            E search = ReflectUtil.newInstance(getFirstParameterizedTypeClass());
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
     * 获取需要更新实体的字段名称列表
     *
     * @param source 来源对象
     * @return 需要更新的属性列表
     */
    private String @NotNull [] getUpdateFieldNames(@NotNull E source) {
        // 获取Bean
        BeanWrapper srcBean = new BeanWrapperImpl(source);
        List<String> list = new ArrayList<>();
        Arrays.stream(srcBean.getPropertyDescriptors()).map(PropertyDescriptor::getName).forEach(name -> {
            // 获取属性的Field
            Field field = ReflectUtil.getField(name, source.getClass());
            if (Objects.isNull(field)) {
                // 为空 则忽略 不更新
                return;
            }
            NullEnable nullEnable = ReflectUtil.getAnnotation(NullEnable.class, field);
            if (Objects.nonNull(nullEnable) && nullEnable.value()) {
                // 标记了可以允许null更新，则跳过 不忽略
                return;
            }
            if (Objects.isNull(srcBean.getPropertyValue(name))) {
                list.add(name);
            }
        });
        return list.toArray(new String[0]);
    }

    /**
     * 创建排序对象
     *
     * @param sort 排序对象
     * @return Sort {@code Spring} 的排序对象
     */
    private @NotNull org.springframework.data.domain.Sort createSort(@Nullable Sort sort) {
        sort = Objects.requireNonNullElse(sort, new Sort());
        if (!StringUtils.hasText(sort.getField())) {
            sort.setField(curdConfig.getDefaultSortField());
        }

        if (Objects.isNull(sort.getDirection()) || !Sort.ASC.equalsIgnoreCase(sort.getDirection())) {
            // 未传入 或者传入不是明确的 ASC，那就DESC
            return org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc(sort.getField())
            );
        }
        return org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.asc(sort.getField())
        );
    }

    /**
     * 创建分页对象
     *
     * @param queryPageData 查询请求
     * @return Spring 分页对象
     */
    private @NotNull Pageable createPageable(@NotNull QueryPageRequest<E> queryPageData) {
        Page page = Objects.requireNonNullElse(queryPageData.getPage(), new Page());
        page.setPageNum(Objects.requireNonNullElse(page.getPageNum(), 1))
                .setPageSize(
                        Objects.requireNonNullElse(page.getPageSize(), curdConfig.getDefaultPageSize())
                );
        int pageNumber = Math.max(0, page.getPageNum() - 1);
        int pageSize = Math.max(1, queryPageData.getPage().getPageSize());
        return PageRequest.of(pageNumber, pageSize, createSort(queryPageData.getSort()));
    }

    /**
     * 获取查询条件列表
     *
     * @param root    {@code root}
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
    private @NotNull Specification<E> createSpecification(@NotNull E filter, boolean isEqual) {
        return (root, criteriaQuery, criteriaBuilder) ->
                createPredicate(root, criteriaQuery, criteriaBuilder, filter, isEqual);
    }

    /**
     * 创建 {@code Predicate}
     *
     * @param root          {@code root}
     * @param criteriaQuery {@code query}
     * @param builder       {@code builder}
     * @param filter        过滤器实体
     * @return 查询条件
     */
    private @Nullable Predicate createPredicate(
            @NotNull Root<E> root, CriteriaQuery<?> criteriaQuery,
            @NotNull CriteriaBuilder builder, @NotNull E filter, boolean isEqual
    ) {
        if (Objects.isNull(criteriaQuery)) {
            return null;
        }
        E lastFilter = beforeCreatePredicate(filter.copy());
        List<Predicate> predicateList = getPredicateList(root, builder, lastFilter, isEqual);

        // 添加更多自定义查询条件
        predicateList.addAll(addSearchPredicate(root, builder, filter));

        // 添加修改时间和创建时间的区间查询
        addCreateAndUpdateTimePredicate(root, builder, filter, predicateList);
        Predicate[] predicates = new Predicate[predicateList.size()];
        criteriaQuery.where(builder.and(predicateList.toArray(predicates)));
        return criteriaQuery.getRestriction();
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
}
