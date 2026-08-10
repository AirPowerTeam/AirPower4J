# airpower-file 使用文档

> AirPower File 模块 - 基于「策略模式 + 平台枚举」的文件上传封装，开箱支持本地磁盘、阿里云 OSS、腾讯云 COS，可在配置项中无侵入切换。

## 一、模块定位

`airpower-file` 提供：

| 组件                                          | 路径                                  | 作用                                       |
|-----------------------------------------------|---------------------------------------|--------------------------------------------|
| `FileHelper`                                  | `cn.hamm.airpower.file.FileHelper`    | 业务层上传入口，按 `FilePlatform` 自动路由 |
| `IFilePlatform`                               | `cn.hamm.airpower.file.IFilePlatform` | 文件平台策略接口                           |
| `LocalFile` / `AliyunOss` / `TencentCloudOss` | `cn.hamm.airpower.file.platform.*`    | 三种内置实现                               |
| `FileConfig`                                  | `cn.hamm.airpower.file.FileConfig`    | `airpower.file.*` 配置                     |
| `FilePlatform`                                | `cn.hamm.airpower.file.FilePlatform`  | 存储平台枚举                               |

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-file</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

依赖中已包含阿里云 `aliyun-sdk-oss` 与腾讯云 `cos_api`。

## 四、应用配置

### 4.1 本地存储

```yaml
airpower:
  file:
    file-platform: LOCAL
    local-absolute-directory: /home/static/
    upload-directory: upload
    upload-max-size: 10485760    # 10 MB
```

### 4.2 阿里云 OSS

```yaml
airpower:
  file:
    file-platform: ALIYUN
    aliyun-access-key-id: ${ALIYUN_AK}
    aliyun-access-key-secret: ${ALIYUN_SK}
    aliyun-end-point: oss-cn-hangzhou.aliyuncs.com
    aliyun-bucket-name: airpower
```

### 4.3 腾讯云 COS

```yaml
airpower:
  file:
    file-platform: TENCENT_CLOUD
    tencent-secret-id: ${TENCENT_SID}
    tencent-secret-key: ${TENCENT_SK}
    tencent-bucket-name: airpower-1300000000
    tencent-region: ap-shanghai
```

## 五、上传文件

```java
import cn.hamm.airpower.file.FileHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@PostMapping("/upload")
public String upload(@RequestParam MultipartFile file) {
    String relativePath = fileHelper.upload(file, "avatar");
    // relativePath 形如 "upload/avatar/2026-08-10/<md5>.<ext>"
    return relativePath;
}
```

`FileHelper.upload(...)` 重载：

| 方法                                                                           | 行为                                                |
|--------------------------------------------------------------------------------|-----------------------------------------------------|
| `upload(multipartFile, category)`                                              | 按 `category` 自动拼日期目录，文件名 = MD5 + 扩展名 |
| `upload(multipartFile, relativeDirectory, fileName)`                           | 自定义目录 + 文件名                                 |
| `upload(multipartFile, relativeDirectory, fileName, Consumer<Long> sizeLimit)` | 自定义单次大小限制（`null` 表示忽略）               |

## 六、辅助方法

```java
// 获取扩展名（不带点）
String ext = FileHelper.getFileExtension(multipartFile);

// 计算文件 MD5
String md5 = FileHelper.getFileHash(multipartFile);

// 校验扩展名白名单
FileHelper.

validateFileExtension(multipartFile, "jpg","png","webp");

// MultipartFile → File（创建临时文件）
File tmp = FileHelper.multipartFileToFile(multipartFile);

// 获取上传目录
String dir = fileHelper.getUploadDirectory("avatar");           // upload/avatar/
String today = fileHelper.getUploadDirectory("avatar", true);   // upload/avatar/2026-08-10/

// 通过相对路径换取 URL（OSS / COS 会签发 7 天临时 URL）
String url = fileHelper.getUrl(relativePath);
```

## 七、自定义存储平台

实现 `IFilePlatform` 并注册为 Spring Bean 即可被 `FileHelper` 识别：

```java

@Component
public class MinioFile implements IFilePlatform {
    @Override
    public void save(MultipartFile file, String directory, String fileName) {
        // ...
    }

    @Override
    public String getUrl(String path) {
        return minioClient.getPresignedObjectUrl(...);
    }
}
```

然后扩展 `FilePlatform` 枚举并按需调整 `FileHelper.getFilePlatform()` 的 switch 分支（推荐 fork 或重写 `FileHelper`，或在自定义
Bean 中替换）。

## 八、关键类速查

| 类 / 接口         | 路径                                             | 说明                          |
|-------------------|--------------------------------------------------|-------------------------------|
| `FileHelper`      | `cn.hamm.airpower.file.FileHelper`               | 上传入口                      |
| `IFilePlatform`   | `cn.hamm.airpower.file.IFilePlatform`            | 文件平台策略接口              |
| `LocalFile`       | `cn.hamm.airpower.file.platform.LocalFile`       | 本地存储实现                  |
| `AliyunOss`       | `cn.hamm.airpower.file.platform.AliyunOss`       | 阿里云 OSS                    |
| `TencentCloudOss` | `cn.hamm.airpower.file.platform.TencentCloudOss` | 腾讯云 COS                    |
| `FileConfig`      | `cn.hamm.airpower.file.FileConfig`               | 配置绑定                      |
| `FilePlatform`    | `cn.hamm.airpower.file.FilePlatform`             | 平台枚举                      |
| `Auto`            | `cn.hamm.airpower.file.Auto`                     | `@AutoConfiguration` 装配入口 |

## 九、常见问题

1. **本地存储文件无法访问？** 需自行挂一个静态资源映射（如 Spring
   `ResourceHandlerRegistry.addResourceHandler("/static/**").addResourceLocations("file:/home/static/")`）。
2. **OSS 临时 URL 有效期多长？** `AliyunOss.getUrl` 默认签发 7 天。
3. **上传超过 `upload-max-size`？** 全局拦截器会返回 `FORBIDDEN_UPLOAD_MAX_SIZE`。
4. **需要在保存前压缩图片？** 在自定义 `IFilePlatform` 或业务层调用 `FileHelper.upload(...)` 前处理 `MultipartFile`。