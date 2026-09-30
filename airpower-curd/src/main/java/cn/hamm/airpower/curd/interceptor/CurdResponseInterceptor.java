package cn.hamm.airpower.curd.interceptor;

import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.api.RequestUtil;
import cn.hamm.airpower.api.config.ApiConfig;
import cn.hamm.airpower.core.*;
import cn.hamm.airpower.core.annotation.DesensitizeIgnore;
import cn.hamm.airpower.core.annotation.ExposeAll;
import cn.hamm.airpower.curd.annotation.DisableRequestLog;
import cn.hamm.airpower.curd.annotation.DisableResponseLog;
import cn.hamm.airpower.curd.base.CurdController;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
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

import static cn.hamm.airpower.curd.interceptor.CurdRequestInterceptor.REQUEST_CONTROLLER_KEY;
import static cn.hamm.airpower.curd.interceptor.CurdRequestInterceptor.REQUEST_METHOD_KEY;

/**
 * <h1>全局响应拦截器</h1>
 *
 * @author Hamm.cn
 * @apiNote 统一处理三件事：按 {@code @Meta}/{@code @Desensitize} 裁剪出参、
 * 写入 {@code traceId}、打印请求响应日志
 */
@ControllerAdvice
@Slf4j
public class CurdResponseInterceptor implements ResponseBodyAdvice<Object> {
    @Autowired
    private ApiConfig apiConfig;

    /**
     * 是否支持
     *
     * @param returnType    请求方法
     * @param converterType 转换器
     * @return 恒为 {@code true}，对所有出参生效
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
            responseResult = beforeResponseFinished(getResponseBody(body, controller, method), request, response);
        }
        printLog(method, request, Json.toString(responseResult));
        return responseResult;
    }

    /**
     * 获取响应包体
     *
     * @param method         请求的方法
     * @param responseResult 响应的包体
     * @return 响应的包体，全局关闭或方法标记禁用时返回空串
     */
    private String getResponseString(Method method, String responseResult) {
        String response = "";
        if (!apiConfig.getResponseLog()) {
            return response;
        }
        if (method != null) {
            DisableResponseLog disableResponseLog = ReflectUtil.getAnnotation(DisableResponseLog.class, method);
            if (Objects.nonNull(disableResponseLog) && disableResponseLog.value()) {
                // 禁用日志
                return response;
            }
        }
        return responseResult;
    }

    /**
     * 打印请求响应日志
     *
     * @param method   请求的方法
     * @param request  请求
     * @param response 响应的包体
     */
    private void printLog(Method method, @NotNull ServerHttpRequest request, String response) {
        Map<String, Object> mapLogs = new HashMap<>();
        Map<String, Object> headers = getHeaderMap(request);
        mapLogs.put("headers", headers);
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        mapLogs.put("request", getRequestString(method, servletRequest));
        mapLogs.put("response", getResponseString(method, response));
        log.info("请求响应: {} {} {} {}",
                request.getMethod().name(),
                request.getURI(),
                RequestUtil.getIpAddress(servletRequest),
                Json.toString(mapLogs)
        );
    }

    /**
     * 获取请求包体
     *
     * @param method             请求的方法
     * @param httpServletRequest 请求
     * @return 请求包体，全局关闭、方法标记禁用或请求未被包装时返回空串
     */
    private String getRequestString(Method method, HttpServletRequest httpServletRequest) {
        String request = "";
        if (!apiConfig.getRequestLog()) {
            return request;
        }
        if (method != null) {
            DisableRequestLog disableRequestLog = ReflectUtil.getAnnotation(DisableRequestLog.class, method);
            if (Objects.nonNull(disableRequestLog) && disableRequestLog.value()) {
                // 禁用日志
                return request;
            }
        }
        return getRequestBody(httpServletRequest);
    }

