package cn.hamm.airpower.root;

import cn.hamm.airpower.annotation.Desensitize;
import cn.hamm.airpower.annotation.ExcelColumn;
import cn.hamm.airpower.annotation.NullEnable;
import cn.hamm.airpower.annotation.Search;
import cn.hamm.airpower.config.ServiceConfig;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.helper.RedisHelper;
import cn.hamm.airpower.interfaces.IDictionary;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.model.Page;
import cn.hamm.airpower.model.Sort;
import cn.hamm.airpower.model.query.QueryExport;
import cn.hamm.airpower.model.query.QueryListRequest;
import cn.hamm.airpower.model.query.QueryPageRequest;
import cn.hamm.airpower.model.query.QueryPageResponse;
import cn.hamm.airpower.util.*;
import cn.hamm.airpower.validate.dictionary.Dictionary;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletRequest;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;

import static cn.hamm.airpower.config.Constant.*;
import static cn.hamm.airpower.enums.DateTimeFormatter.FULL_DATE;
import static cn.hamm.airpower.enums.DateTimeFormatter.FULL_TIME;
import static cn.hamm.airpower.exception.ServiceError.*;

/**
 * <h1>服务根类</h1>
 *
 * @param <E> 实体
 * @param <R> 数据源
 * @author Hamm.cn
 */
@SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection"})
@Slf4j
public class RootService<E extends RootEntity<E>, R extends RootRepository<E>> {
    /**
     * <h3>创建时间字段名 {@code createTime}</h3>
     */
    public static final String STRING_CREATE_TIME = "createTime";

    /**
     * <h3>修改时间字段名 {@code updateTime}</h3>
     */
    public static final String STRING_UPDATE_TIME = "updateTime";

    /**
     * <h3>主键 {@code ID} 字段名</h3>
     */
    public static final String STRING_ID = "id";

    /**
     * <h3>提交的数据不允许为空</h3>
     */
    private static final String DATA_REQUIRED = "提交的数据不允许为空";

    /**
     * <h3>导出文件夹前缀</h3>
     */
    private static final String EXPORT_DIR_PREFIX = "export_";

    /**
     * <h3>导出文件前缀</h3>
     */
    private static final String EXPORT_FILE_PREFIX = EXPORT_DIR_PREFIX + "file_";

    /**
     * <h3>导出文件后缀</h3>
     */
    private static final String EXPORT_FILE_CSV = ".csv";

    /**
     * <h3>数据源</h3>
     */
    @Autowired
    protected R repository;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected RedisHelper redisHelper;

    @Autowired
    protected ServiceConfig serviceConfig;

    @Autowired
    protected HttpServletRequest request;

    /**
     * <h3>创建导出任务</h3>
     *
     * @param queryListRequest 请求查询的参数
     * @return 导出任务ID
     * @see #beforeExportQuery(QueryListRequest)
     * @see #afterExportQuery(List)
     * @see #createExportStream(List)
     */
    public final String createExportTask(QueryListRequest<E> queryListRequest) {
        String fileCode = RandomUtil.randomString().toLowerCase();
        final String fileCacheKey = EXPORT_FILE_PREFIX + fileCode;
        Object object = redisHelper.get(fileCacheKey);
        if (Objects.nonNull(object)) {
            return createExportTask(queryListRequest);
        }
        redisHelper.set(fileCacheKey, "");
        TaskUtil.runAsync(() -> {
            // 查数据 写文件
            List<E> list = exportQuery(queryListRequest);
            String url = saveExportFile(createExportStream(list));
            redisHelper.set(fileCacheKey, url);
        });
        return fileCode;
    }

    /**
     * <h3>导出查询前置方法</h3>
     *
     * @param queryListRequest 查询请求
     * @return 处理后的查询请求
     */
    protected QueryListRequest<E> beforeExportQuery(QueryListRequest<E> queryListRequest) {
        return queryListRequest;
    }

