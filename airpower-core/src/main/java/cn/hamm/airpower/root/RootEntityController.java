package cn.hamm.airpower.root;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.annotation.Extends;
import cn.hamm.airpower.annotation.Filter;
import cn.hamm.airpower.annotation.Permission;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.interfaces.IEntityAction;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.model.query.QueryExport;
import cn.hamm.airpower.model.query.QueryListRequest;
import cn.hamm.airpower.model.query.QueryPageRequest;
import cn.hamm.airpower.model.query.QueryPageResponse;
import cn.hamm.airpower.util.TaskUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.enums.Api.*;

/**
 * <h1>实体控制器根类</h1>
 *
 * @param <S> Service
 * @param <E> 实体或实体的子类
 * @author Hamm.cn
 * @apiNote 提供了 {@link Extends} 处理接口黑白名单，同时提供了一些 前置/后置 方法，可被子控制器类重写(不建议)
 */
@Permission
public class RootEntityController<
        E extends RootEntity<E>,
        S extends RootService<E, R>,
        R extends RootRepository<E>> extends RootController implements IEntityAction {
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    protected S service;

    /**
     * <h3>创建导出任务</h3>
     */
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<E> queryListRequest) {
        Export.checkApiAvailable(this);
        return Json.data(service.createExportTask(queryListRequest), "导出任务创建成功");
    }

    /**
     * <h3>查询异步导出结果</h3>
     */
    @Description("查询异步导出结果")
    @PostMapping("queryExport")
    @Permission(authorize = false)
    public Json queryExport(@RequestBody @Validated QueryExport queryExport) {
        QueryExport.checkApiAvailable(this);
        return Json.data(service.queryExport(queryExport), "请下载导出的文件");
    }

    /**
     * <h3>添加一条新数据接口</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeAdd(E)
     * @see #afterAdd(long, E)
     * @see #afterSaved(long, E)
     */
    @Description("添加")
    @PostMapping("add")
    @Filter(WhenGetDetail.class)
    public Json add(@RequestBody @Validated(WhenAdd.class) E source) {
        Add.checkApiAvailable(this);
        source.ignoreReadOnlyFields();
        source = beforeAdd(source);
        final E finalSource = source;
        long id = service.add(source);
        TaskUtil.run(
                () -> afterAdd(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
        return Json.entity(id, "添加成功");
    }

    /**
     * <h3>修改一条已存在的数据接口</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeUpdate(E)
     * @see #afterUpdate(long, E)
     * @see #afterSaved(long, E)
     */
    @Description("修改")
    @PostMapping("update")
    @Filter(WhenGetDetail.class)
    public Json update(@RequestBody @Validated(WhenUpdate.class) @NotNull E source) {
        Update.checkApiAvailable(this);
        long id = source.getId();
        source.ignoreReadOnlyFields();
        source = beforeUpdate(source);
        final E finalSource = source;
        service.update(source);
        TaskUtil.run(
                () -> afterUpdate(id, finalSource),
                () -> afterSaved(id, finalSource)
        );
        return Json.entity(id, "修改成功");
    }

    /**
     * <h3>删除一条已存在的数据接口</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeDelete(long)
     * @see #afterDelete(long)
     */
    @Description("删除")
    @PostMapping("delete")
    public Json delete(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        Delete.checkApiAvailable(this);
        long id = source.getId();
        beforeDelete(id);
        service.delete(id);
        TaskUtil.run(() -> afterDelete(id));
        return Json.entity(id, "删除成功");
    }

    /**
     * <h3>查询一条详情数据</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #afterGetDetail(E)
     */
    @Description("查询详情")
    @PostMapping("getDetail")
    @Filter(WhenGetDetail.class)
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        GetDetail.checkApiAvailable(this);
        return Json.data(afterGetDetail(service.get(source.getId())));
    }

    /**
     * <h3>禁用一条已存在的数据</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeDisable(long)
     * @see #afterDisable(long)
     */
    @Description("禁用")
    @PostMapping("disable")
    public Json disable(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        Disable.checkApiAvailable(this);
        long id = source.getId();
        beforeDisable(id);
        service.disable(id);
        TaskUtil.run(() -> afterDisable(id));
        return Json.entity(source.getId(), "禁用成功");
    }

    /**
     * <h3>启用一条已存在的数据</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeEnable(long)
     * @see #afterEnable(long)
     */
    @Description("启用")
    @PostMapping("enable")
    public Json enable(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
        Enable.checkApiAvailable(this);
        long id = source.getId();
        beforeEnable(id);
        service.enable(id);
        TaskUtil.run(() -> afterEnable(id));
        return Json.entity(source.getId(), "启用成功");
    }

    /**
     * <h3>不分页查询</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeGetList(QueryListRequest)
     * @see #afterGetList(List)
     */
    @Description("不分页查询")
    @PostMapping("getList")
    @Filter(WhenGetList.class)
    public Json getList(@RequestBody QueryListRequest<E> queryListRequest) {
        GetList.checkApiAvailable(this);
        queryListRequest = requireQueryAndFilterNonNullElse(queryListRequest, new QueryListRequest<>());
        queryListRequest = beforeGetList(queryListRequest);
        return Json.data(afterGetList(service.getList(queryListRequest)));
    }

    /**
     * <h3>分页查询</h3>
     *
     * @apiNote 可被子控制器类注解 {@link Extends} 继承或忽略，不建议重写，可使用前后置方法来处理业务逻辑。
     * @see #beforeGetPage(QueryPageRequest)
     * @see #afterGetPage(QueryPageResponse)
     */
    @Description("分页查询")
    @PostMapping("getPage")
    @Filter(WhenGetPage.class)
    public Json getPage(@RequestBody QueryPageRequest<E> queryPageRequest) {
        GetPage.checkApiAvailable(this);
        queryPageRequest = requireQueryAndFilterNonNullElse(queryPageRequest, new QueryPageRequest<>());
        queryPageRequest = beforeGetPage(queryPageRequest);
        return Json.data(afterGetPage(service.getPage(queryPageRequest)));
    }

    /**
     * <h3>查询分页后置方法</h3>
     *
     * @see #getPage(QueryPageRequest)
     */
    protected QueryPageResponse<E> afterGetPage(QueryPageResponse<E> queryPageResponse) {
        return queryPageResponse;
    }

    /**
     * <h3>查询分页前置方法</h3>
     *
     * @apiNote 可重写后重新设置查询条件
     * @see #getPage(QueryPageRequest)
     */
    protected QueryPageRequest<E> beforeGetPage(QueryPageRequest<E> queryPageRequest) {
        return queryPageRequest;
    }

    /**
     * <h3>查询不分页前置方法</h3>
     *
     * @apiNote 可重写后重新设置查询条件
     */
    protected QueryListRequest<E> beforeGetList(QueryListRequest<E> queryListRequest) {
        return queryListRequest;
    }

    /**
     * <h3>查询不分页后置方法</h3>
     *
     * @apiNote 可重写后执行装载更多数据的业务
     */
    protected List<E> afterGetList(List<E> list) {
        return list;
    }

    /**
     * <h3>查询详情后置方法</h3>
     *
     * @apiNote 可重写后执行装载更多数据的业务
     */
    protected E afterGetDetail(E entity) {
        return entity;
    }

    /**
     * <h3>新增前置方法</h3>
     *
     * @apiNote 可重写后执行新增前的数据处理
     */
    protected E beforeAdd(E entity) {
        return entity;
    }

    /**
     * <h3>新增后置方法</h3>
     *
     * @param id     主键 {@code ID}
     * @param source 原始实体
     * @apiNote 可重写后执行新增后的其他业务
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterAdd(long id, E source) {
    }

    /**
     * <h3>修改前置方法</h3>
     *
     * @param entity Api请求提交的实体数据，可能会缺失很多数据
     * @return 实体
     * @apiNote 可重写后执行修改前的其他业务或拦截
     */
    protected E beforeUpdate(E entity) {
        return entity;
    }

    /**
     * <h3>修改后置方法</h3>
     *
     * @param id     主键 {@code ID}
     * @param source 原始实体
     * @apiNote 可重写后执行修改之后的其他业务
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterUpdate(long id, E source) {
    }

    /**
     * <h3>保存后置方法</h3>
     *
     * @param id     主键 {@code ID}
     * @param source 原始实体
     * @apiNote 新增和修改最后触发
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterSaved(long id, E source) {
    }

    /**
     * <h3>删除前置方法</h3>
     *
     * @param id 主键 {@code ID}
     * @apiNote 可重写后执行删除之前的业务处理或拦截
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeDelete(long id) {
    }

    /**
     * 删除后置方法
     *
     * @param id 主键 {@code ID}
     * @apiNote 可重写后执行删除之后的其他业务
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterDelete(long id) {
    }

    /**
     * <h3>禁用前置方法</h3>
     *
     * @param id 主键 {@code ID}
     * @apiNote 可重写后执行禁用之前的业务处理或拦截
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void beforeDisable(long id) {
    }

    /**
     * <h3>禁用后置方法</h3>
     *
     * @param id 主键 {@code ID}
     * @apiNote 可重写后执行禁用之后的其他业务
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
     * <h3>启用后置方法</h3>
     *
     * @param id 主键 {@code ID}
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    protected void afterEnable(long id) {
    }

    /**
     * <h3>验证非空查询请求且非空过滤器请求</h3>
     *
     * @param queryListRequest 传入的查询请求
     * @param newInstance      新实例
     * @param <Q>              QueryRequest子类
     * @return 处理后的查询请求
     */
    private <Q extends QueryListRequest<E>> @NotNull Q requireQueryAndFilterNonNullElse(
            Q queryListRequest, Q newInstance) {
        queryListRequest = Objects.requireNonNullElse(queryListRequest, newInstance);
        queryListRequest.setFilter(Objects.requireNonNullElse(queryListRequest.getFilter(), getNewEntity()));
        return queryListRequest;
    }

    /**
     * <h3>获取一个空实体</h3>
     *
     * @return 实体
     */
    private @NotNull E getNewEntity() {
        try {
            return getEntityClass().getConstructor().newInstance();
        } catch (java.lang.Exception exception) {
            throw new ServiceException(exception.getMessage());
        }
    }

    /**
     * <h3>获取实体类</h3>
     *
     * @return 类
     */
    @SuppressWarnings("unchecked")
    private Class<E> getEntityClass() {
        return (Class<E>) ((ParameterizedType) getClass()
                .getGenericSuperclass())
                .getActualTypeArguments()[0];
    }
}
