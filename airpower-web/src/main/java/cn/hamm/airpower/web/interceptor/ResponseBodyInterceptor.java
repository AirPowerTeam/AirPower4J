package cn.hamm.airpower.web.interceptor;

import cn.hamm.airpower.core.*;
import cn.hamm.airpower.core.annotation.DesensitizeIgnore;
import cn.hamm.airpower.core.annotation.ExposeAll;
import cn.hamm.airpower.core.constant.HttpConstant;
import cn.hamm.airpower.web.api.ApiConfig;
import cn.hamm.airpower.web.api.ApiController;
import cn.hamm.airpower.web.api.DisableLog;
import cn.hamm.airpower.web.curd.CurdController;
import cn.hamm.airpower.web.curd.query.QueryPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static cn.hamm.airpower.web.interceptor.AbstractRequestInterceptor.REQUEST_CONTROLLER_KEY;
import static cn.hamm.airpower.web.interceptor.AbstractRequestInterceptor.REQUEST_METHOD_KEY;

/**
 * <h1>全局拦截响应</h1>
 *
 * @author Hamm.cn
 */
@ControllerAdvice
@Slf4j
public class ResponseBodyInterceptor implements ResponseBodyAdvice<Object> {
    @Autowired
    private ApiConfig apiConfig;

    /**
     * 是否支持
     *
     * @param returnType    请求方法
     * @param converterType 转换器
     */
    @Contract(pure = true)
    @Override
    public final boolean supports(
            @NotNull MethodParameter returnType,
            @NotNull Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    /**
     * 响应结果处理前置
     *
     * @param body                  输出数据
     * @param returnType            请求方法
     * @param selectedContentType   选择的数据类型
     * @param selectedConverterType 选择的转换器
     * @param request               请求
     * @param response              响应
     * @return 处理后的结果
     */
    @Override
    public final Object beforeBodyWrite(
            Object body,
            @NotNull MethodParameter returnType,
            @NotNull MediaType selectedContentType,
            @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response
    ) {
        Method method = (Method) getShareData(REQUEST_METHOD_KEY);
        ApiController controller = (ApiController) getShareData(REQUEST_CONTROLLER_KEY);
        Object responseResult;
        if (Objects.isNull(method)) {
            responseResult = beforeResponseFinished(body, request, response);
        } else {
            responseResult = beforeResponseFinished(getResult(body, controller, method), request, response);
        }
        if (!apiConfig.getBodyTraceId()) {
            // 不在 Body 中响应 那么在 Header 中响应
            String traceId = TraceUtil.getTraceId();
            response.getHeaders().set(HttpConstant.Header.TRACE_ID, traceId);
        }
        if (method != null) {
            DisableLog disableLog = ReflectUtil.getAnnotation(DisableLog.class, method);
            if (Objects.nonNull(disableLog) && disableLog.value()) {
                // 禁用日志
                return responseResult;
            }
        }
        if (apiConfig.getRequestLog()) {
            log.info("请求包体 {}", getRequestBody(((ServletServerHttpRequest) request).getServletRequest()));
        }
        if (apiConfig.getResponseLog()) {
            log.info("响应包体 {}", Json.toString(responseResult));
        }
        return responseResult;
    }

    /**
     * 获取响应结果
     *
     * @param result     响应结果
     * @param controller 控制器实例
     * @param method     请求的方法
     * @return 处理后的数据
     */
    @Contract("null, _, _ -> null")
    private Object getResult(Object result, ApiController controller, Method method) {
        if (!(result instanceof Json json)) {
            // 返回不是JsonData 原样返回
            return result;
        }
        if (apiConfig.getBodyTraceId()) {
            json.setTraceId(TraceUtil.getTraceId());
        }
        Object data = json.getData();
        if (Objects.isNull(data)) {
            return json;
        }

        // 获取暴露所有字段的类列表
        @NotNull List<Class<? extends RootModel<?>>> exposeModelsFieldNotMeta;
        ExposeAll exposeAll = ReflectUtil.getAnnotation(ExposeAll.class, method);
        if (Objects.nonNull(exposeAll)) {
            exposeModelsFieldNotMeta = Arrays.stream(exposeAll.value()).toList();
        } else {
            exposeModelsFieldNotMeta = new ArrayList<>();
            try {
                Class<? extends RootModel<?>> entityClass = null;
                // 如果没有标记 自动读取实体类
                if (controller instanceof CurdController<?, ?, ?> curdController) {
                    entityClass = curdController.getEntityClass();
                }
                if (Objects.nonNull(entityClass)) {
                    exposeModelsFieldNotMeta.add(entityClass);
                }
            } catch (Exception exception) {
                log.error(exception.getMessage(), exception);
            }
        }

        // 是否需要忽略脱敏
        DesensitizeIgnore desensitizeIgnore = ReflectUtil.getAnnotation(DesensitizeIgnore.class, method);
        boolean isDesensitize = Objects.isNull(desensitizeIgnore);

        Object object = dataResolver(data, exposeModelsFieldNotMeta, isDesensitize);
        json.setData(object);
        // 其他数据 原样返回
        return json;
    }

    /**
     * 数据处理
     *
     * @param data                     数据
     * @param exposeModelsFieldNotMeta 暴露所有字段的类列表
     * @param isDesensitize            是否需要脱敏
     * @param <M>                      数据类型
     * @return 处理后的数据
     */
    private <M extends RootModel<M>> @NotNull Object dataResolver(@NotNull Object data, @NotNull List<Class<? extends RootModel<?>>> exposeModelsFieldNotMeta, boolean isDesensitize) {
        if (data instanceof QueryPageResponse) {
            // 如果 data 分页对象
            @SuppressWarnings("unchecked")
            QueryPageResponse<M> queryPageResponse = (QueryPageResponse<M>) data;
            queryPageResponse.getList().forEach(item -> dataResolver(item, exposeModelsFieldNotMeta, isDesensitize));
            return queryPageResponse;
        }
        Class<?> dataCls = data.getClass();
        if (data instanceof Collection) {
            // 如果是集合
            Collection<?> collection = CollectionUtil.getCollectWithoutNull(
                    (Collection<?>) data, dataCls
            );
            collection.stream()
                    .toList()
                    .forEach(item -> {
                        if (RootModel.isModel(item.getClass())) {
                            dataResolver(item, exposeModelsFieldNotMeta, isDesensitize);
                        }
                    });
            return collection;
        }
        if (RootModel.isModel(dataCls)) {
            // 如果 data 是 Model
            @SuppressWarnings("unchecked")
            M model = ((M) data);
            model.exclude(exposeModelsFieldNotMeta, isDesensitize);
            return model;
        }
        return data;
    }

    /**
     * 响应结束前置方法
     *
     * @param body 响应体
     * @return 响应体
     * @apiNote 如无其他操作，请直接返回 body 参数即可
     */
    @SuppressWarnings("unused")
    protected Object beforeResponseFinished(Object body, ServerHttpRequest request, ServerHttpResponse response) {
        return body;
    }

    /**
     * 获取共享数据
     *
     * @param key KEY
     * @return VALUE
     */
    protected final @Nullable Object getShareData(String key) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(requestAttributes)) {
            return null;
        }
        return requestAttributes.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * 获取请求体
     *
     * @param request 请求
     * @return 请求体
     */
    protected String getRequestBody(HttpServletRequest request) {
        String requestBody = "";
        // 判断是否是包装过的请求
        if (request instanceof ContentCachingRequestWrapper wrappedRequest) {
            byte[] bodyBytes = wrappedRequest.getContentAsByteArray();
            requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
        }
        return requestBody;
    }
}