    /**
     * <h3>创建导出数据的文件字节流</h3>
     *
     * @param exportList 导出的数据
     * @return 导出的文件的字节流
     * @apiNote 支持完全重写导出文件生成逻辑
     *
     * <ul>
     *     <li>默认导出为 {@code CSV} 表格，如需自定义导出方式或格式，可直接重写此方法</li>
     *     <li>如仅需{@code 自定义导出存储位置}，可重写 {@link #saveExportFile(InputStream)}</li>
     * </ul>
     */
    protected InputStream createExportStream(List<E> exportList) {
        // 导出到csv并存储文件
        Class<E> entityClass = getEntityClass();
        List<Field> fieldList = new ArrayList<>();
        ReflectUtil.getFieldList(entityClass).forEach(field -> {
            ExcelColumn excelColumn = ReflectUtil.getAnnotation(ExcelColumn.class, field);
            if (Objects.isNull(excelColumn)) {
                return;
            }
            fieldList.add(field);
        });

        List<String> rowList = new ArrayList<>();
        // 添加表头
        rowList.add(String.join(STRING_COMMA, fieldList.stream().map(ReflectUtil::getDescription).toList()));

        String json = Json.toString(exportList);
        List<Map<String, Object>> mapList = Json.parse2MapList(json);
        mapList.forEach(map -> {
            List<String> columnList = new ArrayList<>();
            fieldList.forEach(field -> {
                final String fieldName = field.getName();
                Object value = map.get(fieldName);
                value = prepareExcelColumn(fieldName, value, fieldList);
                columnList.add(value.toString()
                        .replaceAll(STRING_COMMA, STRING_BLANK)
                        .replaceAll(REGEX_LINE_BREAK, STRING_BLANK));
            });
            rowList.add(String.join(STRING_COMMA, columnList));
        });
        return new ByteArrayInputStream(String.join(REGEX_LINE_BREAK, rowList).getBytes());
    }

