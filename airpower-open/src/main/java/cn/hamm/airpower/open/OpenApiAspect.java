package cn.hamm.airpower.open;

import cn.hamm.airpower.api.RequestUtil;
import cn.hamm.airpower.api.curd.CurdEntity;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.crypto.RsaUtil;
import cn.hamm.airpower.core.datetime.DateTimeUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.redis.RedisHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Objects;

import static cn.hamm.airpower.exception.Errors.*;
import static cn.hamm.airpower.open.OpenApiError.*;

/**
 * <h2>Open API 切面</h2>
 *
 * @author Hamm.cn
 */
@Slf4j
@Aspect
@Component
public class OpenApiAspect<A extends CurdEntity<A> & IOpenApp, S extends IOpenAppService<A>> {
    /**
     * 防重放时长
     */
    private static final int NONCE_SECOND = 300;

    /**
     * Redis 前缀
     */
    private static final String REDIS_PREFIX = "open:";

    /**
     * nonce 允许的最小长度
     */
    private static final int MIN_NONCE_LENGTH = 8;

    /**
     * nonce 允许的最大长度
     *
     * @apiNote 上界是<b>安全属性</b>，不是风格偏好：nonce 会被拼进 Redis 的键
     */
    private static final int MAX_NONCE_LENGTH = 64;

    @Autowired(required = false)
    private S openAppService;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private RedisHelper redisHelper;

    @SuppressWarnings("EmptyMethod")
    @Pointcut("@annotation(cn.hamm.airpower.open.OpenApi)")
    public void pointCut() {
    }

    /**
     * Open API 切面
     */
    @Around("pointCut()")
    public Object openApi(@NotNull ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        validOpenApi(proceedingJoinPoint);
        OpenRequest openRequest = getOpenRequest(proceedingJoinPoint);
        A openApp = getOpenApp(openRequest);
        openRequest.setOpenApp(openApp);

        // 校验白名单
        checkIpWhiteList(openApp, openRequest.getAppKey());

        // 应用公钥验签
        checkSignature(openApp.getPublicKey(), openRequest.getSignature(), openRequest.getPayload());

        // 获取有效负载
        OpenPayload openPayload = openRequest.getOpenPayload();

        // 校验时间戳
        checkTimestamp(openPayload.getTimestamp());

        // 校验随机串
        checkNonce(openRequest.getAppKey(), openPayload.getNonce());
        Object object = proceedingJoinPoint.proceed();
        if (object instanceof Json json) {
            // 如果是Json 需要将 Json.data 对输出的数据进行加密
            json.setData(OpenResponse.create(openRequest.getOpenApp(), json.getData()));
        }
        return object;
    }

    /**
     * 验证切面点是否支持 Open API
     *
     * @param proceedingJoinPoint {@code ProceedingJoinPoint}
     */
    private void validOpenApi(@NotNull ProceedingJoinPoint proceedingJoinPoint) {
        Signature signature = proceedingJoinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        OpenApi openApi = method.getAnnotation(OpenApi.class);
        OPEN_API_UNSUPPORTED.whenNull(openApi);
    }

    /**
     * 获取 Open API 请求参数
     *
     * @param proceedingJoinPoint {@code ProceedingJoinPoint}
     * @return {@code OpenRequest}
     */
    private @NotNull OpenRequest getOpenRequest(@NotNull ProceedingJoinPoint proceedingJoinPoint) {
        Object[] args = proceedingJoinPoint.getArgs();
        SERVICE_ERROR.when(args.length != 1, "OpenApi 必须接收一个参数");
        if (!(args[0] instanceof OpenRequest openRequest)) {
            throw new ServiceException("OpenApi 必须接收一个 OpenRequest参数");
        }
        return openRequest;
    }

    /**
     * 时间戳检测
     *
     * @param timestamp 时间戳
     */
    private void checkTimestamp(Long timestamp) {
        TIMESTAMP_REQUIRED.whenEmpty(timestamp);
        long currentTimeMillis = System.currentTimeMillis();
        int nonceExpireMillisecond = NONCE_SECOND * DateTimeUtil.MILLISECONDS_PER_SECOND;
        TIMESTAMP_INVALID.when(
                timestamp > currentTimeMillis + nonceExpireMillisecond ||
                        timestamp < currentTimeMillis - nonceExpireMillisecond
        );
    }

