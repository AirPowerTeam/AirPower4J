package cn.hamm.airpower.interceptor;

import cn.hamm.airpower.annotation.DesensitizeExclude;
import cn.hamm.airpower.annotation.Filter;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.model.query.QueryPageResponse;
import cn.hamm.airpower.root.RootModel;
import cn.hamm.airpower.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

/**
 * <h1>全局拦截响应</h1>
 *
 * @author Hamm.cn
 */
@ControllerAdvice
@Slf4j
public class ResponseBodyInterceptor implements ResponseBodyAdvice<Object> {
    @Contract(pure = true)
    @Override
    public final boolean supports(
            @NotNull MethodParameter returnType,
            @NotNull Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public final Object beforeBodyWrite(
            Object body,
            @NotNull MethodParameter returnType,
            @NotNull MediaType selectedContentType,
            @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NotNull ServerHttpRequest request,
            @NotNull ServerHttpResponse response
    ) {
        Method method = (Method) getShareData(AbstractRequestInterceptor.REQUEST_METHOD_KEY);
        if (Objects.isNull(method)) {
            return beforeResponseFinished(body, request, response);
        }
        return beforeResponseFinished(getResult(body, method), request, response);
    }

    @Contract("null, _ -> null")
    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    private <M extends RootModel<M>> Object getResult(Object result, Method method) {
        if (!(result instanceof Json json)) {
            // 返回不是JsonData 原样返回
            return result;
        }

        if (Objects.isNull(json.getData())) {
            return result;
        }

        Filter filter = Utils.getReflectUtil().getAnnotation(Filter.class, method);
        DesensitizeExclude desensitizeExclude = Utils.getReflectUtil().getAnnotation(DesensitizeExclude.class, method);
        if (json.getData() instanceof QueryPageResponse) {
            QueryPageResponse<M> queryPageResponse = (QueryPageResponse<M>) json.getData();
            // 如果 data 分页对象
            queryPageResponse.getList().forEach(item -> item.filterAndDesensitize(filter, Objects.isNull(desensitizeExclude)));
            return json.setData(queryPageResponse);
        }

        Class<?> dataCls = json.getData().getClass();
        if (json.getData() instanceof Collection) {
            Collection<?> collection = Utils.getCollectionUtil().getCollectWithoutNull(
                    (Collection<?>) json.getData(), dataCls
            );
            collection.stream().toList().forEach(item -> {
                if (Utils.getReflectUtil().isModel(item.getClass())) {
                    ((M) item).filterAndDesensitize(filter, Objects.isNull(desensitizeExclude));
                }
            });
            return json.setData(collection);
        }
        if (Utils.getReflectUtil().isModel(dataCls)) {
            // 如果 data 是 Model
            //noinspection unchecked
            return json.setData(((M) json.getData()).filterAndDesensitize(filter, Objects.isNull(desensitizeExclude)));
        }

        // 其他数据 原样返回
        return json;
    }

    /**
     * <h2>响应结束前置方法</h2>
     *
     * @param body 响应体
     * @return 响应体
     * @apiNote 如无其他操作，请直接返回<code>body</code>参数即可
     */
    @SuppressWarnings("unused")
    protected Object beforeResponseFinished(Object body, ServerHttpRequest request, ServerHttpResponse response) {
        return body;
    }

    /**
     * <h2>获取共享数据</h2>
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
