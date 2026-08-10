# airpower-curd 使用文档

> AirPower CURD 模块 - 通用增删改查的「灵魂」：控制器基类 `CurdController`、服务基类 `CurdService`、实体基类 `CurdEntity`
> 、Query 模型、注解、拦截器、权限扫描与异步导出全在内。

## 一、模块定位

`airpower-curd` 提供了基于 JPA + Spring Data 的「约定优于配置」CURD 体系，业务侧只需继承 `CurdEntity` + `ICurdRepository` +
`CurdService` + `CurdController`，即可获得完整的 RESTful 接口。

核心组件一览：

| 组件                                                                                                          | 路径                                                              | 作用                                                                            |
|---------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `CurdEntity<E>`                                                                                               | `cn.hamm.airpower.curd.base`                                      | JPA `@MappedSuperclass`，自带 `id` / `isDisabled` / `createTime` / `updateTime` |
| `ICurdRepository<E>`                                                                                          | `cn.hamm.airpower.curd.base`                                      | JPA 仓库接口，扩展了 `getForUpdateById` 悲观锁                                  |
| `CurdService<E, R>`                                                                                           | `cn.hamm.airpower.curd.base`                                      | CURD 业务基类，封装前后置 / 唯一校验 / 模糊搜索                                 |
| `CurdController<E, S, R>`                                                                                     | `cn.hamm.airpower.curd.base`                                      | REST 控制器基类，10 个开箱即用接口                                              |
| `Curd` 枚举 / `@Extends` 注解                                                                                 | `cn.hamm.airpower.curd.base` / `cn.hamm.airpower.curd.annotation` | 子控制器精细控制父类接口                                                        |
| `QueryRequest` / `QueryListRequest` / `QueryPageRequest` / `QueryPageResponse` / `Page` / `Sort` / `PageData` | `cn.hamm.airpower.curd.model.query`                               | 标准化查询请求 / 响应模型                                                       |
| `ExportHelper` / `TransactionHelper`                                                                          | `cn.hamm.airpower.curd.helper`                                    | 异步导出、事务封装                                                              |
| `CurdRequestInterceptor` / `CurdResponseInterceptor` / `ExceptionInterceptor`                                 | `cn.hamm.airpower.curd.interceptor`                               | 全局拦截器                                                                      |
| `Permission` / `@Permission` / `IPermission` / `PermissionUtil`                                               | `cn.hamm.airpower.curd.permission`                                | RBAC 权限扫描与判定                                                             |
| `CurdConfig` / `ExportConfig` / `AccessConfig`                                                                | `cn.hamm.airpower.curd.config`                                    | 全局配置                                                                        |
| `CurdUtil.scanEntity(...)`                                                                                    | `cn.hamm.airpower.curd.base`                                      | 扫描 `*Entity.class` 反射生成实体元信息                                         |

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-curd</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

依赖已经传递引入了 `airpower-core` / `airpower-api` / `airpower-cookie` / `airpower-redis`，以及 JPA、Validation、MySQL
Connector。

## 三、应用配置

```yaml
airpower:
  curd:
    default-page-size: 20
    default-sort-field: id
    export:
      export-page-size: 5000
      export-path: /home/static/export   # 异步导出文件保存目录
    access:
      authorize-expire-second: 86400     # AccessToken 默认有效期
```

源码：[CurdConfig.java](src/main/java/cn/hamm/airpower/curd/config/CurdConfig.java)、[ExportConfig.java](src/main/java/cn/hamm/airpower/curd/config/ExportConfig.java)、[AccessConfig.java](src/main/java/cn/hamm/airpower/curd/config/AccessConfig.java)。

> 还需要在 `application.yml` 中配置 `spring.datasource.*` / `spring.jpa.hibernate.ddl-auto=create-drop` 等基础 JPA 设定。

## 四、定义一个 CURD 业务模块（完整示例）

### 4.1 实体

```java
package com.example.app.entity;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Search;
import cn.hamm.airpower.curd.base.CurdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Description("用户")
public class UserEntity extends CurdEntity<UserEntity> {

    @Description("昵称")
    @Search
    @Column(columnDefinition = "varchar(64) default '' comment '昵称'")
    private String nickname;

    @Description("邮箱")
    @Column(unique = true, columnDefinition = "varchar(128) default '' comment '邮箱'")
    private String email;
}
```

- 必须继承 `CurdEntity`，框架会自动维护 `id` / `isDisabled` / `createTime` / `updateTime`。
- `@Search` 标注的字段会被识别为左模糊（`fullLike = true` 切换为全模糊）。
- `@Column(unique = true)` 会在保存前自动唯一性校验。

### 4.2 仓库

