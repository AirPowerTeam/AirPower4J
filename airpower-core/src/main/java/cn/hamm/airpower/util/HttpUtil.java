package cn.hamm.airpower.util;

import cn.hamm.airpower.enums.ContentType;
import cn.hamm.airpower.enums.HttpMethod;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.helper.CookieHelper;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static cn.hamm.airpower.enums.ContentType.JSON;
import static cn.hamm.airpower.enums.HttpMethod.GET;

/**
 * <h1>{@code HTTP} 请求工具类</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true, makeFinal = true)
public class HttpUtil {
    /**
     * <h3>{@code ContentType}</h3>
     */
    public static final String CONTENT_TYPE = "Content-Type";

    /**
     * <h3>请求头</h3>
     */
    private Map<String, Object> headers = new HashMap<>();

    /**
     * <h3>{@code Cookie}</h3>
     */
    private Map<String, Object> cookies = new HashMap<>();

    /**
     * <h3>请求地址</h3>
     */
    private String url;

    /**
     * <h3>请求体</h3>
     */
    private String body = "";

    /**
     * <h3>请求方法</h3>
     */
    private HttpMethod method = GET;

    /**
     * <h3>请求体类型</h3>
     */
    private ContentType contentType = JSON;

    /**
     * <h3>连接超时时间</h3>
     */
    private int connectTimeout = 5;

    /**
     * <h3>禁止外部实例化</h3>
     */
    private HttpUtil() {
    }

    /**
     * <h3>创建一个 {@code HttpUtil} 对象</h3>
     *
     * @return {@code HttpUtil}
     */
    @Contract(" -> new")
    public static @NotNull HttpUtil create() {
        return new HttpUtil();
    }

    /**
     * <h3>添加 {@code Cookie}</h3>
     *
     * @param key   {@code Cookie} 键
     * @param value {@code Cookie} 值
     * @return {@code HttpUtil}
     */
    @Contract("_, _ -> this")
    public final HttpUtil addCookie(String key, String value) {
        cookies.put(key, value);
        return this;
    }

    /**
     * <h3>发送 {@code POST} 请求</h3>
     *
     * @return {@code HttpResponse}
     */
    public final HttpResponse<String> post() {
        method = HttpMethod.POST;
        return send();
    }

    /**
     * <h3>发送 {@code POST} 请求</h3>
     *
     * @param body 请求体
     * @return {@code HttpResponse}
     */
    public final HttpResponse<String> post(String body) {
        method = HttpMethod.POST;
        this.body = body;
        return send();
    }

    /**
     * <h3>发送GET请求</h3>
     *
     * @return {@code HttpResponse}
     */
    public final HttpResponse<String> get() {
        method = GET;
        return send();
    }

    /**
     * <h3>发送请求</h3>
     *
     * @return {@code HttpResponse}
     */
    public final HttpResponse<String> send() {
        try {
            return getHttpClient().send(getHttpRequest(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * <h3>获取 {@code HttpRequest} 对象</h3>
     *
     * @return {@code HttpRequest}
     */
    private HttpRequest getHttpRequest() {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach((key, value) -> requestBuilder.header(key, value.toString()));
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
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
                    CookieHelper.COOKIE, String.join(CookieHelper.COOKIE_VALUE_DELIMITER, cookieList)
            );
        }
        if (Objects.nonNull(contentType)) {
            requestBuilder.header(CONTENT_TYPE, contentType.getValue());
        }
        return requestBuilder.build();
    }

    /**
     * <h3>获取 {@code HttpClient}</h3>
     *
     * @return {@code HttpClient}
     */
    private HttpClient getHttpClient() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        if (connectTimeout > 0) {
            httpClientBuilder.connectTimeout(Duration.ofSeconds(connectTimeout));
        }
        return httpClientBuilder.build();
    }

    /**
     * <h3>添加 {@code Header}</h3>
     *
     * @param key   {@code Header} 键
     * @param value {@code Header} 值
     * @return {@code HttpUtil}
     */
    @Contract("_, _ -> this")
    public final HttpUtil addHeader(String key, Object value) {
        headers.put(key, value);
        return this;
    }
}
