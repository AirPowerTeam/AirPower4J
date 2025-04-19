package cn.hamm.airpower.exception;

import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.util.RequestUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
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
     * <h3>错误信息和描述</h3>
     */
    private static final String MESSAGE_AND_DESCRIPTION = "%s (%s)";

    /**
     * <h3>参数验证失败</h3>
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
     * <h3>参数校验失败</h3>
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
     * <h3>删除时的数据关联校验异常</h3>
     */
    @ExceptionHandler({SQLIntegrityConstraintViolationException.class, DataIntegrityViolationException.class})
    public Json deleteUsingDataException(@NotNull Exception exception) {
        logException(exception);
        return responseError(FORBIDDEN_DELETE_USED);
    }

    /**
     * <h3>访问的接口没有实现</h3>
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Json notFoundHandle(@NotNull NoHandlerFoundException exception) {
        logException(exception);
        return responseError(API_SERVICE_UNSUPPORTED);
    }

    /**
     * <h3>请求的数据不是标准 {@code JSON}</h3>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Json dataExceptionHandle(@NotNull HttpMessageNotReadableException exception) {
        logException(exception);
        return responseError(REQUEST_CONTENT_TYPE_UNSUPPORTED,
                "请求参数格式不正确,请检查是否接口支持的JSON");
    }

    /**
     * <h3>不支持的请求方法</h3>
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
     * <h3>不支持的文件上传</h3>
     */
    @ExceptionHandler(MultipartException.class)
    public Json multipartExceptionHandle(@NotNull MultipartException exception) {
        logException(exception);
        return responseError(REQUEST_METHOD_UNSUPPORTED, "请使用 multipart 方式上传文件");
    }

    /**
     * <h3>未选择上传文件</h3>
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
     * <h3>未提交必要参数</h3>
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
     * <h3>不支持的数据类型</h3>
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
     * <h3>数据库连接发生错误</h3>
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    public Json databaseExceptionHandle(@NotNull CannotCreateTransactionException exception) {
        logException(exception);
        return responseError(DATABASE_ERROR);
    }

    /**
     * <h3>{@code Redis} 连接发生错误</h3>
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public Json redisExceptionHandle(@NotNull RedisConnectionFailureException exception) {
        logException(exception);
        return responseError(REDIS_ERROR);
    }

    /**
     * <h3>系统自定义异常</h3>
     */
    @ExceptionHandler(ServiceException.class)
    public Json systemExceptionHandle(@NotNull ServiceException exception) {
        logException(exception);
        return responseError(exception).setData(exception.getData());
    }

    /**
     * <h3>数据字段不存在</h3>
     */
    @ExceptionHandler(value = PropertyReferenceException.class)
    public Json propertyReferenceExceptionHandle(@NotNull PropertyReferenceException exception) {
        logException(exception);
        return responseError(DATABASE_UNKNOWN_FIELD, String.format(
                "数据库缺少字段 %s", exception.getPropertyName()
        ));
    }

    /**
     * <h3>数据表或字段异常</h3>
     */
    @ExceptionHandler(value = InvalidDataAccessResourceUsageException.class)
    public Json invalidDataAccessResourceUsageExceptionHandle(
            @NotNull InvalidDataAccessResourceUsageException exception
    ) {
        logException(exception);
        return responseError(DATABASE_TABLE_OR_FIELD_ERROR);
    }

    /**
     * <h3>数据表或字段异常</h3>
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Json maxUploadSizeExceededExceptionHandle(@NotNull MaxUploadSizeExceededException exception) {
        logException(exception);
        return responseError(FORBIDDEN_UPLOAD_MAX_SIZE);
    }

    /**
     * <h3>其他异常</h3>
     */
    @ExceptionHandler(value = {Exception.class, RuntimeException.class})
    public Object otherExceptionHandle(@NotNull Exception exception) {
        logException(exception);
        return responseError(SERVICE_ERROR);
    }

    /**
     * <h3>记录异常信息</h3>
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
     * <h3>返回错误信息</h3>
     *
     * @param serviceError 错误信息
     * @return Json
     */
    private Json responseError(IException<?> serviceError) {
        return responseError(serviceError, serviceError.getMessage());
    }

    /**
     * <h3>返回错误信息</h3>
     *
     * @param serviceError 错误信息
     * @param message      错误信息
     * @return Json
     */
    private Json responseError(IException<?> serviceError, String message) {
        return Json.error(serviceError, message).setRequestId(MDC.get(RequestUtil.REQUEST_ID));
    }
}
