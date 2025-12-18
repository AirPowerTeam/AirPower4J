package cn.hamm.airpower.curd;

import cn.hamm.airpower.access.Permission;
import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.api.Extends;
import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.curd.query.QueryExport;
import cn.hamm.airpower.curd.query.QueryListRequest;
import cn.hamm.airpower.curd.query.QueryPageRequest;
import cn.hamm.airpower.curd.query.QueryPageResponse;
import cn.hamm.airpower.export.ExportConfig;
import cn.hamm.airpower.reflect.ReflectUtil;
import cn.hamm.airpower.util.TaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.curd.Curd.*;

/**
 * <h1>增删改查控制器</h1>
 *
 * @param <S> Service
 * @param <E> 实体或实体的子类
 * @author Hamm.cn
 * @apiNote 提供了 {@link Extends} 处理接口黑白名单，同时提供了一些 前置/后置 方法，可被子控制器类重写(不建议)
 */
@Slf4j
@Permission
public class CurdController<
        E extends CurdEntity<E>,
        S extends CurdService<E, R>,
        R extends ICurdRepository<E>> extends ApiController implements ICurdAction {
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    protected S service;

    @Autowired
    private ExportConfig exportConfig;

    /**
     * 创建导出任务
     */
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<E> queryListRequest) {
        Export.checkApiAvailable(this);
        QueryPageRequest<E> queryPageRequest = new QueryPageRequest<>();
        queryPageRequest.setSort(queryListRequest.getSort());
        queryPageRequest.setFilter(queryListRequest.getFilter());
        queryPageRequest.setPage(new Page().setPageSize(exportConfig.getExportPageSize()));
        queryPageRequest = beforeExportQuery(queryPageRequest);
        return Json.data(service.createExportTask(queryPageRequest), "导出任务创建成功");
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
     * 查询异步导出结果
     */
    @Description("查询异步导出结果")
    @PostMapping("queryExport")
    @Permission(authorize = false)
    public Json queryExport(@RequestBody @Validated QueryExport queryExport) {
        QueryExport.checkApiAvailable(this);
        return Json.data(service.queryExport(queryExport), "请下载导出的文件");
    }

    /**
     * 添加一条新数据接口
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeAdd(E)
     * @see #afterAdd(E, E)
     * @see #afterSaved(E, E)
     */
    @Description("添加")
    @PostMapping("add")
    public Json add(@RequestBody @Validated(WhenAdd.class) E source) {
        Add.checkApiAvailable(this);
        source.ignoreReadOnlyFields();
        source = beforeAdd(source);
        final E finalSource = source;
        final E entity = service.add(source);
        TaskUtil.run(
                () -> afterAdd(entity, finalSource),
                () -> afterSaved(entity, finalSource)
        );
        return Json.entity(entity.getId(), "添加成功");
    }

    /**
     * 修改一条已存在的数据接口
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeUpdate(E)
     * @see #afterUpdate(E, E)
     * @see #afterSaved(E, E)
     */
    @Description("修改")
    @PostMapping("update")
    public Json update(@RequestBody @Validated(WhenUpdate.class) @NotNull E source) {
        Update.checkApiAvailable(this);
        source.ignoreReadOnlyFields();
        source = beforeUpdate(source);

        final E entity = service.update(source);
        final E finalSource = source;
        TaskUtil.run(
                () -> afterUpdate(entity, finalSource),
                () -> afterSaved(entity, finalSource)
        );
        return Json.entity(entity.getId(), "修改成功");
    }

    /**
     * 删除一条已存在的数据接口
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeDelete(E)
     * @see #afterDelete(long)
     */
    @Description("删除")
    @PostMapping("delete")
    public Json delete(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        Delete.checkApiAvailable(this);
        E entity = service.get(source.getId());
        beforeDelete(entity);
        service.delete(entity.getId());
        TaskUtil.run(() -> afterDelete(entity.getId()));
        return Json.entity(entity.getId(), "删除成功");
    }

    /**
     * 查询一条详情数据
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #afterGetDetail(E)
     */
    @Description("查询详情")
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        GetDetail.checkApiAvailable(this);
        return Json.data(afterGetDetail(service.get(source.getId())));
    }

    /**
     * 禁用一条已存在的数据
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeDisable(E)
     * @see #afterDisable(E)
     */
    @Description("禁用")
    @PostMapping("disable")
    public Json disable(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        Disable.checkApiAvailable(this);
        E entity = service.get(source.getId());
        beforeDisable(entity);
        final E finalEntity = service.disable(entity.getId());
        TaskUtil.run(() -> afterDisable(finalEntity));
        return Json.entity(finalEntity.getId(), "禁用成功");
    }

    /**
     * 启用一条已存在的数据
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeEnable(E)
     * @see #afterEnable(E)
     */
    @Description("启用")
    @PostMapping("enable")
    public Json enable(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        Enable.checkApiAvailable(this);
        E entity = service.get(source.getId());
        beforeEnable(entity);
        final E finalEntity = service.enable(entity.getId());
        TaskUtil.run(() -> afterEnable(finalEntity));
        return Json.entity(finalEntity.getId(), "启用成功");
    }

    /**
     * 不分页查询
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeGetList(QueryListRequest)
     * @see #afterGetList(List)
     */
    @Description("不分页查询")
    @PostMapping("getList")
    public Json getList(@RequestBody QueryListRequest<E> queryListRequest) {
        GetList.checkApiAvailable(this);
        queryListRequest = requireQueryAndFilterNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeGetList(queryListRequest);
        return Json.data(afterGetList(service.getList(queryListRequest)));
    }

    /**
     * 分页查询
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeGetPage(QueryPageRequest)
     * @see #afterGetPage(QueryPageResponse)
     */
    @Description("分页查询")
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<E> queryPageRequest) {
        GetPage.checkApiAvailable(this);
        queryPageRequest = requireQueryAndFilterNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        return Json.data(afterGetPage(service.getPage(queryPageRequest)));
    }

    /**
     * 查询分页后置方法
     *
     * @see #getPage(QueryPageRequest)
     */
    protected QueryPageResponse<E> afterGetPage(QueryPageResponse<E> queryPageResponse) {
        return queryPageResponse;
    }

    /**
     * 查询分页前置方法
     *
     * @apiNote 可重写后重新设置查询条件
     * @see #getPage(QueryPageRequest)
     */
    protected QueryPageRequest<E> beforeGetPage(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }

    /**
     * 查询不分页前置方法
     *
     * @apiNote 可重写后重新设置查询条件
     */
    protected QueryListRequest<E> beforeGetList(QueryListRequest<E> queryListRequest) {
        return queryListRequest;
    }

    /**
     * 查询不分页后置方法
     *
     * @apiNote 可重写后执行装载更多数据的业务
     */
    protected List<E> afterGetList(List<E> list) {
        return list;
    }

    /**
     * 查询详情后置方法
     *
     * @apiNote 可重写后执行装载更多数据的业务
     */
    protected E afterGetDetail(@NotNull E entity) {
        return entity;
    }

    /**
     * 新增前置方法
     *
     * @apiNote 可重写后执行新增前的数据处理
     */
    protected E beforeAdd(@NotNull E entity) {
        return entity;
    }

    /**
     * 新增后置方法
     *
     * @param entity 保存后的实体
     * @param source 原始实体
     * @apiNote 可重写后执行新增后的其他业务
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterAdd(@NotNull E entity, @NotNull E source) {
    }

    /**
     * 修改前置方法
     *
     * @param entity 请求提交的实体数据，可能会缺失很多数据
     * @return 实体
     * @apiNote 可重写后执行修改前的其他业务或拦截
     */
    protected E beforeUpdate(@NotNull E entity) {
        return entity;
    }

    /**
     * 修改后置方法
     *
     * @param entity 修改后的实体
     * @param source 原始实体
     * @apiNote 可重写后执行修改之后的其他业务
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterUpdate(@NotNull E entity, @NotNull E source) {
    }

    /**
     * 保存后置方法
     *
     * @param entity 保存后的实体
     * @param source 原始实体
     * @apiNote 新增和修改最后触发
     */
    @SuppressWarnings("unused")
    protected void afterSaved(@NotNull E entity, @NotNull E source) {
    }

    /**
     * 删除前置方法
     *
     * @param entity 删除前的实体
     * @apiNote 可重写后执行删除之前的业务处理或拦截
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeDelete(@NotNull E entity) {
    }

    /**
     * 删除后置方法
     *
     * @param id 主键
     * @apiNote 可重写后执行删除之后的其他业务
     */
    @SuppressWarnings("unused")
    protected void afterDelete(long id) {
    }

    /**
     * 禁用前置方法
     *
     * @param entity 禁用前的实体
     * @apiNote 可重写后执行禁用之前的业务处理或拦截
     */
    @SuppressWarnings("unused")
    protected void beforeDisable(@NotNull E entity) {
    }

    /**
     * 禁用后置方法
     *
     * @param entity 禁用后的实体
     * @apiNote 可重写后执行禁用之后的其他业务
     */
    @SuppressWarnings("unused")
    protected void afterDisable(@NotNull E entity) {
    }

    /**
     * 启用前置方法
     *
     * @param entity 启用前的实体
     */
    @SuppressWarnings("unused")
    protected void beforeEnable(@NotNull E entity) {
    }

    /**
     * 启用后置方法
     *
     * @param entity 启用后的实体
     */
    @SuppressWarnings("unused")
    protected void afterEnable(@NotNull E entity) {
    }

    /**
     * 验证非空查询请求且非空过滤器请求
     *
     * @param queryListRequest 传入的查询请求
     * @param newInstance      新实例
     * @param <Q>              QueryRequest子类
     * @return 处理后的查询请求
     */
    private <Q extends QueryListRequest<E>> @NotNull Q requireQueryAndFilterNonNullElse(
            Q queryListRequest, Q newInstance) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, newInstance);
        queryListRequest.setFilter(Objects.requireNonNullElse(queryListRequest.getFilter(), ReflectUtil.newInstance(getEntityClass())));
        return queryListRequest;
    }

    /**
     * 获取实体类
     *
     * @return 类
     */
    @SuppressWarnings("unchecked")
    public final Class<E> getEntityClass() {
        return (Class<E>) ((ParameterizedType) getClass()
                .getGenericSuperclass())
                .getActualTypeArguments()[0];
    }
}