    /**
     * <h3>保存导出生成的文件</h3>
     *
     * @param exportFileStream 导出的文件字节流
     * @return 存储后的可访问路径
     * @apiNote 可重写此方法存储至其他地方后返回可访问绝对路径
     */
    protected String saveExportFile(InputStream exportFileStream) {
        // 准备导出的相对路径
        final String absolutePath = serviceConfig.getExportFilePath() + File.separator;
        SERVICE_ERROR.when(!StringUtils.hasText(absolutePath), "导出失败，未配置导出文件目录");
        try {
            long milliSecond = System.currentTimeMillis();

            // 追加今日文件夹 定时任务将按存储文件夹进行删除过时文件
            String todayDir = DateTimeUtil.format(milliSecond,
                    FULL_DATE.getValue()
                            .replaceAll(STRING_LINE, STRING_EMPTY)
            );
            String exportFilePath = EXPORT_DIR_PREFIX;
            exportFilePath += todayDir + File.separator;

            if (!Files.exists(Paths.get(absolutePath + exportFilePath))) {
                Files.createDirectory(Paths.get(absolutePath + exportFilePath));
            }

            // 存储的文件名
            final String fileName = todayDir + STRING_UNDERLINE + DateTimeUtil.format(milliSecond,
                    FULL_TIME.getValue()
                            .replaceAll(STRING_COLON, STRING_EMPTY)
            ) + STRING_UNDERLINE + RandomUtil.randomString() + EXPORT_FILE_CSV;

            // 拼接最终存储路径
            exportFilePath += fileName;
            Path path = Paths.get(absolutePath + exportFilePath);
            Files.write(path, exportFileStream.readAllBytes());
            return exportFilePath;
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * <h3>导出查询后置方法</h3>
     *
     * @param exportList 导出的数据列表
     * @return 处理后的数据列表
     */
    protected List<E> afterExportQuery(@NotNull List<E> exportList) {
        return exportList;
    }

    /**
     * <h3>查询导出结果</h3>
     *
     * @param queryExport 查询导出模型
     * @return 导出文件地址
     */
    protected final String queryExport(@NotNull QueryExport queryExport) {
        final String fileCacheKey = EXPORT_FILE_PREFIX + queryExport.getFileCode();
        Object object = redisHelper.get(fileCacheKey);
        DATA_NOT_FOUND.whenNull(object, "错误的FileCode");
        DATA_NOT_FOUND.whenEmpty(object, "文件暂未准备完毕");
        return object.toString();
    }

    /**
     * <h3>添加前置方法</h3>
     *
     * @param source 原始实体
     * @return 处理后的实体
     */
    protected @NotNull E beforeAdd(@NotNull E source) {
        return source;
    }

    /**
     * <h3>添加一条数据</h3>
     *
     * @param source 原始实体
     * @return 保存后的主键 {@code ID}
     * @see #beforeAdd(E)
     * @see #beforeSaveToDatabase(E)
     * @see #afterAdd(long, E)
     * @see #afterSaved(long, E)
     */
    public final long add(@NotNull E source) {
        source = beforeAdd(source);
        SERVICE_ERROR.whenNull(source, DATA_REQUIRED);
        source.setId(null).setIsDisabled(false).setCreateTime(System.currentTimeMillis());
        E finalSource = source;
        long id = saveToDatabase(source);
        TaskUtil.run(() -> afterAdd(id, finalSource));
        return id;
    }

    /**
     * <h3>添加后置方法</h3>
     *
     * @param id     主键 {@code ID}
     * @param source 原始实体
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterAdd(long id, @NotNull E source) {
    }

    /**
     * <h3>修改前置方法</h3>
     *
     * @param source 原始实体
     * @return 处理后的实体
     */
    protected @NotNull E beforeUpdate(@NotNull E source) {
        return source;
    }

    /**
     * <h3>修改一条已经存在的数据</h3>
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
     * <h3>修改一条已经存在的数据</h3>
     *
     * @param source 保存的实体
     * @apiNote 此方法的 {@code null} 属性依然会被更新到数据库
     * @see #beforeUpdate(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     * @see #update(E)
     */
    @SuppressWarnings("unused")
    public final void updateWithNull(@NotNull E source) {
        updateToDatabase(true, source);
    }

    /**
     * <h3>修改后置方法</h3>
     *
     * <p>
     * 请不要在重写此方法后再次调用 {@link #update(E)  } 与 {@link #updateWithNull(E)} 以 {@code 避免循环} 调用
     * </p>
     * <p>
     * 如需再次保存，请调用 {@link #updateToDatabase(E)}
     * </p>
     *
     * @param id     主键 {@code ID}
     * @param source 原始实体
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterUpdate(long id, @NotNull E source) {
    }

    /**
     * <h3>保存后置方法</h3>
     *
     * @param id     主键 {@code ID}
     * @param source 保存前的原数据
     * @apiNote 添加或修改后最后触发
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterSaved(long id, @NotNull E source) {

    }

    /**
     * <h3>禁用前置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeDisable(long id) {
    }

    /**
     * <h3>禁用指定的数据</h3>
     *
     * @param id 主键 {@code ID}
     * @see #beforeDisable(long)
     * @see #afterDisable(long)
     */
    public final void disable(long id) {
        beforeDisable(id);
        disableById(id);
        TaskUtil.run(() -> afterDisable(id));
    }

    /**
     * <h3>禁用后置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterDisable(long id) {
    }

    /**
     * <h3>启用前置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeEnable(long id) {
    }

    /**
     * <h3>启用指定的数据</h3>
     *
     * @param id 主键 {@code ID}
     * @see #beforeEnable(long)
     * @see #afterEnable(long)
     */
    public final void enable(long id) {
        beforeEnable(id);
        enableById(id);
        TaskUtil.run(() -> afterEnable(id));
    }

    /**
     * <h3>启用后置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterEnable(long id) {
    }

    /**
     * <h3>删除前置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeDelete(long id) {
    }

    /**
     * <h3>删除指定的数据</h3>
     *
     * @param id 主键 {@code ID}
     * @see #beforeDelete(long)
     * @see #afterDelete(long)
     */
    public final void delete(long id) {
        beforeDelete(id);
        deleteById(id);
        TaskUtil.run(() -> afterDelete(id));
    }

    /**
     * <h3>删除后置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterDelete(long id) {
    }

    /**
     * <h3>不分页查询前置方法</h3>
     *
     * @param sourceRequestData 查询条件
     * @return 处理后的查询条件
     * @see #getList(QueryListRequest)
     */
    protected @NotNull QueryListRequest<E> beforeGetList(@NotNull QueryListRequest<E> sourceRequestData) {
        return sourceRequestData;
    }

    /**
     * <h3>不分页查询数据</h3>
     *
     * @param queryListRequest 请求的request
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
     * <h3>过滤数据</h3>
     *
     * @param filter 全匹配过滤器
     * @return List数据
     */
    public final @NotNull List<E> filter(@Nullable E filter) {
        return filter(filter, null);
    }

    /**
     * <h3>过滤数据</h3>
     *
     * @param filter 全匹配过滤器
     * @param sort   排序
     * @return List数据
     */
    public final @NotNull List<E> filter(@Nullable E filter, @Nullable Sort sort) {
        filter = Objects.requireNonNullElse(filter, getEntityInstance());
        return repository.findAll(createSpecification(filter, true), createSort(sort));
    }

    /**
     * <h3>不分页查询后置方法</h3>
     *
     * @param list 查询到的数据
     * @return 处理后的数据
     * @see #getPage(QueryPageRequest)
     */
    protected @NotNull List<E> afterGetList(@NotNull List<E> list) {
        return list;
    }

    /**
     * <h3>分页查询前置方法</h3>
     *
     * @param sourceRequestData 原始请求的数据
     * @return 处理后的请求数据
     */
    protected @NotNull QueryPageRequest<E> beforeGetPage(@NotNull QueryPageRequest<E> sourceRequestData) {
        return sourceRequestData;
    }

    /**
     * <h3>分页查询后置方法</h3>
     *
     * @param queryPageResponse 查询到的数据
     * @return 处理后的数据
     */
    protected @NotNull QueryPageResponse<E> afterGetPage(@NotNull QueryPageResponse<E> queryPageResponse) {
        return queryPageResponse;
    }

    /**
     * <h3>数据库操作前的{@code 最后一次}确认</h3>
     *
     * @return 当前实体
     */
    protected @NotNull E beforeSaveToDatabase(@NotNull E entity) {
        return entity;
    }

    /**
     * <h3>添加搜索的查询条件</h3>
     *
     * @param root    {@code ROOT}
     * @param builder 参数构造器
     * @param search  原始查询对象
     * @return 查询条件列表
     * @apiNote 如需要删除自动添加的查询条件，请调用 {@link #beforeCreatePredicate(RootEntity)}
     */
    @SuppressWarnings("unused")
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<E> root,
            @NotNull CriteriaBuilder builder,
            @NotNull E search
    ) {
        return new ArrayList<>();
    }

    /**
     * <h3>根据{@code ID}查询对应的实体</h3>
     *
     * @param id 主键 {@code ID}
     * @return 实体
     * @see #getMaybeNull(long)
     * @see #getWithEnable(long)
     */
    public final @NotNull E get(long id) {
        return afterGet(getById(id));
    }

    /**
     * <h3>根据{@code ID}查询正常启用的实体</h3>
     *
     * @param id 主键 {@code ID}
     * @return 实体
     * @see #get(long)
     * @see #getMaybeNull(long)
     */
    public final @NotNull E getWithEnable(long id) {
        E entity = get(id);
        FORBIDDEN_DISABLED.when(
                entity.getIsDisabled(),
                String.format(FORBIDDEN_DISABLED.getMessage(), id, ReflectUtil.getDescription(getEntityClass()))
        );
        return entity;
    }

    /**
     * <h3>根据{@code ID}查询对应的实体(可能为{@code null})</h3>
     *
     * @param id 主键 {@code ID}
     * @return 实体
     * @apiNote 查不到返回 {@code null}，不抛异常
     * @see #get(long)
     */
    public final @Nullable E getMaybeNull(long id) {
        return afterGet(getByIdMaybeNull(id));
    }

    /**
     * <h3>详情查询后置方法</h3>
     *
     * @param result 查到的数据
     * @return 处理后的数据
     */
    protected E afterGet(E result) {
        return result;
    }

    /**
     * <h3>分页查询数据</h3>
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
        QueryPageResponse<E> queryPageResponse = getResponsePageList(pageData);
        queryPageResponse.setSort(queryPageRequest.getSort());
        return afterGetPage(queryPageResponse);
    }

    /**
     * <h3>禁用指定的数据</h3>
     *
     * @param id 主键 {@code ID}
     * @apiNote 不建议直接调用, 请优先使用前后置方法
     * @see #beforeDisable(long)
     * @see #afterDisable(long)
     */
    protected final void disableById(long id) {
        E entity = get(id);
        saveToDatabase(entity.setIsDisabled(true));
    }

    /**
     * <h3>启用指定的数据</h3>
     *
     * @param id 主键 {@code ID}
     * @apiNote 不建议直接调用, 请优先使用前后置方法
     * @see #beforeEnable(long)
     * @see #afterEnable(long)
     */
    protected final void enableById(long id) {
        E entity = get(id);
        saveToDatabase(entity.setIsDisabled(false));
    }

    /**
     * <h3>删除指定的数据</h3>
     *
     * @param id 主键 {@code ID}
     * @apiNote 不建议直接调用, 请优先使用前后置方法
     * @see #beforeDelete(long)
     * @see #afterDelete(long)
     */
    protected final void deleteById(long id) {
        repository.deleteById(id);
    }

    /**
     * <h3>更新到数据库(不触发前后置)</h3>
     *
     * @param source 原始实体
     * @see #update(E)
     * @see #updateWithNull(E)
     */
    protected final void updateToDatabase(@NotNull E source) {
        updateToDatabase(source, false);
    }

    /**
     * <h3>更新到数据库(触发前后置)</h3>
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
                ReflectUtil.getDescription(getEntityClass())
        ));
        saveToDatabase(source, withNull);
    }

    /**
     * <h3>添加查询条件({@code value}不为{@code null}时)</h3>
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
     * <h3>导出查询</h3>
     *
     * @param queryListRequest 查询请求
     * @return 查询结果
     */
    private @NotNull List<E> exportQuery(QueryListRequest<E> queryListRequest) {
        queryListRequest = requireWithFilterNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeExportQuery(queryListRequest);
        List<E> list = query(queryListRequest);
        return afterExportQuery(list);
    }

    /**
     * <h3>查询数据</h3>
     *
     * @param queryListRequest 查询请求
     * @return 查询结果数据列表
     */
    private @NotNull List<E> query(@NotNull QueryListRequest<E> queryListRequest) {
        return repository.findAll(
                createSpecification(queryListRequest.getFilter(), false), createSort(queryListRequest.getSort())
        );
    }

    /**
     * <h3>准备导出列</h3>
     *
     * @param fieldName 字段名
     * @param value     当前值
     * @param fieldList 字段列表
     * @return 处理后的值
     */
    private @NotNull Object prepareExcelColumn(String fieldName, Object value, List<Field> fieldList) {
        if (Objects.isNull(value) || !StringUtils.hasText(value.toString())) {
            value = STRING_LINE;
        }
        try {
            Field field = fieldList.stream()
                    .filter(item -> Objects.equals(item.getName(), fieldName))
                    .findFirst()
                    .orElse(null);
            if (Objects.isNull(field)) {
                return value;
            }
            ExcelColumn excelColumn = ReflectUtil.getAnnotation(ExcelColumn.class, field);
            if (Objects.isNull(excelColumn)) {
                return value;
            }
            return switch (excelColumn.value()) {
                case DATETIME -> REGEX_TAB + DateTimeUtil.format(Long.parseLong(value.toString()));
                case TEXT -> REGEX_TAB + value;
                case BOOLEAN -> (boolean) value ? STRING_YES : STRING_NO;
                case DICTIONARY -> {
                    Dictionary dictionary = ReflectUtil.getAnnotation(Dictionary.class, field);
                    if (Objects.isNull(dictionary)) {
                        yield value;
                    } else {
                        IDictionary dict = DictionaryUtil.getDictionary(
                                dictionary.value(), Integer.parseInt(value.toString())
                        );
                        yield dict.getLabel();
                    }
                }
                default -> value;
            };
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            return value;
        }
    }

    /**
     * <h3>验证非空查询请求且非空过滤器请求</h3>
     *
     * @param queryListRequest 查询请求
     * @param newInstance      新实例
     * @return 检查后的查询请求
     */
    private <Q extends QueryListRequest<E>> @NotNull Q requireWithFilterNonNullElse(
            Q queryListRequest, Q newInstance) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, newInstance);
        queryListRequest.setFilter(Objects.requireNonNullElse(queryListRequest.getFilter(), getEntityInstance()));
        return queryListRequest;
    }

    /**
     * <h3>更新到数据库</h3>
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

    /**
     * <h3>根据{@code ID}查询对应的实体</h3>
     *
     * @param id 主键 {@code ID}
     * @return 实体
     */
    private @NotNull E getById(long id) {
        PARAM_MISSING.whenNull(id, String.format(
                "查询失败，请传入%s的ID！",
                ReflectUtil.getDescription(getEntityClass())
        ));
        return repository.findById(id).orElseThrow(() ->
                new ServiceException(
                        DATA_NOT_FOUND,
                        String.format("没有查询到ID为%s的%s", id, ReflectUtil.getDescription(getEntityClass()))
                )
        );
    }

    /**
     * <h3>根据ID查询对应的实体</h3>
     *
     * @param id 主键 {@code ID}
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
     * <h3>保存到数据库</h3>
     *
     * @param entity 待保存实体
     * @return 实体ID
     */
    private long saveToDatabase(@NotNull E entity) {
        return saveToDatabase(entity, false);
    }

    /**
     * <h3>保存到数据库</h3>
     *
     * @param entity   待保存实体
     * @param withNull 是否保存空值
     * @return 实体ID
     */
    private long saveToDatabase(@NotNull E entity, boolean withNull) {
        checkUnique(entity);
        entity.setUpdateTime(System.currentTimeMillis());
        if (Objects.nonNull(entity.getId())) {
            // 修改前清掉JPA缓存，避免查询到旧数据
            entityManager.clear();
            // 有ID 走修改 且不允许修改下列字段
            E existEntity = getById(entity.getId());
            entity = withNull ? entity : getEntityForUpdate(entity, existEntity);
        }
        if (Objects.isNull(entity.getId())) {
            // 新增
            return saveAndFlush(entity);
        }
        // 修改前清掉JPA缓存，避免查询到旧数据
        entityManager.clear();
        // 有ID 走修改 且不允许修改下列字段
        E existEntity = getById(entity.getId());
        entity = withNull ? entity : getEntityForUpdate(entity, existEntity);
        return saveAndFlush(entity);
    }

    /**
     * <h3>保存并强刷到数据库</h3>
     *
     * @param entity 保存的实体
     * @return 实体ID
     * @apiNote 仅供 {@link #saveToDatabase(E, boolean)} 调用
     */
    private long saveAndFlush(@NotNull E entity) {
        E target = getEntityInstance();
        BeanUtils.copyProperties(entity, target);
        target = beforeSaveToDatabase(target);
        target = repository.saveAndFlush(target);
        // 新增完毕，清掉查询缓存，避免查询到旧数据
        entityManager.clear();
        return target.getId();
    }

    /**
     * <h3>获取用于更新的实体</h3>
     *
     * @param sourceEntity 来源实体
     * @param exist        已存在实体
     * @return 目标实体
     */
    @Contract("_, _ -> param2")
    protected final @NotNull E getEntityForUpdate(@NotNull E sourceEntity, @NotNull E exist) {
        String[] updateFieldNames = getUpdateFieldNames(sourceEntity);
        BeanUtils.copyProperties(sourceEntity, exist, updateFieldNames);
        return desensitize(exist);
    }

    /**
     * <h3>脱敏</h3>
     *
     * @param exist 待脱敏实体
     * @return 脱敏后的实体
     */
    @Contract("_ -> param1")
    private E desensitize(@NotNull E exist) {
        List<Field> fieldList = ReflectUtil.getFieldList(getEntityClass());
        fieldList.forEach(field -> {
            Desensitize desensitize = ReflectUtil.getAnnotation(Desensitize.class, field);
            if (Objects.isNull(desensitize)) {
                // 非脱敏注解标记属性
                return;
            }
            // 脱敏字段
            Object fieldValue = ReflectUtil.getFieldValue(exist, field);
            if (Objects.isNull(fieldValue)) {
                // 值本身是空的
                return;
            }
            if (desensitize.replace() && Objects.equals(desensitize.symbol(), fieldValue.toString())) {
                // 如果是替换 且没有修改内容
                ReflectUtil.setFieldValue(exist, field, null);
            }
            if (!desensitize.replace() && fieldValue.toString().contains(desensitize.symbol())) {
                // 如果值包含脱敏字符
                ReflectUtil.setFieldValue(exist, field, null);
            }
        });
        return exist;
    }

    /**
     * <h3>判断是否唯一</h3>
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
     * <h3>获取一个空实体对象</h3>
     *
     * @return 实体
     */
    @NotNull
    public E getEntityInstance() {
        try {
            return getEntityClass().getConstructor().newInstance();
        } catch (Exception exception) {
            throw new ServiceException(exception.getMessage());
        }
    }

    /**
     * <h3>获取实体类</h3>
     *
     * @return 类
     */
    private @NotNull Class<E> getEntityClass() {
        //noinspection unchecked
        return (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    /**
     * <h3>获取需要更新实体的字段名称列表</h3>
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
     * <h3>获取响应的分页数据</h3>
     *
     * @param data 分页数据
     * @return 输出分页对象
     */
    private @NotNull QueryPageResponse<E> getResponsePageList(
            @NotNull org.springframework.data.domain.Page<E> data
    ) {
        return new QueryPageResponse<E>()
                .setList(data.getContent())
                .setTotal(Math.toIntExact(data.getTotalElements()))
                .setPageCount(data.getTotalPages())
                .setPage(new Page()
                        .setPageSize(data.getPageable().getPageSize())
                        .setPageNum(data.getPageable().getPageNumber() + 1)
                );
    }

    /**
     * <h3>创建{@code Sort}</h3>
     *
     * @param sort 排序对象
     * @return Sort {@code Spring} 的排序对象
     */
    private @NotNull org.springframework.data.domain.Sort createSort(@Nullable Sort sort) {
        sort = Objects.requireNonNullElse(sort, new Sort());
        if (!StringUtils.hasText(sort.getField())) {
            sort.setField(serviceConfig.getDefaultSortField());
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
     * <h3>创建{@code Pageable}</h3>
     *
     * @param queryPageData 查询请求
     * @return Pageable
     */
    private @NotNull Pageable createPageable(@NotNull QueryPageRequest<E> queryPageData) {
        Page page = Objects.requireNonNullElse(queryPageData.getPage(), new Page());
        page.setPageNum(Objects.requireNonNullElse(page.getPageNum(), 1))
                .setPageSize(
                        Objects.requireNonNullElse(page.getPageSize(), serviceConfig.getDefaultPageSize())
                );
        int pageNumber = Math.max(0, page.getPageNum() - 1);
        int pageSize = Math.max(1, queryPageData.getPage().getPageSize());
        return PageRequest.of(pageNumber, pageSize, createSort(queryPageData.getSort()));
    }

    /**
     * <h3>获取查询条件列表</h3>
     *
     * @param root    {@code root}
     * @param builder {@code builder}
     * @param search  搜索实体
     * @param isEqual 是否强匹配
     * @return 搜索条件
     */
    private @NotNull List<jakarta.persistence.criteria.Predicate> getPredicateList(
            @NotNull From<?, ?> root, @NotNull CriteriaBuilder builder, @NotNull Object search, boolean isEqual
    ) {
        List<Field> fields = ReflectUtil.getFieldList(search.getClass());
        List<Predicate> predicateList = new ArrayList<>();
        fields.forEach(field -> {
            Object fieldValue = ReflectUtil.getFieldValue(search, field);
            if (Objects.isNull(fieldValue) || !StringUtils.hasText(fieldValue.toString())) {
                // 没有传入查询值 空字符串 跳过
                return;
            }
            Search searchMode = ReflectUtil.getAnnotation(Search.class, field);
            if (Objects.isNull(searchMode)) {
                // 没有配置查询注解 跳过
                return;
            }
            switch (searchMode.value()) {
                case JOIN:
                    Join<?, ?> payload = root.join(field.getName(), JoinType.INNER);
                    predicateList.addAll(getPredicateList(payload, builder, fieldValue, isEqual));
                    break;
                case LIKE:
                    if (!isEqual) {
                        // 如果是模糊匹配
                        predicateList.add(
                                builder.like(root.get(field.getName()), fieldValue + STRING_PERCENT)
                        );
                        break;
                    }
                    // 如果不是模糊匹配，走到default分支
                default:
                    // 强匹配
                    Predicate predicate = builder.equal(root.get(field.getName()), fieldValue);
                    predicateList.add(predicate);
            }
        });
        return predicateList;
    }

    /**
     * <h3>添加创建时间和更新时间的查询条件</h3>
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
                STRING_CREATE_TIME, builder::greaterThanOrEqualTo, search.getCreateTimeFrom()
        );
        addPredicateNonNull(root, predicateList,
                STRING_CREATE_TIME, builder::lessThan, search.getCreateTimeTo()
        );
        addPredicateNonNull(root, predicateList,
                STRING_UPDATE_TIME, builder::greaterThanOrEqualTo, search.getUpdateTimeFrom()
        );
        addPredicateNonNull(root, predicateList,
                STRING_UPDATE_TIME, builder::lessThan, search.getUpdateTimeTo()
        );
    }

    /**
     * <h3>创建查询对象</h3>
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
     * <h3>创建 {@code Predicate}</h3>
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
        predicateList.addAll(addSearchPredicate(root, builder, filter));
        addCreateAndUpdateTimePredicate(root, builder, filter, predicateList);
        Predicate[] predicates = new Predicate[predicateList.size()];
        criteriaQuery.where(builder.and(predicateList.toArray(predicates)));
        return criteriaQuery.getRestriction();
    }

    /**
     * <h3>在创建查询条件前调用</h3>
     *
     * @param filter 过滤器
     * @return 处理后的过滤器
     * @apiNote 此处理不影响 {@link #addSearchPredicate(Root, CriteriaBuilder, RootEntity)} 的 {@code search} 参数
     */
    protected E beforeCreatePredicate(@NotNull E filter) {
        return filter;
    }
}
