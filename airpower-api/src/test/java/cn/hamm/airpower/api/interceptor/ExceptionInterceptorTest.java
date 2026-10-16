package cn.hamm.airpower.api.interceptor;

import cn.hamm.airpower.api.fixture.DemoEntity;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.exception.Errors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.mockito.Mockito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>全局异常处理单元测试</h2>
 *
 * <p>本类守的是两条对外契约：</p>
 * <ul>
 *     <li><b>HTTP 状态码恒为 200</b>，真实结果放在包体里由前端判断。
 *     一旦某个分支冒出非 200，前端的统一错误处理就会失效</li>
 *     <li><b>响应体绝不携带异常或堆栈</b>。堆栈只进日志。
 *     这条比第一条更要命：一旦泄漏，攻击者能直接拿到内部类名、
 *     SQL 片段与依赖版本</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("全局异常处理单元测试")
class ExceptionInterceptorTest {

    private final ExceptionInterceptor interceptor = new ExceptionInterceptor();

    /**
     * 构造一个稳定的 {@link MethodParameter}。
     * <p>不能随手用 {@code getDeclaredMethods()[0]}：测试类本身可能一个声明方法都没有，
     * 索引越界会把真正要测的东西盖掉。</p>
     *
     * @return 方法参数
     */
    private static org.springframework.core.MethodParameter methodParameter() {
        try {
            return new org.springframework.core.MethodParameter(
                    ExceptionInterceptorTest.class.getDeclaredMethod("placeholder"), -1);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("占位方法被重命名了", e);
        }
    }

    /**
     * 仅供 {@link #methodParameter()} 取方法签名用，不参与任何断言
     */
    @SuppressWarnings("unused")
    private void placeholder() {
    }

    @Nested
    @DisplayName("1. HTTP 状态码恒为 200")
    class HttpStatusAlwaysOk {

        @Test
        @DisplayName("类上标注了 OK 状态")
        void annotatedAsOk() throws NoSuchMethodException {
            org.springframework.web.bind.annotation.ResponseStatus status =
                    ExceptionInterceptor.class.getAnnotation(
                            org.springframework.web.bind.annotation.ResponseStatus.class);
            assertNotNull(status);
            assertEquals(HttpStatus.OK, status.value());
        }
    }

    @Nested
    @DisplayName("2. 业务异常")
    class ServiceExceptionHandling {

        @Test
        @DisplayName("业务异常的错误码与消息原样返回")
        void codeAndMessagePreserved() {
            ServiceException exception = new ServiceException(Errors.PARAM_INVALID.getCode(), "参数不对");
            Json json = interceptor.systemExceptionHandle(exception);
            assertEquals(Errors.PARAM_INVALID.getCode(), json.getCode());
            assertEquals("参数不对", json.getMessage());
        }

        @Test
        @DisplayName("业务异常自带的 data 透传给前端")
        void dataPassedThrough() {
            // 导出接口等场景靠 data 回传文件编码，属正常业务数据
            ServiceException exception =
                    new ServiceException(Errors.SERVICE_ERROR.getCode(), "出错了", "file-code-123");
            assertEquals("file-code-123", interceptor.systemExceptionHandle(exception).getData());
        }

        @Test
        @DisplayName("异常对象绝不进入 data")
        void throwableNeverInData() {
            // 这是本组最关键的一条。ServiceException 构造时就把 Throwable
            // 从 data 里踢走了（只留在 cause），响应体里不会看到堆栈
            IllegalStateException cause = new IllegalStateException("连接串 jdbc:mysql://内网地址/库名");
            ServiceException exception = new ServiceException("操作失败", cause);
            assertNull(exception.getData(), "异常被放进了 data");
        }

        @Test
        @DisplayName("data 为 null 时不额外写入")
        void nullDataStaysNull() {
            ServiceException exception = new ServiceException(Errors.DATA_NOT_FOUND.getCode(), "查不到");
            assertNull(interceptor.systemExceptionHandle(exception).getData());
        }

