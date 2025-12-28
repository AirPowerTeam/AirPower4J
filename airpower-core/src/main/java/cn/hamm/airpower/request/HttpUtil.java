package cn.hamm.airpower.request;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.request.HttpConstant.Header;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static cn.hamm.airpower.request.HttpMethod.GET;

/**
 * <h1>HTTP 请求工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Data
@Accessors(chain = true, makeFinal = true)
public class HttpUtil {
    /**
     * 请求头
     */
    private Map<String, Object> headers = new HashMap<>();

    /**
     * Cookie
     */
    private Map<String, Object> cookies = new HashMap<>();

    /**
     * 请求地址
     */
    private String url;

    /**
     * 请求体
     */
    private String body = "";

    /**
     * 请求方法
     */
    private HttpMethod method = GET;

    /**
     * 请求体类型
     */
    private String contentType = HttpConstant.ContentType.APPLICATION_JSON_UTF8;

    /**
     * 连接超时时间
     */
    private int connectTimeout = 5;

    /**
     * 禁止外部实例化
     */
    private HttpUtil() {
    }

    /**
     * 创建一个 HttpUtil 对象
     *
     * @return HttpUtil
     */
    @Contract(" -> new")
    public static @NotNull HttpUtil create() {
        return new HttpUtil();
    }

    /**
     * 添加  Cookie
     *
     * @param key   Cookie 键
     * @param value Cookie 值
     * @return HttpUtil
     */
    @Contract("_, _ -> this")
    public final HttpUtil addCookie(String key, String value) {
        cookies.put(key, value);
        return this;
    }

    /**
     * 发送 POST 请求
     *
     * @return HttpResponse
     */
    public final @NotNull HttpResponse<String> post() {
        method = HttpMethod.POST;
        return send();
    }

    /**
     * 发送 POST 请求
     *
     * @param body 请求体
     * @return HttpResponse
     */
    @SuppressWarnings("UnusedReturnValue")
    public final @NotNull HttpResponse<String> post(String body) {
        method = HttpMethod.POST;
        this.body = body;
        return send();
    }

    /**
     * 发送 GET 请求
     *
     * @return HttpResponse
     */
    public final @NotNull HttpResponse<String> get() {
        method = GET;
        return send();
    }

    /**
     * 发送请求
     *
     * @return HttpResponse
     */
    public final @NotNull HttpResponse<String> send() {
        try {
            HttpResponse<String> response = getHttpClient().send(getHttpRequest(), HttpResponse.BodyHandlers.ofString());
            log.info("响应码: {}", response.statusCode());
            log.info("响应体: {}", response.body());
            return response;
        } catch (Exception exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * 获取 HttpRequest 对象
     *
     * @return HttpRequest
     */
    private HttpRequest getHttpRequest() {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach((key, value) -> requestBuilder.header(key, value.toString()));
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
        log.info("请求地址: {} {}", method.name(), url);
        log.info("请求头: {}", Json.toString(headers));
        log.info("请求体: {}", body);
        switch (method) {
            case GET -> requestBuilder.GET();
            case POST -> requestBuilder.POST(bodyPublisher);
            case PUT -> requestBuilder.PUT(bodyPublisher);
            case DELETE -> requestBuilder.DELETE();
            default -> throw new ServiceException("不支持的请求方法");
        }
        if (Objects.nonNull(cookies)) {
            List<String> cookieList = new ArrayList<>();
            cookies.forEach((key, value) -> cookieList.add(key + "=" + value));
            requestBuilder.setHeader(
                    Header.COOKIE, String.join("; ", cookieList)
            );
        }
        if (Objects.nonNull(contentType)) {
            requestBuilder.header(Header.CONTENT_TYPE, contentType);
        }
        return requestBuilder.build();
    }

    /**
     * 获取 HttpClient
     *
     * @return HttpClient
     */
    private HttpClient getHttpClient() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        if (connectTimeout > 0) {
            httpClientBuilder.connectTimeout(Duration.ofSeconds(connectTimeout));
        }
        return httpClientBuilder.build();
    }

    /**
     * 添加 Header
     *
     * @param key   Header 键
     * @param value Header 值
     * @return HttpUtil
     */
    @Contract("_, _ -> this")
    public final HttpUtil addHeader(String key, Object value) {
        headers.put(key, value);
        return this;
    }
}
