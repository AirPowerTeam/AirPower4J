package cn.hamm.airpower.interceptor;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.exception.IException;
import cn.hamm.airpower.exception.ServiceError;
import cn.hamm.airpower.exception.ServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hamm.airpower.exception.ServiceError.*;

/**
 * <h1>全局异常处理拦截器</h1>
 *
 * @author Hamm.cn
 * @see ServiceError
 */
@ControllerAdvice
@ResponseStatus(HttpStatus.OK)
@ResponseBody
@Slf4j
public class ExceptionInterceptor {
    /**
     * 错误信息和描述
     */
    private static final String MESSAGE_AND_DESCRIPTION = "%s (%s)";

    /**
     * 参数验证失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Json badRequestHandle(@NotNull MethodArgumentNotValidException exception) {
        logException(exception);
        BindingResult result = exception.getBindingResult();
        StringBuilder stringBuilder = new StringBuilder();
        if (!result.hasErrors() || !result.hasFieldErrors()) {
            return responseError(PARAM_INVALID);
        }
        List<FieldError> errors = result.getFieldErrors();
        errors.stream().findFirst().ifPresent(error -> stringBuilder.append(String.format(
                MESSAGE_AND_DESCRIPTION, error.getDefaultMessage(), error.getField()
        )));
        return responseError(PARAM_INVALID, stringBuilder.toString())
                .setData(errors.stream().map(item -> String.format(
                        MESSAGE_AND_DESCRIPTION, item.getDefaultMessage(), item.getField()
                )));
    }

    /**
     * 参数校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Json badRequestHandle(@NotNull ConstraintViolationException exception) {
        logException(exception);
        StringBuilder stringBuilder = new StringBuilder();
        Set<ConstraintViolation<?>> errors = exception.getConstraintViolations();
        errors.stream().findFirst().ifPresent(error -> stringBuilder.append(String.format(
                MESSAGE_AND_DESCRIPTION, error.getMessage(), error.getInvalidValue().toString()
        )));
        return responseError(PARAM_INVALID, stringBuilder.toString());
    }

    /**
     * 删除时的数据关联校验异常
     */
    @ExceptionHandler({SQLIntegrityConstraintViolationException.class, DataIntegrityViolationException.class})
    public Json deleteUsingDataException(@NotNull Exception exception) {
        logException(exception);
        return responseError(FORBIDDEN_DELETE_USED);
    }

    /**
     * 访问的接口没有实现
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Json notFoundHandle(@NotNull NoHandlerFoundException exception) {
        logException(exception);
        return responseError(API_SERVICE_UNSUPPORTED);
    }

    /**
     * 请求的数据不是标准 JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Json dataExceptionHandle(@NotNull HttpMessageNotReadableException exception) {
        logException(exception);
        return responseError(REQUEST_CONTENT_TYPE_UNSUPPORTED,
                "请求参数格式不正确,请检查是否接口支持的JSON");
    }

    /**
     * 不支持的请求方法
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Json methodExceptionHandle(@NotNull HttpRequestMethodNotSupportedException exception) {
        logException(exception);
        String supportedMethod = String.join("/", Objects.requireNonNull(exception.getSupportedMethods()));
        return responseError(REQUEST_METHOD_UNSUPPORTED, String.format(
                "%s 不被支持，请使用 %s 方法请求", exception.getMethod(), supportedMethod
        ));
    }

    /**
     * 不支持的文件上传
     */
    @ExceptionHandler(MultipartException.class)
    public Json multipartExceptionHandle(@NotNull MultipartException exception) {
        logException(exception);
        return responseError(REQUEST_METHOD_UNSUPPORTED, "请使用 multipart 方式上传文件");
    }

    /**
     * 未选择上传文件
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Json missingServletRequestPartExceptionHandle(@NotNull MissingServletRequestPartException exception) {
        logException(exception);
        return responseError(PARAM_MISSING, String.format(
                "缺少文件 %s",
                Objects.requireNonNull(exception.getRequestPartName())
        ));
    }

    /**
     * 未提交必要参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Json missingServletRequestParameterExceptionHandle(@NotNull MissingServletRequestParameterException exception) {
        logException(exception);
        return responseError(PARAM_MISSING, String.format(
                "缺少参数 %s",
                Objects.requireNonNull(exception.getParameterName())
        ));
    }

    /**
     * 不支持的数据类型
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Json httpMediaTypeNotSupportedExceptionHandle(@NotNull HttpMediaTypeNotSupportedException exception) {
        logException(exception);
        return responseError(REQUEST_CONTENT_TYPE_UNSUPPORTED, String.format(
                "%s 不被支持，请使用JSON请求",
                Objects.requireNonNull(exception.getContentType()).getType()
        ));
    }

    /**
     * 数据库连接发生错误
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    public Json databaseExceptionHandle(@NotNull CannotCreateTransactionException exception) {
        logException(exception);
        return responseError(DATABASE_ERROR);
    }

    /**
     * Redis 连接发生错误
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public Json redisExceptionHandle(@NotNull RedisConnectionFailureException exception) {
        logException(exception);
        return responseError(REDIS_ERROR);
    }

    /**
     * 系统自定义异常
     */
    @ExceptionHandler(ServiceException.class)
    public Json systemExceptionHandle(@NotNull ServiceException exception) {
        logException(exception);
        return responseError(exception).setData(exception.getData());
    }

    /**
     * 数据字段不存在
     */
    @ExceptionHandler(value = PropertyReferenceException.class)
    public Json propertyReferenceExceptionHandle(@NotNull PropertyReferenceException exception) {
        logException(exception);
        return responseError(DATABASE_UNKNOWN_FIELD, String.format(
                "数据库缺少字段 %s", exception.getPropertyName()
        ));
    }

    /**
     * 数据表或字段异常
     */
    @ExceptionHandler(value = InvalidDataAccessResourceUsageException.class)
    public Json invalidDataAccessResourceUsageExceptionHandle(
            @NotNull InvalidDataAccessResourceUsageException exception
    ) {
        logException(exception);
        return responseError(DATABASE_TABLE_OR_FIELD_ERROR);
    }

    /**
     * 数据表或字段异常
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Json maxUploadSizeExceededExceptionHandle(@NotNull MaxUploadSizeExceededException exception) {
        logException(exception);
        return responseError(FORBIDDEN_UPLOAD_MAX_SIZE);
    }

    /**
     * 其他异常
     */
    @ExceptionHandler(value = {Exception.class, RuntimeException.class})
    public Object otherExceptionHandle(@NotNull Exception exception) {
        logException(exception);
        return responseError(SERVICE_ERROR);
    }

    /**
     * 记录异常信息
     *
     * @param exception 异常
     */
    private void logException(@NotNull Exception exception) {
        if (exception instanceof IException<?> serviceError) {
            log.error("[{}]{}", serviceError.getCode(), serviceError.getMessage(), exception);
            return;
        }
        log.error(exception.getMessage(), exception);
    }

    /**
     * 返回错误信息
     *
     * @param serviceError 错误信息
     * @return Json
     */
    private Json responseError(IException<?> serviceError) {
        return responseError(serviceError, serviceError.getMessage());
    }

    /**
     * 返回错误信息
     *
     * @param serviceError 错误信息
     * @param message      错误信息
     * @return Json
     */
    private Json responseError(IException<?> serviceError, String message) {
        return Json.error(serviceError, message);
    }
}
