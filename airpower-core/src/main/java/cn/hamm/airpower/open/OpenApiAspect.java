package cn.hamm.airpower.open;

import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.helper.RedisHelper;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.util.DateTimeUtil;
import cn.hamm.airpower.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import static cn.hamm.airpower.exception.ServiceError.*;

/**
 * <h1>{@code OpenApi} 切面</h1>
 *
 * @author Hamm.cn
 */
@Aspect
@Component
public class OpenApiAspect<S extends IOpenAppService, LS extends IOpenLogService> {
    /**
     * <h3>防重放时长</h3>
     */
    private static final int NONCE_CACHE_SECOND = 300;

    /**
     * <h3>防重放缓存前缀</h3>
     */
    private static final String NONCE_CACHE_PREFIX = "NONCE_";

    @Autowired(required = false)
    private S openAppService;

    @Autowired(required = false)
    private LS openLogService;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private RedisHelper redisHelper;

    @SuppressWarnings("EmptyMethod")
    @Pointcut("@annotation(cn.hamm.airpower.open.OpenApi)")
    public void pointCut() {

    }

    /**
     * <h3>{@code OpenApi} 切面</h3>
     */
    @Around("pointCut()")
    public Object openApi(@NotNull ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        validOpenApi(proceedingJoinPoint);
        OpenRequest openRequest = getOpenRequest(proceedingJoinPoint);
        Long openLogId = null;
        String response = "";
        try {
            IOpenApp openApp = getOpenApp(openRequest);
            checkIpWhiteList(openApp);
            openRequest.checkSignature(openApp);
            Object object = proceedingJoinPoint.proceed();
            openLogId = addOpenLog(
                    openRequest.getOpenApp(),
                    request.getRequestURI(),
                    openRequest.decodeContent()
            );
            if (object instanceof Json json) {
                // 日志记录原始数据
                response = Json.toString(json);
                // 如果是Json 需要将 Json.data 对输出的数据进行加密
                json.setData(OpenResponse.encodeResponse(openRequest.getOpenApp(), json.getData()));
            }
            updateLogResponse(openLogId, response);
            return object;
        } catch (ServiceException serviceException) {
            updateLogResponse(openLogId, serviceException);
            throw serviceException;
        } catch (Exception exception) {
            updateLogResponse(openLogId, exception);
            throw exception;
        }
    }

    /**
     * <h3>验证切面点是否支持OpenApi</h3>
     *
     * @param proceedingJoinPoint {@code ProceedingJoinPoint}
     */
    private void validOpenApi(@NotNull ProceedingJoinPoint proceedingJoinPoint) {
        Signature signature = proceedingJoinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        OpenApi openApi = method.getAnnotation(OpenApi.class);
        API_SERVICE_UNSUPPORTED.whenNull(openApi);
    }

    /**
     * <h3>获取OpenApi请求参数</h3>
     *
     * @param proceedingJoinPoint {@code ProceedingJoinPoint}
     * @return {@code OpenRequest}
     */
    private @NotNull OpenRequest getOpenRequest(@NotNull ProceedingJoinPoint proceedingJoinPoint) {
        Object[] args = proceedingJoinPoint.getArgs();
        SERVICE_ERROR.when(args.length != 1, "OpenApi必须接收一个参数");
        if (!(args[0] instanceof OpenRequest openRequest)) {
            throw new ServiceException("OpenApi必须接收一个OpenRequest参数");
        }
        checkTimestamp(openRequest.getTimestamp());
        checkNonce(openRequest.getNonce());
        return openRequest;
    }

    /**
     * <h3>时间戳检测</h3>
     *
     * @param timestamp 时间戳
     */
    private void checkTimestamp(long timestamp) {
        long currentTimeMillis = System.currentTimeMillis();
        int nonceExpireMillisecond = NONCE_CACHE_SECOND * DateTimeUtil.MILLISECONDS_PER_SECOND;
        TIMESTAMP_INVALID.when(
                timestamp > currentTimeMillis + nonceExpireMillisecond ||
                        timestamp < currentTimeMillis - nonceExpireMillisecond
        );
    }

    /**
     * <h3>获取请求的应用</h3>
     *
     * @param openRequest {@code OpenRequest}
     * @return {@code OpenApp}
     */
    private @NotNull IOpenApp getOpenApp(@NotNull OpenRequest openRequest) {
        INVALID_APP_KEY.when(!StringUtils.hasText(openRequest.getAppKey()));
        SERVICE_ERROR.whenNull(openAppService, "注入OpenAppService失败");
        IOpenApp openApp = openAppService.getByAppKey(openRequest.getAppKey());
        INVALID_APP_KEY.whenNull(openApp);
        FORBIDDEN_OPEN_APP_DISABLED.when(openApp.getIsDisabled());
        return openApp;
    }

    /**
     * <h3>验证IP白名单</h3>
     */
    private void checkIpWhiteList(@NotNull IOpenApp openApp) {
        final String ipStr = openApp.getIpWhiteList();
        if (!StringUtils.hasText(ipStr)) {
            // 未配置IP白名单
            return;
        }
        final String[] ipList = ipStr.split("\n");
        final String ip = RequestUtil.getIpAddress(request);
        if (!StringUtils.hasText(ip)) {
            MISSING_REQUEST_ADDRESS.show();
        }
        if (Arrays.stream(ipList).map(String::trim).toList().contains(ip)) {
            return;
        }
        INVALID_REQUEST_ADDRESS.show();
    }

    /**
     * <h3>添加日志</h3>
     *
     * @param openApp     {@code OpenApp}
     * @param url         请求 {@code URL}
     * @param requestBody 请求数据
     * @return 日志ID
     */
    private @Nullable Long addOpenLog(IOpenApp openApp, String url, String requestBody) {
        if (Objects.nonNull(openLogService)) {
            return openLogService.addRequest(openApp, url, requestBody);
        }
        return null;
    }

    /**
     * <h3>防重放检测</h3>
     */
    private void checkNonce(String nonce) {
        String cacheKey = NONCE_CACHE_PREFIX + nonce;
        Object savedNonce = redisHelper.get(cacheKey);
        REPEAT_REQUEST.whenNotNull(savedNonce);
        redisHelper.set(cacheKey, 1, NONCE_CACHE_SECOND);
    }

    /**
     * <h3>更新日志返回数据</h3>
     *
     * @param openLogId    日志 {@code ID}
     * @param responseBody 返回值
     */
    private void updateLogResponse(Long openLogId, String responseBody) {
        if (Objects.isNull(openLogId) || Objects.isNull(openLogService)) {
            return;
        }
        openLogService.updateResponse(openLogId, responseBody);
    }

    /**
     * <h3>更新日志异常</h3>
     *
     * @param openLogId        日志 {@code ID}
     * @param serviceException 异常
     */
    private void updateLogResponse(Long openLogId, @NotNull ServiceException serviceException) {
        updateLogResponse(openLogId, Json.toString(Json.create()
                .setCode(serviceException.getCode())
                .setMessage(serviceException.getMessage())
        ));
    }

    /**
     * <h3>更新日志异常</h3>
     *
     * @param openLogId 日志 {@code ID}
     * @param exception 异常
     */
    private void updateLogResponse(Long openLogId, @NotNull Exception exception) {
        updateLogResponse(openLogId, Json.toString(Json.create().setMessage(exception.getMessage())));
    }
}
