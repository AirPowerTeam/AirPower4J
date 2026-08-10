# airpower-email 使用文档

> AirPower Email 模块 - 基于 Spring `JavaMailSender` 的统一邮件发送助手，支持验证码 / 富文本邮件。

## 一、模块定位

`airpower-email` 通过 `EmailHelper` 提供两类开箱即用方法：

- `sendEmail(email, title, content)`：发送自定义富文本邮件。
- `sendCode(email, title, code, sign)`：发送验证码邮件（内置标准 HTML 模板）。

底层依赖 Jakarta Mail 与 Spring `JavaMailSender`，因此集成方需在 `application.yml` 中配置 SMTP 账号。

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-email</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

## 三、应用配置

```yaml
spring:
  mail:
    host: smtp.qq.com
    port: 465
    username: ${MAIL_USERNAME}     # 既是发件人又是登录账号
    password: ${MAIL_PASSWORD}     # SMTP 授权码
    default-encoding: UTF-8
    properties:
      mail:
        smtp:
          auth: true
          starttls.enable: true
          ssl.enable: true
```

> `airpower-email` 通过 `@Value("${spring.mail.username: ''}")` 读取发件人地址，请确保 `spring.mail.username`
> 配置正确，否则发送时会抛 `EMAIL_ERROR`。

## 四、发送自定义邮件

```java
import cn.hamm.airpower.email.helper.EmailHelper;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Service;

@Service
public class NotifyService {

    private final EmailHelper emailHelper;

    public NotifyService(EmailHelper emailHelper) {
        this.emailHelper = emailHelper;
    }

    public void notify(String email, String title, String html) throws MessagingException {
        emailHelper.sendEmail(email, title, html);
    }
}
```

## 五、发送验证码邮件

```java
public void sendCaptcha(String email, String code, String sign) throws MessagingException {
    emailHelper.sendCode(email, "您的验证码", code, sign);
}
```

模板效果（由 `EmailHelper.sendCode` 渲染）：

```html

<div style='border-radius:20px;padding: 20px;background-color:#f5f5f5;color:#333;display:inline-block;'>
    <div style='font-size:24px;font-weight:bold;color:orangered;'>
        {code}
    </div>
    <div style='margin-top:20px;font-size:16px;font-weight:300'>
        上面是你的验证码，请注意不要转发给他人，五分钟内有效，请尽快使用。
    </div>
    <div style='margin-top:10px;font-size:12px;color:#aaa;font-weight:300'>
        {sign}
    </div>
</div>
```

## 六、关键类速查

| 类            | 路径                                        | 说明                          |
|---------------|---------------------------------------------|-------------------------------|
| `EmailHelper` | `cn.hamm.airpower.email.helper.EmailHelper` | 邮件发送助手                  |
| `Auto`        | `cn.hamm.airpower.email.Auto`               | `@AutoConfiguration` 装配入口 |

## 七、常见问题

1. **抛 `EMAIL_ERROR`：`未配置邮件服务的信息(spring.mail.username)`？** 在 `application.yml` 中补全
   `spring.mail.username`。
2. **QQ 邮箱 / 163 邮箱需要「授权码」而非登录密码。**
3. **SMTP 25 端口被云厂商屏蔽？** 推荐改用 465（SSL）或 587（STARTTLS）。
4. **如何在测试环境跳过发送？** 把 `EmailHelper` 替换为自定义 `@Primary` 的 Mock 即可，或将 `spring.mail.host` 配置为
   `greenmail` 的本地测试容器。