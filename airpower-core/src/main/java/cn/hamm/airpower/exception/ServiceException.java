package cn.hamm.airpower.exception;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import static cn.hamm.airpower.exception.ServiceError.SERVICE_ERROR;

/**
 * <h1>系统异常包装类</h1>
 *
 * @author Hamm.cn
 */
@NoArgsConstructor
@Getter
public class ServiceException extends RuntimeException implements IException<ServiceException> {
    /**
     * 错误代码
     */
    private int code = SERVICE_ERROR.getCode();

    /**
     * 错误数据
     */
    private Object data = null;

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param message 错误信息
     */
    public ServiceException(String message) {
        super(message);
    }

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param message 错误信息
     * @param data    错误数据
     */
    public ServiceException(String message, Object data) {
        super(message);
        this.data = data;
    }

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param code    错误代码
     * @param message 错误信息
     * @param data    错误数据
     */
    public ServiceException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    /**
     * 直接抛出一个异常
     *
     * @param exception 异常
     * @param message   错误信息
     */
    public ServiceException(@NotNull IException<?> exception, String message) {
        super(message);
        this.code = exception.getCode();
    }

    /**
     * 直接抛出一个异常
     *
     * @param exception 异常
     */
    public ServiceException(@NotNull IException<?> exception) {
        super(exception.getMessage());
        this.code = exception.getCode();
    }

    /**
     * 直接抛出一个异常
     *
     * @param exception 异常
     */
    public ServiceException(@NotNull Exception exception) {
        super(exception.getMessage());
    }
}
