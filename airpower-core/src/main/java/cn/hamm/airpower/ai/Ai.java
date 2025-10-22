package cn.hamm.airpower.ai;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.exception.ServiceError;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.request.HttpConstant.STATUS;
import cn.hamm.airpower.request.HttpUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Consumer;

import static cn.hamm.airpower.exception.ServiceError.AI_ERROR;
import static cn.hamm.airpower.request.HttpConstant.ContentType.APPLICATION_JSON_UTF8;
import static cn.hamm.airpower.request.HttpConstant.GrantType.BEARER;
import static cn.hamm.airpower.request.HttpConstant.Header.AUTHORIZATION;
import static cn.hamm.airpower.request.HttpConstant.Header.CONTENT_TYPE;

/**
 * <h1>AI模型</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.ai")
@Slf4j
public class Ai {
    /**
     * 模型返回数据结束标识
     */
    public static final String FLAG_STREAM_DONE = "[DONE]";

    /**
     * 模型返回数据开始标识
     */
    public static final String FLAG_STREAM_DATA = "data: ";

    /**
     * 请求地址
     */
    private String url = "https://api.siliconflow.cn/v1/chat/completions";

    /**
     * 调用密钥
     */
    private String key;

    /**
     * 模型名称
     */
    private String model = "Qwen/Qwen3-8B";

    /**
     * 最大Token
     */
    private Integer maxToken = 4096;

    /**
     * 思考功能
     */
    private Boolean enableThinking = false;

    /**
     * 发送同步请求
     *
     * @param request 请求参数
     * @return 响应结果
     */
    public final AiResponse request(AiRequest request) {
        AI_ERROR.whenNull(request, "请求参数错误，请检查请求参数");
        request.setStream(false);
        setRequestParam(request);
        String json = Json.toString(request);
        System.out.println(json);
        System.out.println(url);
        HttpResponse<String> httpResponse = HttpUtil.create()
                .setUrl(url)
                .addHeader(AUTHORIZATION, getBearerToken())
                .post(json);
        AI_ERROR.whenNotEquals(httpResponse.statusCode(), STATUS.OK, "请求失败，AI模型服务异常");
        String response = httpResponse.body();
        try {
            return Json.parse(response, AiResponse.class);
        } catch (Exception e) {
            throw new ServiceException(AI_ERROR, e.getMessage());
        }
    }

    /**
     * 发送异步请求
     *
     * @param request  模型请求参数
     * @param callback 回调函数
     */
    public final void requestAsync(@NotNull AiRequest request, Consumer<AiStream> callback) {
        AI_ERROR.whenNull(request, "请求参数错误，请检查请求参数");
        request.setStream(true);
        setRequestParam(request);
        HttpClient httpClient = getHttpClient();
        HttpRequest httpRequest = getHttpRequest(request);
        try {
            HttpResponse<InputStream> httpResponse = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            AI_ERROR.whenNotEquals(httpResponse.statusCode(), STATUS.OK, "请求失败，AI模型服务异常");
            InputStream inputStream = httpResponse.body();
            try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 去除首尾空格
                    line = line.trim();
                    if (!line.startsWith(FLAG_STREAM_DATA)) {
                        continue;
                    }

                    String replaced = line.replace(FLAG_STREAM_DATA, "");
                    if (replaced.equals(FLAG_STREAM_DONE)) {
                        callback.accept(new AiStream().setIsDone(true));
                        break;
                    }
                    AiResponse response = Json.parse(replaced, AiResponse.class);
                    callback.accept(new AiStream().setResponse(response));
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(ServiceError.AI_ERROR, e.getMessage());
        }

    }

    /**
     * 设置请求参数
     *
     * @param request 模型请求参数
     */
    private void setRequestParam(@NotNull AiRequest request) {
        request
                .setEnableThinking(enableThinking)
                .setModel(Objects.requireNonNullElse(request.getModel(), model))
                .setMaxToken(Objects.requireNonNullElse(request.getMaxToken(), maxToken));
    }

    /**
     * 获取 HttpClient
     *
     * @return HttpClient
     */
    private HttpClient getHttpClient() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        return httpClientBuilder.build();
    }

    /**
     * 获取 HttpRequest 对象
     *
     * @return HttpRequest
     */
    private HttpRequest getHttpRequest(AiRequest request) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(Json.toString(request));
        requestBuilder.POST(bodyPublisher);
        requestBuilder.setHeader(AUTHORIZATION, getBearerToken());
        requestBuilder.header(CONTENT_TYPE, APPLICATION_JSON_UTF8);
        return requestBuilder.build();
    }

    /**
     * 获取授权请求参数
     *
     * @return 授权请求参数
     */
    private @NotNull String getBearerToken() {
        return BEARER + " " + key;
    }
}