    /**
     * 获取请求的应用
     *
     * @param openRequest {@code OpenRequest}
     * @return {@code OpenApp}
     */
    private @NotNull A getOpenApp(@NotNull OpenRequest openRequest) {
        APP_KEY_REQUIRED.whenEmpty(openRequest.getAppKey());
        SERVICE_ERROR.whenNull(openAppService, "注入 OpenAppService 失败");
        A openApp = openAppService.getByAppKey(openRequest.getAppKey());
        APP_KEY_INVALID.whenNull(openApp);
        OPEN_APP_STATUS_ERROR.whenNull(openApp.getIsDisabled(), "应用状态异常");
        OPEN_APP_DISABLED.when(openApp.getIsDisabled());
        return openApp;
    }

    /**
     * 验证 IP 白名单
     *
     * @param openApp 开放应用
     * @param appKey  应用标识，仅用于日志定位
     * @apiNote 白名单未配置时放行：它是可选的第二道防线，不是必选项
     */
    void checkIpWhiteList(@NotNull A openApp, @NotNull String appKey) {
        String ipStr = Objects.requireNonNullElse(openApp.getIpWhiteList(), "");
        ipStr = ipStr
                // 避免输入换行、全角空格、半角逗号和分号
                .replace("\n", " ")
                .replace(";", " ")
                .replace(",", " ")
                .replace("　", " ");
        do {
            ipStr = ipStr.replace("  ", " ");
        } while (ipStr.contains("  "));
        if (!StringUtils.hasText(ipStr)) {
            log.warn("开放应用未配置 IP 白名单，所有来源 IP 均可调用。appKey={}", appKey);
            return;
        }
        final String ip = RequestUtil.getIpAddress(request);
        // 取不到来源地址时必须在这里拦下，否则会报成「不在白名单内」，把排查带偏
        if (!StringUtils.hasText(ip)) {
            throw new ServiceException(MISSING_REQUEST_ADDRESS);
        }
        String[] strings = ipStr.split(" ");
        log.info("IP 白名单检查，ip={}, appKey={}, 已配置={}", ip, appKey, strings);
        for (String s : strings) {
            if (ip.equals(s)) {
                return;
            }
        }
        // 来源 IP 与白名单内容只进服务端日志，不回显给调用方
        log.warn("IP 白名单拒绝，ip={}, appKey={}", ip, appKey);
        throw new ServiceException(INVALID_REQUEST_ADDRESS);
    }

    /**
     * 防重放检测
     *
     * @param appKey 应用标识
     * @param nonce  随机字符串
     */
    void checkNonce(String appKey, String nonce) {
        APP_KEY_REQUIRED.whenEmpty(appKey);
        NONCE_REQUIRED.whenEmpty(nonce);
        if (nonce.length() < MIN_NONCE_LENGTH || nonce.length() > MAX_NONCE_LENGTH) {
            PARAM_INVALID.show("nonce 长度必须在 " + MIN_NONCE_LENGTH + "~" + MAX_NONCE_LENGTH + " 之间");
        }
        String cacheKey = REDIS_PREFIX + appKey + ":nonce:" + DigestUtils.sha256Hex(nonce);
        if (!redisHelper.setIfAbsent(cacheKey, 1, NONCE_SECOND)) {
            REPEAT_REQUEST.show();
        }
    }

    /**
     * 验签
     *
     * @param publicKey 公钥
     * @param signature 签名
     * @param payload   被签名负载
     */
    void checkSignature(String publicKey, String signature, String payload) {
        OPEN_APP_PUBLIC_KEY_REQUIRED.whenEmpty(publicKey);
        SIGNATURE_REQUIRED.whenEmpty(signature);
        // 公钥验签
        boolean success = RsaUtil.create().setPublicKey(publicKey).publicKeyVerifySignature(payload, signature);
        SIGNATURE_INVALID.when(!success);
    }
}