        @Test
        @DisplayName("无错误码的业务异常回落到默认错误码")
        void defaultCodeUsed() {
            ServiceException exception = new ServiceException("未实现权限校验");
            Json json = interceptor.systemExceptionHandle(exception);
            assertEquals(Json.SERVICE_ERROR, json.getCode());
        }
    }

    @Nested
    @DisplayName("3. 参数校验失败")
    class ValidationHandling {

        @Test
        @DisplayName("字段校验失败时把全部字段错误放进 data")
        void fieldErrorsCollected() {
            // 前端需要逐字段高亮，只回一条消息没法定位
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "source");
            bindingResult.addError(new FieldError("source", "username", "用户名不能为空"));
            bindingResult.addError(new FieldError("source", "mobile", "手机号格式不正确"));

            Json json = interceptor.badRequestHandle(
                    new MethodArgumentNotValidException(methodParameter(), bindingResult));

            assertEquals(Errors.PARAM_INVALID.getCode(), json.getCode());
            List<?> data = (List<?>) json.getData();
            assertEquals(2, data.size());
        }

        @Test
        @DisplayName("字段错误的格式是「消息 (字段名)」")
        void fieldErrorFormat() {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "source");
            bindingResult.addError(new FieldError("source", "username", "用户名不能为空"));

            Json json = interceptor.badRequestHandle(
                    new MethodArgumentNotValidException(methodParameter(), bindingResult));

