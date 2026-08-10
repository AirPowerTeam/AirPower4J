# airpower-open 使用文档

> AirPower Open 模块 - 通过 AOP 切面 + 加密 / 签名 / 防重放 / IP 白名单的「开放平台」网关，让任何方法一键变成 OpenAPI。

## 一、模块定位

`airpower-open` 把「OpenAPI 鉴权 + 加解密 + 签名校验 + 防重放 + IP 白名单」封装在一个 `@OpenApi` 注解中，业务方法只需声明注解、参数声明为
`OpenRequest`，即可被开放平台调用。

核心组件：

| 组件                 | 路径                                       | 作用                                                                                |
|----------------------|--------------------------------------------|-------------------------------------------------------------------------------------|
| `@OpenApi`           | `cn.hamm.airpower.open.OpenApi`            | 标注在方法 / 类上启用开放平台拦截                                                   |
| `OpenApiAspect<S>`   | `cn.hamm.airpower.open.OpenApiAspect`      | 切面核心，校验 + 解密 + 加密回包                                                    |
| `OpenRequest`        | `cn.hamm.airpower.open.OpenRequest`        | 入参载体，含 `appKey` / `version` / `timestamp` / `nonce` / `content` / `signature` |
| `IOpenApp`           | `cn.hamm.airpower.open.IOpenApp`           | 应用实体需实现的接口                                                                |
| `IOpenAppService`    | `cn.hamm.airpower.open.IOpenAppService`    | 通过 `appKey` 查找应用的服务接口                                                    |
| `OpenArithmeticType` | `cn.hamm.airpower.open.OpenArithmeticType` | 加密方式：`NO` / `AES` / `RSA`                                                      |
| `OpenResponse`       | `cn.hamm.airpower.open.OpenResponse`       | 响应数据加密器                                                                      |
| `OpenBaseModel`      | `cn.hamm.airpower.open.OpenBaseModel`      | 业务数据基类（继承 `RootModel`）                                                    |

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-open</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

模块已传递依赖 `airpower-api` / `airpower-cookie` / `airpower-redis` / `spring-boot-starter-aop`。

## 三、实现开放应用

### 3.1 应用实体实现 `IOpenApp`

```java
import cn.hamm.airpower.curd.base.CurdEntity;
import cn.hamm.airpower.open.IOpenApp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.EqualsAndHashCode;
import lombok.Data;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class OpenAppEntity extends CurdEntity<OpenAppEntity> implements IOpenApp {

    @Column(unique = true, columnDefinition = "varchar(64) default '' comment 'AppKey'")
    private String appKey;

    @Column(columnDefinition = "varchar(128) default '' comment 'AppSecret'")
    private String appSecret;

    @Column(columnDefinition = "varchar(2048) default '' comment 'RSA 私钥'")
    private String privateKey;

    @Column(columnDefinition = "tinyint default 0 comment '加密算法 0=NO 1=AES 2=RSA'")
    private Integer arithmetic;

    @Column(columnDefinition = "text comment 'IP 白名单，多行分隔'")
    private String ipWhiteList;
}
```

### 3.2 应用服务实现 `IOpenAppService`

```java
import cn.hamm.airpower.open.IOpenApp;
import cn.hamm.airpower.open.IOpenAppService;
import org.springframework.stereotype.Service;

@Service
public class OpenAppService extends CurdService<OpenAppEntity, OpenAppRepository>
        implements IOpenAppService {

    @Override
    public IOpenApp getByAppKey(String appKey) {
        OpenAppEntity entity = new OpenAppEntity();
        entity.setAppKey(appKey);
        return repository.findOne(Example.of(entity)).orElse(null);
    }
}
```

## 四、暴露一个开放接口

```java
import cn.hamm.airpower.open.OpenApi;
import cn.hamm.airpower.open.OpenRequest;
import cn.hamm.airpower.open.OpenBaseModel;
import cn.hamm.airpower.core.Json;
import org.springframework.web.bind.annotation.PostMapping;

@PostMapping("/order/create")
@OpenApi
public Json createOrder(OpenRequest request) {
    OrderPayload payload = request.parse(OrderPayload.class);
    // ... 业务逻辑 ...
    return Json.data(new OrderResponse().setOrderId("X-2026-0810-001"));
}
```

`request.parse(...)` 会：

1. 用 `openApp.getArithmetic()` 对 `content` 进行 AES / RSA 解密。
2. JSON 反序列化为目标业务对象。
3. 调用 `ValidateUtil.valid(...)` 做 JSR-303 校验。

## 五、客户端请求示例（AES 加密）

```json
{
  "appKey": "ak_demo",
  "version": 1,
  "timestamp": 1723276800000,
  "nonce": "f8c2b8c0-3f4a-4d0a-8c0e-1f5e4d3c2b1a",
  "content": "<AES 加密后的业务 JSON>",
  "signature": "<sha1(appSecret + appKey + version + timestamp + nonce + content)>"
}
```

请求 / 响应关键点：

- `timestamp` 允许 ±5 分钟（300 秒）误差。
- `nonce` 在 Redis 中以 `NONCE_<nonce>` 为 key 缓存 5 分钟，重复提交会触发 `REPEAT_REQUEST`。
- `signature` 算法：`sha1(appSecret + appKey + version + timestamp + nonce + content)`。
- 响应体中 `Json.data` 会被 `OpenResponse.encodeResponse(...)` 二次加密，返回字符串（不再直接是对象）。

## 六、关键类速查

| 类 / 注解            | 路径                                       | 说明                          |
|----------------------|--------------------------------------------|-------------------------------|
| `@OpenApi`           | `cn.hamm.airpower.open.OpenApi`            | 启用开放平台切面              |
| `OpenApiAspect`      | `cn.hamm.airpower.open.OpenApiAspect`      | 切面实现                      |
| `OpenRequest`        | `cn.hamm.airpower.open.OpenRequest`        | 入参基类                      |
| `IOpenApp`           | `cn.hamm.airpower.open.IOpenApp`           | 应用实体接口                  |
| `IOpenAppService`    | `cn.hamm.airpower.open.IOpenAppService`    | 应用服务接口                  |
| `OpenArithmeticType` | `cn.hamm.airpower.open.OpenArithmeticType` | 加密方式枚举                  |
| `OpenResponse`       | `cn.hamm.airpower.open.OpenResponse`       | 响应加密工具                  |
| `OpenBaseModel`      | `cn.hamm.airpower.open.OpenBaseModel`      | 业务模型基类                  |
| `Auto`               | `cn.hamm.airpower.open.Auto`               | `@AutoConfiguration` 装配入口 |

## 七、常见问题

1. **响应里 `data` 变成了字符串？** `OpenApiAspect` 默认会对 `Json.data` 加密，若希望原样返回，请移除 `@OpenApi`
   或在自定义实现中跳过加密。
2. **希望扩展加密算法？** 新增 `OpenArithmeticType` 枚举值，并在 `OpenResponse.encodeResponse` /
   `OpenRequest.decodeContent` 中补全分支。
3. **时间戳漂移导致 `TIMESTAMP_INVALID`？** 客户端校时，或修改 `OpenApiAspect` 中的 `NONCE_CACHE_SECOND` 阈值。
4. **如何在网关层校验？** OpenApi 模块通常仅做业务侧校验，建议在 Spring Cloud Gateway / Nginx 中再做一层 IP / 限流 / WAF。