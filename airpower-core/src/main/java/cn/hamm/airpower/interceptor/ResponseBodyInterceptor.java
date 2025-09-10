package cn.hamm.airpower.interceptor;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.curd.query.QueryPageResponse;
import cn.hamm.airpower.desensitize.DesensitizeIgnore;
import cn.hamm.airpower.reflect.ReflectUtil;
import cn.hamm.airpower.request.RequestUtil;
import cn.hamm.airpower.root.RootModel;
import cn.hamm.airpower.util.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

import static cn.hamm.airpower.interceptor.AbstractRequestInterceptor.REQUEST_METHOD_KEY;

/**
 * <h1>全局拦截响应</h1>
 *
 * @author Hamm.cn
 */
@ControllerAdvice
@Slf4j
public class ResponseBodyInterceptor implements ResponseBodyAdvice<Object> {
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
        if (Objects.isNull(method)) {
            return beforeResponseFinished(body, request, response);
        }
        return beforeResponseFinished(getResult(body, method), request, response);
    }

    /**
     * 获取响应结果
     *
     * @param result 响应结果
     * @param method 请求的方法
     * @param <M>    数据类型
     * @return 处理后的数据
     */
    @Contract("null, _ -> null")
    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    private <M extends RootModel<M>> Object getResult(Object result, Method method) {
        if (!(result instanceof Json json)) {
            // 返回不是JsonData 原样返回
            return result;
        }
        json.setRequestId(MDC.get(RequestUtil.REQUEST_ID));
        Object data = json.getData();
        if (Objects.isNull(data)) {
            return json;
        }
        DesensitizeIgnore desensitizeIgnore = ReflectUtil.getAnnotation(DesensitizeIgnore.class, method);
        if (Objects.nonNull(desensitizeIgnore)) {
            // 无需脱敏
            return json;
        }
        if (data instanceof QueryPageResponse) {
            // 如果 data 分页对象
            QueryPageResponse<M> queryPageResponse = (QueryPageResponse<M>) json.getData();
            queryPageResponse.getList().forEach(RootModel::desensitize);
            return json.setData(queryPageResponse);
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
                        if (ReflectUtil.isModel(item.getClass())) {
                            ((M) item).desensitize();
                        }
                    });
            return json.setData(collection);
        }
        if (ReflectUtil.isModel(dataCls)) {
            // 如果 data 是 Model
            return json.setData(((M) data).desensitize());
        }
        // 其他数据 原样返回
        return json;
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
}