```java
package com.example.app.repository;

import cn.hamm.airpower.curd.base.ICurdRepository;
import com.example.app.entity.UserEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends ICurdRepository<UserEntity> {
}
```

### 4.3 服务

```java
package com.example.app.service;

import cn.hamm.airpower.curd.base.CurdService;
import com.example.app.entity.UserEntity;
import com.example.app.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService extends CurdService<UserEntity, UserRepository> {
}
```

可选前后置钩子（全部继承自 `CurdService`）：

- 新增：`beforeAdd` / `afterAdd` / `afterSaved`
- 修改：`beforeUpdate` / `afterUpdate` / `afterSaved`
- 删除：`beforeDelete` / `afterDelete`
- 启用 / 禁用：`beforeEnable` / `afterEnable` / `beforeDisable` / `afterDisable`
- 查询：`beforeGetList` / `afterGetList` / `beforeGetPage` / `afterGetPage` / `beforeCreatePredicate` /
  `addSearchPredicate`

### 4.4 控制器

```java
package com.example.app.controller;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.curd.annotation.Extends;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.base.CurdController;
import com.example.app.entity.UserEntity;
import com.example.app.service.UserService;
import com.example.app.repository.UserRepository;

@Api("/user")
@Extends(value = {Curd.Add, Curd.Update, Curd.Delete, Curd.GetPage, Curd.GetList})
public class UserController extends CurdController<UserEntity, UserService, UserRepository> {
}
```

> `@Extends` 默认白名单优先：未声明则继承全部父类接口；声明 `value` 后仅暴露指定接口；声明 `exclude` 则排除指定接口。

## 五、Query 模型

### 5.1 不分页查询

```json
{
  "filter": {
    "nickname": "hamm"
  },
  "sort": {
    "field": "createTime",
    "direction": "desc"
  }
}
```

请求类 `QueryListRequest<M>` ⇒ 服务 `service.getList(queryListRequest)` ⇒ 返回 `List<E>`。

### 5.2 分页查询

```json
{
  "filter": {
    "email": "hamm@hamm.cn"
  },
  "sort": {
    "field": "id",
    "direction": "asc"
  },
  "page": {
    "pageNum": 1,
    "pageSize": 20
  }
}
```

请求类 `QueryPageRequest<M>` ⇒ 服务 `service.getPage(queryPageRequest)` ⇒ 返回 `QueryPageResponse<M>`，包含 `list` /
`total` / `pageCount` / `page`。

> `QueryHelper` 会自动追加 `createTime desc + id desc` 的兜底排序，避免重复数据。

## 六、`Curd` 枚举与 `@Extends`

`Curd` 枚举（与 `CurdController` 内方法一一对应）：

| Curd          | HTTP | URL 后缀       | 校验分组         |
|---------------|------|----------------|------------------|
| `Add`         | POST | `/add`         | `WhenAdd`        |
| `Update`      | POST | `/update`      | `WhenUpdate`     |
| `Delete`      | POST | `/delete`      | `WhenIdRequired` |
| `GetDetail`   | POST | `/getDetail`   | `WhenIdRequired` |
| `GetList`     | POST | `/getList`     | -                |
| `GetPage`     | POST | `/getPage`     | -                |
| `Disable`     | POST | `/disable`     | `WhenIdRequired` |
| `Enable`      | POST | `/enable`      | `WhenIdRequired` |
| `Export`      | POST | `/export`      | -                |
| `QueryExport` | POST | `/queryExport` | -                |

子控制器用法：

```java

@Api("/user")
@Extends(exclude = {Curd.Export, Curd.QueryExport}) // 不需要异步导出
public class UserController extends CurdController<UserEntity, UserService, UserRepository> {
}
```

## 七、注解汇总

| 注解                                         | 路径                                           | 作用                          |
|----------------------------------------------|------------------------------------------------|-------------------------------|
| `@Extends(value, exclude)`                   | `cn.hamm.airpower.curd.annotation.Extends`     | 控制父类 CURD 接口的暴露范围  |
| `@Search(fullLike)`                          | `cn.hamm.airpower.curd.annotation.Search`      | 标记字段参与模糊搜索          |
| `@SearchEmpty(value)`                        | `cn.hamm.airpower.curd.annotation.SearchEmpty` | 允许空字符串作为搜索条件      |
| `@NullEnable(value)`                         | `cn.hamm.airpower.curd.annotation.NullEnable`  | 修改时允许将字段写为 `null`   |
| `@DisableRequestLog` / `@DisableResponseLog` | `cn.hamm.airpower.curd.annotation.*`           | 关闭指定接口的请求 / 响应日志 |
| `@Permission(login, authorize)`              | `cn.hamm.airpower.curd.permission.Permission`  | 控制器 / 方法级权限声明       |
| `IPermission<P>`                             | `cn.hamm.airpower.curd.permission.IPermission` | 自定义权限实体需实现的接口    |

