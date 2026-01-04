package cn.hamm.airpower.web.ai;

import cn.hamm.airpower.core.HttpUtil;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.constant.HttpConstant;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.web.exception.ServiceError;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

import static cn.hamm.airpower.core.constant.HttpConstant.ContentType.APPLICATION_JSON_UTF8;
import static cn.hamm.airpower.core.constant.HttpConstant.GrantType.BEARER;
import static cn.hamm.airpower.core.constant.HttpConstant.Header.AUTHORIZATION;
import static cn.hamm.airpower.core.constant.HttpConstant.Header.CONTENT_TYPE;
import static cn.hamm.airpower.web.exception.ServiceError.AI_ERROR;

/**
 * <h1>AI 模型</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.ai")
@Slf4j
public class Ai {
    /**
     * 模型返回数据开始标识
     */
    private static final String FLAG_STREAM_DATA = "data: ";

    /**
     * 模型返回数据结束标识
     */
    private static final String FLAG_STREAM_DONE = "[DONE]";

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
     * 最大 Token
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
        HttpResponse<String> httpResponse = HttpUtil.create()
                .setUrl(url)
                .addHeader(AUTHORIZATION, getBearerToken())
                .post(json);
        AI_ERROR.whenNotEquals(httpResponse.statusCode(), Json.SUCCESS_CODE, "请求失败，AI模型服务异常");
        String response = httpResponse.body();
        try {
            return Json.parse(response, AiResponse.class);
        } catch (Exception e) {
            throw new ServiceException(AI_ERROR, e.getMessage());
        }
    }

    /**
     * 发送流式请求
     *
     * @param request 模型请求参数
     * @param func    回调函数
     */
    public final @NotNull ResponseEntity<StreamingResponseBody> requestStreamRaw(@NotNull AiRequest request, Function<String, String> func) {
        StreamingResponseBody responseBody = outputStream -> {
            HttpResponse<InputStream> httpResponse = getInputStreamHttpResponse(request);
            try (outputStream; outputStream) {
                InputStream inputStream = httpResponse.body();
                try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String apply = func.apply(line);
                        outputStream.write(apply.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new ServiceException(ServiceError.AI_ERROR, e.getMessage());
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"))
                .body(responseBody);
    }

    /**
     * 发送流式请求
     *
     * @param request 模型请求参数
     */
    public final @NotNull ResponseEntity<StreamingResponseBody> requestStreamRaw(@NotNull AiRequest request) {
        return requestStreamRaw(request, line -> line + "\n");
    }

    /**
     * 发送流式请求
     *
     * @param request 模型请求参数
     * @param func    流式处理函数
     */
    public final @NotNull ResponseEntity<StreamingResponseBody> requestStream(@NotNull AiRequest request, Function<AiStream, String> func) {
        return requestStreamRaw(request, line -> {
            line = line.trim();
            AiStream aiStream = new AiStream();
            if (!line.startsWith(FLAG_STREAM_DATA)) {
                return func.apply(aiStream.setResponse(null));
            }

            String replaced = line.replace(FLAG_STREAM_DATA, "");
            if (replaced.equals(FLAG_STREAM_DONE)) {
                return func.apply(aiStream.setIsDone(true));
            }
            AiResponse response = Json.parse(replaced, AiResponse.class);
            return func.apply(aiStream.setResponse(response));
        });
    }

    /**
     * 获取输入流 HTTP 响应
     *
     * @param request 模型请求参数
     * @return 输入流 HTTP 响应
     */
    private @NotNull HttpResponse<InputStream> getInputStreamHttpResponse(@NotNull AiRequest request) {
        try {
            AI_ERROR.whenNull(request, "请求参数错误，请检查请求参数");
            request.setStream(true);
            setRequestParam(request);
            HttpClient httpClient = getHttpClient();
            HttpRequest httpRequest = getHttpRequest(request);
            HttpResponse<InputStream> httpResponse = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            AI_ERROR.whenNotEquals(httpResponse.statusCode(), HttpConstant.Status.OK, "请求失败，AI模型服务异常");
            return httpResponse;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(AI_ERROR, e.getMessage());
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
    @Contract(pure = true)
    private @NotNull String getBearerToken() {
        return BEARER + " " + key;
    }
}
