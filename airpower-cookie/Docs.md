# airpower-cookie 使用文档

> AirPower Cookie 模块 - 统一封装 `jakarta.servlet.http.Cookie` 的创建、安全属性与默认配置。

## 一、模块定位

`airpower-cookie` 提供了 `CookieHelper`，让业务代码无需关心 `HttpOnly`、`Secure`、`Path`、`MaxAge` 等安全字段的统一约定。模块只依赖
`airpower-core` 与 `airpower-exception`，体积极小。

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-cookie</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

## 三、应用配置

```yaml
airpower:
  cookie:
    # Cookie 路径
    cookie-path: /
    # 身份验证 Cookie 名称
    auth-cookie-name: authorization-key
    # 是否仅 HTTP 访问（推荐 true，防止 XSS 窃取）
    cookie-http-only: true
    # 有效期（秒），默认 86400（一天）
    cookie-max-age: 86400
    # 仅在 HTTPS 下发送（生产环境建议 true）
    cookie-security: true
```

源码：[CookieConfig.java](src/main/java/cn/hamm/airpower/cookie/CookieConfig.java)。

## 四、使用 CookieHelper

```java
import cn.hamm.airpower.cookie.CookieHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CookieValue;

@Service
public class AuthService {

    private final CookieHelper cookieHelper;

    public AuthService(CookieHelper cookieHelper) {
        this.cookieHelper = cookieHelper;
    }

    public Cookie buildAuthorizeCookie(String token) {
        // 自动应用 CookieConfig 中的路径 / HttpOnly / Secure / MaxAge
        return cookieHelper.getAuthorizeCookie(token);
    }

    public Cookie buildCustomCookie(String key, String value) {
        return cookieHelper.getCookie(key, value);
    }

    public void onLoginSuccess(jakarta.servlet.http.HttpServletResponse response, String token) {
        response.addCookie(buildAuthorizeCookie(token));
    }
}
```

方法说明：

| 方法                                     | 行为                                      |
|------------------------------------------|-------------------------------------------|
| `CookieHelper.getCookie(key, value)`     | 按 `CookieConfig` 全量装配一个普通 Cookie |
| `CookieHelper.getAuthorizeCookie(value)` | 用 `authCookieName` 创建身份 Cookie       |

## 五、常量与默认值

| 常量                        | 默认值                                 | 说明                      |
|-----------------------------|----------------------------------------|---------------------------|
| `CookieHelper.DEFAULT_PATH` | `/`                                    | 兜底路径                  |
| `cookiePath`                | `/`                                    | `CookieConfig.cookiePath` |
| `authCookieName`            | `authorization-key`                    | 身份 Cookie 名            |
| `cookieHttpOnly`            | `true`                                 | 建议保持开启              |
| `cookieMaxAge`              | `DateTimeUtil.SECOND_PER_DAY`（86400） | 单位：秒                  |
| `cookieSecurity`            | `true`                                 | HTTPS Only                |

## 六、关键类速查

| 类             | 路径                                   | 说明                          |
|----------------|----------------------------------------|-------------------------------|
| `CookieHelper` | `cn.hamm.airpower.cookie.CookieHelper` | Cookie 创建器                 |
| `CookieConfig` | `cn.hamm.airpower.cookie.CookieConfig` | `airpower.cookie.*` 配置绑定  |
| `Auto`         | `cn.hamm.airpower.cookie.Auto`         | `@AutoConfiguration` 装配入口 |

## 七、常见问题

1. **本地 HTTP 调试时 `cookieSecurity=true` 导致 Cookie 写不进去？** 临时将 `airpower.cookie.cookie-security` 设为
   `false`，或使用 `mkcert` / `127.0.0.1` 测试证书。
2. **如何让 Cookie 在前端 JS 中可见？** 将 `cookie-http-only` 显式设为 `false`（不建议生产环境关闭）。
3. **能否每个 Cookie 单独设置 `MaxAge`？** 当前实现统一走 `CookieConfig`，如需自定义，可在业务侧 `new Cookie(...)` 后手动
   `setMaxAge`。