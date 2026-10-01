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
    # 可信代理头，按优先级从高到低排列；默认仅 X-Real-IP
    # 读不到合法 IP 时回退为 TCP 连接对端地址。留空表示只使用 TCP 对端地址
    trust-proxy-headers:
      - X-Real-IP
```

> 代理头可被客户端伪造，只能填写**由你自己可信的代理写入**的头，
> 且代理侧需强制覆盖客户端传入的同名头（如 Nginx `proxy_set_header X-Real-IP $remote_addr;`），
> 否则 IP 白名单、限流、风控都可能被伪造请求头绕过。
> 常见取值：`X-Real-IP`（Nginx）、`X-Forwarded-For`（通用多级代理）、`CF-Connecting-IP`（Cloudflare）。

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
// 解析来源 IP（按 ApiConfig.trustProxyHeaders 顺序读头，读不到则回退 TCP 对端地址）
String ip = RequestUtil.getIpAddress(httpRequest);

// 判断当前请求是否为 multipart/form-data 文件上传
boolean isUpload = RequestUtil.isUploadRequest(httpRequest);

// 构造 QueryString
String qs = RequestUtil.mapToQueryString(Map.of("a", 1, "b", "hello"));

// 在 URL 末尾拼装查询参数
String full = RequestUtil.buildQueryUrl("https://example.com/api", Map.of("page", 1, "size", 20));
```

### 6.1 IP 解析规则

`getIpAddress` 只有两步：

1. **按配置顺序读可信代理头**：依次读取 `airpower.api.trust-proxy-headers` 中配置的请求头，
   命中第一个合法 IP 立即返回。列表顺序即优先级，默认只有 `X-Real-IP`；
2. **回退 TCP 对端地址**：没配置可信代理头，或这些头都没读到合法 IP 时，返回 `getRemoteAddr()`；
   对端地址也拿不到时返回 `unknown`（不再抛业务异常）。

值解析做了严格校验：兼容 `1.2.3.4:8080`、`[2001:db8::1]:8080`、`fe80::1%eth0`、`::ffff:1.2.3.4` 等写法，
拒绝 `unknown`、`1.2.3`、`010.1.1.1` 及含换行的非法值，避免脏数据进入日志与数据库；
逗号分隔的链形头（`X-Forwarded-For`）取最左侧的合法 IP。

> ⚠️ 代理头可被客户端随意构造。因此 `trust-proxy-headers` 只能填写**由你自己可信的代理写入**的头，
> 且代理侧必须强制覆盖客户端传入的同名头，否则攻击者直接 `curl -H "X-Real-IP: 1.2.3.4"` 就能伪造来源 IP。
> 不确定就不配（留空），此时只用 TCP 对端地址，是最保守的。
> 代码中也可调用 `RequestUtil.setTrustProxyHeaders(...)` 动态调整。

源码：[RequestUtil.java](src/main/java/cn/hamm/airpower/api/RequestUtil.java)、[ApiConfig.java](src/main/java/cn/hamm/airpower/api/config/ApiConfig.java)。

## 七、关键类速查

| 类 / 注解          | 路径                                      | 说明                        |
|-----------------|-----------------------------------------|---------------------------|
| `@Api`          | `cn.hamm.airpower.api.annotation.Api`   | REST 控制器标记                |
| `ApiController` | `cn.hamm.airpower.api.ApiController`    | 所有控制器的基类                  |
| `RequestUtil`   | `cn.hamm.airpower.api.RequestUtil`      | IP / 上传 / URL 解析          |
| `ApiConfig`     | `cn.hamm.airpower.api.config.ApiConfig` | `airpower.api.*` 配置绑定     |
| `Auto`          | `cn.hamm.airpower.api.Auto`             | `@AutoConfiguration` 装配入口 |

## 八、典型协作场景

`airpower-api` 通常作为「地基」被以下模块依赖：

| 下游模块                 | 用到本模块的能力                                             |
|----------------------|------------------------------------------------------|
| `airpower-curd`      | `ApiController` 作为所有 CURD 控制器的父类                     |
| `airpower-open`      | `RequestUtil.getIpAddress` 做 IP 白名单校验                |
| `airpower-websocket` | `ApiConfig.accessTokenSecret` 校验 WebSocket 握手 Token  |
| 业务代码                 | `@Api` 注解 + `ApiController.getCurrentUserId()` 获取登录态 |