## 八、拦截器链

| 拦截器                    | 作用                                                                       |
|---------------------------|----------------------------------------------------------------------------|
| `RequestFilter`           | 包装 POST 请求体为 `ContentCachingRequestWrapper`，便于日志回写            |
| `CurdRequestInterceptor`  | TraceId 注入、登录态校验、RBAC 校验、接口权限映射                          |
| `CurdResponseInterceptor` | TraceId 回传、模型字段裁剪（`@Meta` / `@Desensitize`）、脱敏、请求响应日志 |
| `ExceptionInterceptor`    | 统一异常翻译为 `Json` 响应（涵盖参数校验、数据库、Redis、上传等）          |

继承 `CurdRequestInterceptor` 重写 `checkUserPermission(...)` 即可对接自有 RBAC 体系。

## 九、异步导出

`CurdController` 内置 `export` + `queryExport`：

```bash
POST /user/export
{ "filter": { "nickname": "hamm" } }
# 返回 { "code": 200, "data": "文件随机编码 fileCode" }

POST /user/queryExport
{ "fileCode": "xxxxxx" }
# 返回 { "code": 200, "data": "export/2026-08-10/xxx.csv" }
```

- 由 `ExportHelper.createExportTask(...)` 在 `TaskUtil.runAsync` 中分页查询写入 CSV。
- 文件路径生成依赖 `airpower.curd.export.export-path`，未配置会抛 `SERVICE_ERROR`。
- 列定义读取实体类的 `@Description` 与 `@Meta` 字段，默认排除未标注 `@Meta` 的属性。

## 十、`CurdUtil.scanEntity` 反射工具

```java
List<CurdUtil.EntityMeta> metas = CurdUtil.scanEntity("com.example.app.entity");
// metas 里每个 EntityMeta 包含 name / description / fields
// fields 包含 name / description / type / options(字典) / isUnique / definition / isId
```

常用于「可视化建模」、「低代码后台」、「动态表单生成」场景。

## 十一、关键类速查

| 类 / 接口           | 路径                                              | 说明                          |
|---------------------|---------------------------------------------------|-------------------------------|
| `CurdEntity`        | `cn.hamm.airpower.curd.base.CurdEntity`           | 实体基类                      |
| `ICurdRepository`   | `cn.hamm.airpower.curd.base.ICurdRepository`      | 数据源基接口                  |
| `CurdService`       | `cn.hamm.airpower.curd.base.CurdService`          | 业务基类                      |
| `CurdController`    | `cn.hamm.airpower.curd.base.CurdController`       | 控制器基类                    |
| `Curd`              | `cn.hamm.airpower.curd.base.Curd`                 | 控制器接口枚举                |
| `CurdUtil`          | `cn.hamm.airpower.curd.base.CurdUtil`             | 实体扫描工具                  |
| `QueryHelper`       | `cn.hamm.airpower.curd.model.query.QueryHelper`   | 条件构造                      |
| `TransactionHelper` | `cn.hamm.airpower.curd.helper.TransactionHelper`  | `REPEATABLE_READ` 事务封装    |
| `ExportHelper`      | `cn.hamm.airpower.curd.helper.ExportHelper`       | 异步导出                      |
| `PermissionUtil`    | `cn.hamm.airpower.curd.permission.PermissionUtil` | 权限扫描与密码散列            |
| `Auto`              | `cn.hamm.airpower.curd.Auto`                      | `@AutoConfiguration` 装配入口 |

## 十二、常见问题

1. **`@JsonInclude(NON_NULL)` 是否生效？** `CurdEntity` 已声明 `NON_NULL`，但请求 / 响应体的 `Json` 包体由 `airpower-core`
   的 `Json` 类统一处理，可在 `application.yml` 中调整 Spring Boot 的 `spring.jackson.default-property-inclusion`。
2. **接口调用报 `API_SERVICE_UNSUPPORTED`？** 当前控制器使用了 `@Extends` 限定接口，或对应 CURD 枚举被 `exclude`。
3. **加锁更新？** 调用 `service.updateWithLock(id, exist -> {...})`，由 `TransactionHelper` 包裹 `REPEATABLE_READ` + 悲观锁。
4. **导出文件找不到？** 检查 `airpower.curd.export.export-path` 是否已挂载并具有写权限。
5. **如何关闭 `@RequestBody` 重复读取？** 模块已提供 `RequestFilter`，Spring 容器启动后会自动注册。