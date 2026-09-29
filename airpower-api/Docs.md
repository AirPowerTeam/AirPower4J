# airpower-api 使用文档

> AirPower API 模块 - 提供 `@Api` 控制器注解、控制器根类 `ApiController`、请求 / IP 工具类，是所有对外接口的「地基」。

## 一、模块定位

`airpower-api` 是 AirPower4J 框架的 API 基石模块，不绑定具体业务，但提供了以下核心抽象：

| 能力                   | 类 / 注解                                                     |
|----------------------|------------------------------------------------------------|
| RESTful 控制器标记        | `@Api("/path")` = `@RestController` + `@RequestMapping`    |
| 控制器基类                | `ApiController`，注入 `ApiConfig` 与当前请求                       |
| 获取当前登录用户             | `getCurrentUserId()` / `getCurrentUserVerifiedToken()`     |
| 解析请求 IP              | `RequestUtil.getIpAddress(request)`（仅信任已配置的反代）             |
| 判断上传请求               | `RequestUtil.isUploadRequest(request)`                     |
| 构建 QueryString / URL | `RequestUtil.mapToQueryString(...)` / `buildQueryUrl(...)` |

源码入口：[Auto.java](src/main/java/cn/hamm/airpower/api/Auto.java)。

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-api</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

## 三、应用配置

```yaml
airpower:
  api:
    # 是否打印请求包体日志（全局开关）
    request-log: true
    # 是否打印响应包体日志
    response-log: true
    # 是否在响应 Body 中回写 TraceId
    body-trace-id: true
    # AccessToken 签名密钥（必填，建议通过环境变量注入）
    access-token-secret: ${AIRPOWER_API_SECRET}
    # 身份令牌的 Header / Param Key
    authorize-header: authorization
  ip:
    # 可信反向代理地址（IP 或 CIDR 网段），只有来自这些地址的请求才会解析 X-Forwarded-For 等代理头
    # 留空表示不信任任何代理头，仅使用 TCP 连接对端地址
    trusted-proxies:
      - 127.0.0.1/32
      - ::1/128
```

源码：[ApiConfig.java](src/main/java/cn/hamm/airpower/api/config/ApiConfig.java)。

## 四、编写第一个控制器

```java
import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.core.Json;
import org.springframework.web.bind.annotation.GetMapping;

@Api("/hello")
public class HelloController extends ApiController {

    @GetMapping("/world")
    public Json world() {
        return Json.data("Hello " + getCurrentUserId());
    }
}
```

要点：

- `@Api("/hello")` 等价于 `@RestController @RequestMapping("/hello")`，无需再额外声明。
- 所有控制器必须继承 `ApiController`，否则 `getCurrentUserId()` 等基础能力无法使用。

## 五、获取当前登录用户

`ApiController` 已自动注入 `HttpServletRequest`，并提供：

```java
// 当前用户 ID（Long 类型）
long userId = getCurrentUserId();

// 完整已校验 Token（含签发时间、过期时间、Payload 等）
AccessTokenUtil.VerifiedToken token = getCurrentUserVerifiedToken();
```

> Token 优先从 QueryString 中读取 `authorization`，其次从 Header 中读取，便于 WebSocket / SSE 等无法自定义 Header 的场景。

## 六、RequestUtil 工具方法

```java
// 解析来源 IP（Forwarded / X-Forwarded-For / X-Real-IP / Proxy-Client-IP / ...）
String ip = RequestUtil.getIpAddress(httpRequest);

// 判断当前请求是否为 multipart/form-data 文件上传
boolean isUpload = RequestUtil.isUploadRequest(httpRequest);

// 构造 QueryString
String qs = RequestUtil.mapToQueryString(Map.of("a", 1, "b", "hello"));

// 在 URL 末尾拼装查询参数
String full = RequestUtil.buildQueryUrl("https://example.com/api", Map.of("page", 1, "size", 20));
```

### 6.1 IP 解析的信任模型

`X-Forwarded-For` 等请求头可被客户端随意构造，直接采信会导致 IP 白名单、限流、风控被绕过，
因此 `getIpAddress` 采用如下规则：

1. **先看 TCP 连接对端**：对端地址（`getRemoteAddr()`）不在 `airpower.ip.trusted-proxies` 内时，直接返回对端地址，完全忽略代理头；
2. **自右向左剥离代理**：对端可信时，解析代理链并跳过所有落在可信网段内的节点，第一个非可信地址即真实来源 IP；
   客户端在 `X-Forwarded-For` 左侧塞入的伪造地址会被自动丢弃；
3. **严格校验字面量**：兼容 `1.2.3.4:8080`、`[2001:db8::1]:8080`、`fe80::1%eth0`、`::ffff:1.2.3.4` 等写法，
   拒绝 `unknown`、`1.2.3`、`010.1.1.1` 及含换行的非法值，避免脏数据进入日志与数据库；
4. **兜底安全**：全部解析失败时返回 `getRemoteAddr()`，异常时返回 `unknown`（不再抛业务异常）。

> 常见场景：Nginx 与应用同机部署时默认配置即可使用；应用在容器 / 网关之后，需把网关地址补进 `trusted-proxies`。
> 代码中也可直接调用 `RequestUtil.setTrustedProxies(...)` 动态调整。

源码：[RequestUtil.java](src/main/java/cn/hamm/airpower/api/RequestUtil.java)、[IpConfig.java](src/main/java/cn/hamm/airpower/api/config/IpConfig.java)。

## 七、关键类速查

| 类 / 注解          | 路径                                      | 说明                        |
|-----------------|-----------------------------------------|---------------------------|
| `@Api`          | `cn.hamm.airpower.api.annotation.Api`   | REST 控制器标记                |
| `ApiController` | `cn.hamm.airpower.api.ApiController`    | 所有控制器的基类                  |
| `RequestUtil`   | `cn.hamm.airpower.api.RequestUtil`      | IP / 上传 / URL 解析          |
| `ApiConfig`     | `cn.hamm.airpower.api.config.ApiConfig` | `airpower.api.*` 配置绑定     |
| `IpConfig`      | `cn.hamm.airpower.api.config.IpConfig`  | `airpower.ip.*` 配置绑定      |
| `Auto`          | `cn.hamm.airpower.api.Auto`             | `@AutoConfiguration` 装配入口 |

## 八、典型协作场景

`airpower-api` 通常作为「地基」被以下模块依赖：

| 下游模块                 | 用到本模块的能力                                             |
|----------------------|------------------------------------------------------|
| `airpower-curd`      | `ApiController` 作为所有 CURD 控制器的父类                     |
| `airpower-open`      | `RequestUtil.getIpAddress` 做 IP 白名单校验                |
| `airpower-websocket` | `ApiConfig.accessTokenSecret` 校验 WebSocket 握手 Token  |
| 业务代码                 | `@Api` 注解 + `ApiController.getCurrentUserId()` 获取登录态 |