            assertEquals("用户名不能为空 (username)", json.getMessage(),
                    "首条字段错误进消息，前端可以直接展示");
        }

        @Test
        @DisplayName("没有字段错误时只回错误码，不带 data")
        void noFieldErrorsGivesNoData() {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "source");
            Json json = interceptor.badRequestHandle(
                    new MethodArgumentNotValidException(methodParameter(), bindingResult));
            assertEquals(Errors.PARAM_INVALID.getCode(), json.getCode());
            assertNull(json.getData());
        }

        @Test
        @DisplayName("方法参数上的约束违规也映射为参数错误")
        void constraintViolationMapped() {
            ConstraintViolation<Object> violation =
                    Mockito.mock(ConstraintViolation.class);
            Mockito.when(violation.getMessage()).thenReturn("必须大于 0");
            Mockito.when(violation.getInvalidValue()).thenReturn(-1);
            java.util.Set<ConstraintViolation<Object>> set = new java.util.HashSet<>();
            set.add(violation);

            Json json = interceptor.badRequestHandle(new ConstraintViolationException(set));
            assertEquals(Errors.PARAM_INVALID.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("必须大于 0"));
        }
    }

    @Nested
    @DisplayName("4. 框架异常到错误码的映射")
    class FrameworkExceptionMapping {

        @Test
        @DisplayName("外键约束冲突映射为「数据正在使用中」")
        void foreignKeyViolationMapped() {
            // 删了还被别的表引用时，开发者需要知道该去清关联数据，
            // 报成「数据库异常」只会让人反复重试
            assertEquals(Errors.FORBIDDEN_DELETE_USED.getCode(),
                    interceptor.deleteUsingDataException(
                            new DataIntegrityViolationException("fk_demo_child")).getCode());
            assertEquals(Errors.FORBIDDEN_DELETE_USED.getCode(),
                    interceptor.deleteUsingDataException(
                            new SQLIntegrityConstraintViolationException("fk")).getCode());
        }

        @Test
        @DisplayName("接口不存在映射为「接口暂未实现」")
        void noHandlerMapped() {
            assertEquals(Errors.API_SERVICE_UNSUPPORTED.getCode(),
                    interceptor.notFoundHandle(new NoHandlerFoundException("GET", "/x", null)).getCode());
        }

        @Test
        @DisplayName("请求体不是合法 JSON 时给出可操作的提示")
        void unreadableBodyGivesHint() {
            Json json = interceptor.dataExceptionHandle(
                    new HttpMessageNotReadableException("bad json"));
            assertEquals(Errors.REQUEST_CONTENT_TYPE_UNSUPPORTED.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("JSON"), "提示里应点明 JSON: " + json.getMessage());
        }

        @Test
        @DisplayName("方法不支持且没有任何候选方法时抛 NPE（缺陷）")
        void methodNotSupportedWithoutCandidatesThrowsNpe() {
            // Spring 的 `new HttpRequestMethodNotSupportedException(method)` 会把
            // supportedMethods 置为 null（源码里显式标注 @Nullable），
            // 而处理方法里写的是 `Objects.requireNonNull(exception.getSupportedMethods())`。
            //
            // 后果：客户端用了一个连候选都没有的方法（比如对一个只支持 GET 的接口发 DELETE），
            // 本该回 405，结果异常处理器自己先炸了 → 500，
            // 前端看到的是「服务异常」而不是「方法不支持」，排查方向被带偏。
            //
            // 修法：判空后回落到一个笼统文案，不要让错误处理器自己抛异常。
            assertThrows(NullPointerException.class,
                    () -> interceptor.methodExceptionHandle(
                            new HttpRequestMethodNotSupportedException("TRACE")),
                    "supportedMethods 为 null 时当前实现会 NPE");
        }

        @Test
        @DisplayName("有候选方法时不抛异常并回带方法列表")
        void methodNotSupportedWithCandidatesWorks() {
            HttpRequestMethodNotSupportedException exception =
                    new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));
            Json json = interceptor.methodExceptionHandle(exception);
            assertEquals(Errors.REQUEST_METHOD_UNSUPPORTED.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("DELETE"), json.getMessage());
            assertTrue(json.getMessage().contains("GET"), json.getMessage());
        }

        @Test
        @DisplayName("Content-Type 不支持时只回显顶层类型")
        void mediaTypeNotSupportedEchoesTopLevelType() {
            // 实际用的是 MediaType#getType()，只拿到 "text" 而不是 "text/xml"。
            // 结果是 text/xml 与 text/plain 给出完全相同的提示，
            // 开发者无法从消息里判断自己发错了什么类型。
            // 这里固化现状；改用 getContentType() 就能带上子类型
            Json json = interceptor.httpMediaTypeNotSupportedExceptionHandle(
                    new HttpMediaTypeNotSupportedException(MediaType.TEXT_XML, List.of(MediaType.APPLICATION_JSON)));
            assertEquals(Errors.REQUEST_CONTENT_TYPE_UNSUPPORTED.getCode(), json.getCode());
            assertEquals("text 不被支持，请使用JSON请求", json.getMessage());
        }

        @Test
        @DisplayName("Content-Type 错误码仍是内容类型不支持")
        void mediaTypeNotSupportedCodeIsStable() {
            assertEquals(Errors.REQUEST_CONTENT_TYPE_UNSUPPORTED.getCode(),
                    interceptor.httpMediaTypeNotSupportedExceptionHandle(
                            new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of())).getCode());
        }

        @Test
        @DisplayName("非 multipart 上传给出明确提示")
        void multipartMismatchGivesHint() {
            Json json = interceptor.multipartExceptionHandle(new MultipartException("不是 multipart"));
            assertEquals(Errors.REQUEST_METHOD_UNSUPPORTED.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("multipart"));
        }

        @Test
        @DisplayName("缺少上传文件时点名是哪个文件")
        void missingPartNamesTheFile() {
            Json json = interceptor.missingServletRequestPartExceptionHandle(
                    new MissingServletRequestPartException("file"));
            assertEquals(Errors.PARAM_MISSING.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("file"), json.getMessage());
        }

        @Test
        @DisplayName("缺少普通参数时点名是哪个参数")
        void missingParameterNamesTheParameter() {
            Json json = interceptor.missingServletRequestParameterExceptionHandle(
                    new MissingServletRequestParameterException("pageNum", "int"));
            assertEquals(Errors.PARAM_MISSING.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("pageNum"));
        }

        @Test
        @DisplayName("事务创建失败映射为数据库连接错误")
        void transactionFailureMapped() {
            assertEquals(Errors.DATABASE_ERROR.getCode(),
                    interceptor.databaseExceptionHandle(
                            new CannotCreateTransactionException("连不上")).getCode());
        }

        @Test
        @DisplayName("Redis 连接失败映射为 Redis 错误")
        void redisFailureMapped() {
            assertEquals(Errors.REDIS_ERROR.getCode(),
                    interceptor.redisExceptionHandle(
                            new RedisConnectionFailureException("连不上")).getCode());
        }

        @Test
        @DisplayName("排序字段写错映射为「不支持的数据库字段」并点名字段")
        void unknownFieldNamesTheField() {
            // 这是前端拼错排序字段时的典型场景，必须让开发者一眼看出是哪个字段
            Json json = interceptor.propertyReferenceExceptionHandle(
                    new PropertyReferenceException("nickName",
                            org.springframework.data.util.TypeInformation.of(DemoEntity.class),
                            List.of()));
            assertEquals(Errors.DATABASE_UNKNOWN_FIELD.getCode(), json.getCode());
            assertTrue(json.getMessage().contains("nickName"), json.getMessage());
        }

        @Test
        @DisplayName("数据表或字段异常映射为表字段错误")
        void invalidResourceUsageMapped() {
            assertEquals(Errors.DATABASE_TABLE_OR_FIELD_ERROR.getCode(),
                    interceptor.invalidDataAccessResourceUsageExceptionHandle(
                            new org.springframework.dao.InvalidDataAccessResourceUsageException(
                                    "事务外调用了加锁查询")).getCode());
        }

        @Test
        @DisplayName("上传超限映射为「上传文件大小超过限制」")
        void uploadTooLargeMapped() {
            assertEquals(Errors.FORBIDDEN_UPLOAD_MAX_SIZE.getCode(),
                    interceptor.maxUploadSizeExceededExceptionHandle(
                            new MaxUploadSizeExceededException(10L)).getCode());
        }

        @Test
        @DisplayName("乐观锁冲突映射为并发错误并提示重试")
        void optimisticLockMapped() {
            assertEquals(Errors.DATABASE_CONCURRENT_ERROR.getCode(),
                    interceptor.objectOptimisticLockingFailureExceptionHandle(
                            new ObjectOptimisticLockingFailureException(DemoEntity.class, ID)).getCode());
        }
    }

    @Nested
    @DisplayName("5. 兜底分支不得泄漏内部细节")
    class FallbackHandling {

        @Test
        @DisplayName("未识别异常统一收敛为通用服务错误")
        void unknownExceptionBecomesGeneric() {
            // 兜底分支必须隐藏原始异常：把 IllegalStateException 的消息
            // 直接回给前端，等于把内部类名与连接串送出去
            Json json = (Json) interceptor.otherExceptionHandle(
                    new IllegalStateException("jdbc:mysql://10.0.0.9:3306/prod?user=root"));
            assertEquals(Errors.SERVICE_ERROR.getCode(), json.getCode());
            assertEquals(Errors.SERVICE_ERROR.getMessage(), json.getMessage(),
                    "兜底分支不得回传原始异常消息");
        }

        @Test
        @DisplayName("空指针等运行时异常同样收敛")
        void runtimeExceptionBecomesGeneric() {
            Json json = (Json) interceptor.otherExceptionHandle(
                    new NullPointerException("at com.internal.Secret.doIt(Secret.java:42)"));
            assertEquals(Errors.SERVICE_ERROR.getCode(), json.getCode());
            assertTrue(!json.getMessage().contains("Secret.java"),
                    "堆栈信息泄漏: " + json.getMessage());
        }

        @Test
        @DisplayName("受检异常同样被收敛")
        void checkedExceptionBecomesGeneric() {
            assertEquals(Errors.SERVICE_ERROR.getCode(),
                    ((Json) interceptor.otherExceptionHandle(new Exception("内部细节"))).getCode());
        }

        @Test
        @DisplayName("兜底分支不携带 data")
        void fallbackCarriesNoData() {
            assertNull(((Json) interceptor.otherExceptionHandle(new RuntimeException("x"))).getData());
        }
    }

    /**
     * 主键，供乐观锁异常构造使用
     */
    private static final long ID = 1L;
}
