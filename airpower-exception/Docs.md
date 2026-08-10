# airpower-exception 使用文档

> AirPower Exception 模块 - 统一错误码字典与异常处理框架，是所有 AirPower 模块的「公共底线」。

## 一、模块定位

`airpower-exception` 提供了：

1. **`Errors` 枚举**：涵盖 4xx / 5xx 的全量业务错误码，每个枚举项都实现了 `IException<ServiceException>`，可链式抛出。
2. **`IException` 接口**：业务异常枚举只需实现该接口即可获得 `show()` / `whenXxx(...)` 链式判定能力。
3. **`Auto` 自动装配**：被其他模块传递依赖，几乎所有上层模块都会引入。

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-exception</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

> 一般不需要显式引入——任何上层 AirPower 模块都会自动带上。

## 三、`Errors` 错误码速查

| 错误码                                 | 含义                  | 典型场景          |
|----------------------------------------|-----------------------|-------------------|
| `201 CONTINUE`                         | 请继续                | 中间件 / 异步轮询 |
| `301 / 302`                            | 客户端升级            | 强制 / 推荐       |
| `4001 PARAM_MISSING`                   | 缺少必要参数          | 校验拦截          |
| `4002 PARAM_INVALID`                   | 参数验证失败          | 校验拦截          |
| `4003 INVALID_APP_KEY`                 | AppKey 无效           | 开放平台          |
| `4004 SIGNATURE_INVALID`               | 签名错误              | 开放平台          |
| `4005 REPEAT_REQUEST`                  | 重复请求              | 开放平台防重放    |
| `4006 TIMESTAMP_INVALID`               | 时间戳超出 ±5 分钟    | 开放平台          |
| `401 UNAUTHORIZED`                     | 未登录                | 全局拦截器        |
| `403 FORBIDDEN`                        | 无权操作              | RBAC              |
| `4031 FORBIDDEN_EXIST`                 | 数据已存在            | CURD 唯一性       |
| `4032 / 4033`                          | 修改 / 删除失败       | CURD              |
| `4034 FORBIDDEN_DELETE_USED`           | 数据正在使用          | 外键关联          |
| `4036 FORBIDDEN_DISABLED`              | 已被禁用              | CURD 启用校验     |
| `4037 FORBIDDEN_OPEN_APP_DISABLED`     | 应用已被禁用          | 开放平台          |
| `4039 DATABASE_CONCURRENT_ERROR`       | 乐观锁冲突            | CURD              |
| `404 DATA_NOT_FOUND`                   | 没有查到数据          | 全局              |
| `405 REQUEST_METHOD_UNSUPPORTED`       | 不支持的 HTTP 方法    | 全局              |
| `415 REQUEST_CONTENT_TYPE_UNSUPPORTED` | 不支持的 Content-Type | 全局              |
| `500 SERVICE_ERROR`                    | 服务异常              | 全局兜底          |
| `5001 / 5002 / 5003`                   | 加解密 / JSON 解码    | 开放平台          |
| `501 API_SERVICE_UNSUPPORTED`          | 接口未实现            | `@Extends`        |
| `5021 / 50211 / 50212`                 | 数据库异常            | JPA               |
| `5022 REDIS_ERROR`                     | Redis 异常            | Redis             |
| `5023 EMAIL_ERROR`                     | 邮件异常              | Email             |
| `5024 WEBSOCKET_ERROR`                 | WebSocket 异常        | WebSocket         |
| `5025 AI_ERROR`                        | AI 调用异常           | AI                |

> 全部枚举见 [Errors.java](src/main/java/cn/hamm/airpower/exception/Errors.java)。

## 四、使用方式

### 4.1 快捷抛出

```java
import static cn.hamm.airpower.exception.Errors.PARAM_MISSING;

public void update(Long id) {
    PARAM_MISSING.whenNull(id, "修改失败，请传入ID");
    // ...
}
```

`whenNull` / `whenEmpty` / `when(...)` / `whenNotEquals(...)` 等方法在条件成立时直接抛 `ServiceException`，由
`ExceptionInterceptor` 翻译为标准 JSON 响应。

### 4.2 自定义错误消息

```java
// PARAM_MISSING.show("请先登录");
```

### 4.3 自定义业务异常枚举

```java
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.interfaces.IException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BizError implements IException<ServiceException> {
    STOCK_NOT_ENOUGH(10001, "库存不足"),
    ORDER_EXPIRED(10002, "订单已过期");

    private final int code;
    private String message;

    @Override
    public ServiceException get() {
        return new ServiceException();
    }
}
```

直接 `STOCK_NOT_ENOUGH.when(quantity < need, "商品 %s 库存不足", skuCode)` 即可。

## 五、与全局拦截器的关系

`airpower-curd` 中的 `ExceptionInterceptor` 已经识别 `IException` 实现并返回 `Json.error(...)`，开发者无需额外注册
`@ControllerAdvice`。

## 六、关键类速查

| 类 / 接口       | 路径                                          | 说明                                                 |
|-----------------|-----------------------------------------------|------------------------------------------------------|
| `Errors`        | `cn.hamm.airpower.exception.Errors`           | 框架内置错误码字典                                   |
| `IException<E>` | `cn.hamm.airpower.core.interfaces.IException` | 异常接口（位于 core，本模块通过 airpower-core 传递） |
| `Auto`          | `cn.hamm.airpower.exception.Auto`             | `@AutoConfiguration` 装配入口                        |

## 七、常见问题

1. **希望自定义异常能被全局拦截器识别？** 自定义枚举实现 `IException<ServiceException>` 即可。
2. **`ServiceException` 如何携带返回数据？** `ExceptionInterceptor` 会读取 `exception.getData()` 并写入响应体。