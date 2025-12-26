package cn.hamm.airpower.exception;

import cn.hamm.airpower.dictionary.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>系统错误代码字典</h1>
 *
 * @author Hamm.cn
 * @apiNote 如需自定义业务异常，可自行创建一个新业务异常枚举类并实现 {@link IException} 接口即可。
 */
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
@Getter
@AllArgsConstructor
public enum ServiceError implements IException<ServiceException>, IDictionary {
    CONTINUE(201, "请继续"),

    UPGRADE_CLIENT_NECESSARY(301, "请更新客户端"),
    UPGRADE_CLIENT_OPTIONAL(302, "建议升级客户端"),

    // 400 请求参数代码
    PARAM_MISSING(4001, "缺少必要的请求参数"),
    PARAM_INVALID(4002, "请求的参数验证失败"),
    INVALID_APP_KEY(4003, "传入的AppKey无效"),
    SIGNATURE_INVALID(4004, "签名无效，请确认使用了正确的签名方式"),
    REPEAT_REQUEST(4005, "重复的请求，请确认传入的nonce是否重复"),
    TIMESTAMP_INVALID(4006, "请求时间不在允许范围内(±5分钟)"),
    MISSING_REQUEST_ADDRESS(4007, "请求来源地址异常"),
    INVALID_REQUEST_ADDRESS(4008, "请求来源不在应用配置的白名单内"),

    // 401 需要登录代码
    UNAUTHORIZED(401, "获取你的身份信息失败，请重新登录后再试"),

    // 403 无权限代码
    FORBIDDEN(403, "无权操作"),
    FORBIDDEN_EXIST(4031, "添加失败，数据已存在"),
    FORBIDDEN_EDIT(4032, "修改失败"),
    FORBIDDEN_DELETE(4033, "删除失败"),
    FORBIDDEN_DELETE_USED(4034, "删除失败，数据正在使用中"),
    FORBIDDEN_UPLOAD_MAX_SIZE(4035, "上传的文件大小超过最大限制"),
    FORBIDDEN_DISABLED(4036, "ID %s 的 %s 已被禁用"),
    FORBIDDEN_OPEN_APP_DISABLED(4037, "当前应用已被禁用"),
    FORBIDDEN_DISABLED_NOT_ALLOWED(4038, "不允许禁用"),
    DATABASE_CONCURRENT_ERROR(4039, "系统繁忙，请稍后重试！"),

    // 404 没有查到数据代码
    DATA_NOT_FOUND(404, "没有查到相关的数据"),

    REQUEST_METHOD_UNSUPPORTED(405, "不支持的请求方法"),

    REQUEST_CONTENT_TYPE_UNSUPPORTED(415, "不支持的数据类型"),

    // 500 服务基础代码
    SERVICE_ERROR(500, "服务出了一点点异常，请稍后再试或联系技术支持"),
    DECRYPT_DATA_FAIL(5001, "数据解密失败，请确认AppKey/AppSecret/加密方式是否正确"),
    ENCRYPT_DATA_FAIL(5002, "数据加密失败"),
    JSON_DECODE_FAIL(5003, "JSON解码失败，请确认有效的JSON数据格式"),

    // 501 API 服务暂不支持
    API_SERVICE_UNSUPPORTED(501, "请求的接口暂未实现"),
    API_DOWN(5011, "API网关暂不可用，请稍后再试"),


    // 502 内部错误代码
    // 5021 数据库相关代码
    DATABASE_ERROR(5021, "数据库服务连接失败，请稍后再试"),
    DATABASE_UNKNOWN_FIELD(50211, "不支持的数据库字段"),
    DATABASE_TABLE_OR_FIELD_ERROR(50212, "数据库表或字段信息异常"),

    // 5022 Redis相关代码
    REDIS_ERROR(5022, "REDIS服务连接失败，请稍后再试"),

    // 5023 邮件相关代码
    EMAIL_ERROR(5023, "发送邮件异常"),

    // 5024 websocket相关代码
    WEBSOCKET_ERROR(5024, "WebSocket服务发生错误，请稍后再试"),

    // 5025 AI相关代码
    AI_ERROR(5025, "AI服务异常"),
    ;

    private final int code;
    private String message;

    /**
     * 设置错误信息
     *
     * @param message 错误信息
     * @return 当前异常
     */
    @Contract(value = "_ -> this", mutates = "this")
    public ServiceError setMessage(String message) {
        this.message = message;
        return this;
    }

    @Contract(pure = true)
    @Override
    public int getKey() {
        return code;
    }

    @Contract(pure = true)
    @Override
    public String getLabel() {
        return message;
    }

    /**
     * 获取一个服务异常
     *
     * @return 服务异常
     */
    @Contract(" -> new")
    @Override
    public @NotNull ServiceException get() {
        return new ServiceException();
    }
}
