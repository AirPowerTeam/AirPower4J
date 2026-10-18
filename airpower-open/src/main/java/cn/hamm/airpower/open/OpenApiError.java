package cn.hamm.airpower.open;

import cn.hamm.airpower.core.dictionary.IDictionary;
import cn.hamm.airpower.core.exception.IException;
import cn.hamm.airpower.core.exception.ServiceException;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * <h2>开放接口错误码</h2>
 *
 * @author Hamm.cn
 */
@Getter
public enum OpenApiError implements IException<ServiceException>, IDictionary {
    // 应用相关
    OPEN_APP_DISABLED(1, "当前应用已被禁用"),
    OPEN_APP_STATUS_ERROR(2, "当前应用已被禁用"),
    APP_KEY_REQUIRED(3, "AppKey 不能为空"),
    APP_KEY_INVALID(4, "传入的 AppKey 无效"),
    PAYLOAD_REQUIRED(5, "请求负载不能为空"),
    OPEN_APP_PUBLIC_KEY_REQUIRED(6, "应用未配置公钥"),

    // 请求相关
    OPEN_API_UNSUPPORTED(101, "暂不支持访问的 OpenAPI"),
    SIGNATURE_INVALID(102, "签名(signature)无效"),
    SIGNATURE_REQUIRED(103, "签名(signature)不能为空"),
    REPEAT_REQUEST(103, "重复的请求，请确认传入的随机串(nonce)是否重复"),
    TIMESTAMP_INVALID(104, "时间戳(timestamp)不在允许范围内(±5分钟)"),
    MISSING_REQUEST_ADDRESS(105, "请求来源地址异常"),
    INVALID_REQUEST_ADDRESS(106, "请求来源不在应用配置的白名单内"),
    NONCE_REQUIRED(107, "随机串(nonce)不能为空"),
    TIMESTAMP_REQUIRED(108, "时间戳(timestamp)不能为空"),

    // 数据相关
    DECRYPT_DATA_FAIL(501, "数据解密失败"),
    ENCRYPT_DATA_FAIL(502, "数据加密失败"),
    JSON_DECODE_FAIL(503, "JSON解码失败，请确认有效的JSON数据格式"),

    ;
    private final int code;
    private final String message;

    OpenApiError(int code, String message) {
        this.code = code + OpenConfig.OPEN_BASE_CODE;
        this.message = message;
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