    /**
     * 获取请求头
     *
     * @param request 请求
     * @return 请求头，只含配置里白名单列出的头
     */
    private @NotNull Map<String, Object> getHeaderMap(@NotNull ServerHttpRequest request) {
        Map<String, Object> mapHeaders = new HashMap<>();
        HttpHeaders headers = request.getHeaders();
        String[] requestLogHeaders = apiConfig.getRequestLogHeaders();
        for (String key : requestLogHeaders) {
            Object value = headers.getFirst(key);
            mapHeaders.put(key, value);
        }
        return mapHeaders;
    }

    /**
     * 获取响应结果
     *
     * @param result     响应结果
     * @param controller 控制器实例
     * @param method     请求的方法
     * @return 处理后的数据
     * @apiNote 只有 {@link Json} 会被处理，控制器直接返回裸对象时原样放行
     * @apiNote 未标记 {@link ExposeAll} 时自动把控制器的实体类加入白名单，
     * 即 CURD 接口默认返回实体的全部 {@code @Meta} 字段
     */
    @Contract("null, _, _ -> null")
    private <M extends RootModel<M>> Object getResponseBody(Object result, ApiController controller, Method method) {
        if (!(result instanceof Json json)) {
            // 返回不是JsonData 原样返回
            return result;
        }
        json.setTraceId(TraceUtil.getTraceId());
        Object data = json.getData();
        if (Objects.isNull(data)) {
            return json;
        }

        // 获取暴露所有字段的类列表
        @NotNull List<Class<? extends RootModel<?>>> whiteList;
        ExposeAll exposeAll = ReflectUtil.getAnnotation(ExposeAll.class, method);
        if (Objects.nonNull(exposeAll)) {
            whiteList = Arrays.stream(exposeAll.value()).toList();
        } else {
            whiteList = new ArrayList<>();
            try {
                Class<M> entityClass = null;
                // 如果没有标记 自动读取实体类
                if (controller instanceof CurdController<?, ?, ?> curdController) {
                    //noinspection unchecked
                    entityClass = (Class<M>) curdController.getEntityClass();
                }
                if (Objects.nonNull(entityClass)) {
                    whiteList.add(entityClass);
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        // 是否需要忽略脱敏
        DesensitizeIgnore desensitizeIgnore = ReflectUtil.getAnnotation(DesensitizeIgnore.class, method);
        boolean isDesensitize = Objects.isNull(desensitizeIgnore);

        Object object = filterModelValue(data, whiteList, isDesensitize);
        json.setData(object);
        // 其他数据 原样返回
        return json;
    }

    /**
     * 响应结束前置方法
     *
     * @param body     响应体
     * @param request  请求
     * @param response 响应
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
     * @apiNote 只能读到 {@code ContentCachingRequestWrapper} 缓存下来的内容，
     * 请求必须经过 {@code RequestFilter} 包装，否则恒返回空串
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

    /**
     * 模型数据过滤
     *
     * @param data          数据
     * @param classList     暴露所有字段的类列表
     * @param isDesensitize 是否需要脱敏
     * @param <M>           数据类型
     * @return 处理后的数据
     * @apiNote 递归下钻分页对象与集合，原地修改元素后返回，调用方拿到的仍是同一批对象
     */
    private <M extends RootModel<M>> @NotNull Object filterModelValue(
            @NotNull Object data,
            @NotNull List<Class<? extends RootModel<?>>> classList,
            boolean isDesensitize
    ) {
        if (data instanceof QueryPageResponse) {
            // 如果 data 分页对象
            @SuppressWarnings("unchecked")
            QueryPageResponse<M> queryPageResponse = (QueryPageResponse<M>) data;
            queryPageResponse.getList().forEach(item -> filterModelValue(item, classList, isDesensitize));
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
                            filterModelValue(item, classList, isDesensitize);
                        }
                    });
            return collection;
        }
        if (RootModel.isModel(dataCls)) {
            // 如果 data 是 Model
            @SuppressWarnings("unchecked")
            M model = ((M) data);
            model.excludeNotMetaAndDesensitize(classList, isDesensitize);
            return model;
        }
        return data;
    }
}
