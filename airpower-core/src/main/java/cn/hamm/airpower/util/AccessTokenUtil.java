package cn.hamm.airpower.util;

import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.root.RootEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static cn.hamm.airpower.config.Constant.REGEX_DOT;
import static cn.hamm.airpower.config.Constant.STRING_DOT;
import static cn.hamm.airpower.exception.ServiceError.PARAM_INVALID;
import static cn.hamm.airpower.exception.ServiceError.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <h1>{@code AccessToken} 工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class AccessTokenUtil {
    /**
     * <h3>无效的令牌</h3>
     */
    private static final String ACCESS_TOKEN_INVALID = "身份令牌无效，请重新获取身份令牌";

    /**
     * <h3>令牌已过期</h3>
     */
    private static final String ACCESS_TOKEN_EXPIRED = "身份令牌已过期，请重新获取身份令牌";

    /**
     * <h3>请先设置密钥环境变量</h3>
     */
    private static final String SET_ENV_TOKEN_SECRET_FIRST = "请在环境变量配置 airpower.accessTokenSecret";

    /**
     * <h3>算法</h3>
     */
    private static final String HMAC_SHA_256 = "HmacSHA256";

    /**
     * <h3>{@code HMAC-SHA-256}错误</h3>
     */
    private static final String HMAC_SHA_256_ERROR = "HMAC-SHA-256发生错误";

    /**
     * <h3>{@code Token} 由 {@code 3} 部分组成</h3>
     */
    private static final int TOKEN_PART_COUNT = 3;

    /**
     * <h3>验证后的 {@code Token}</h3>
     */
    private VerifiedToken verifiedToken;

    /**
     * <h3>禁止外部实例化</h3>
     */
    @Contract(pure = true)
    private AccessTokenUtil() {
    }

    /**
     * <h3>创建实例</h3>
     *
     * @return {@code AccessTokenUtil}
     */
    public static @NotNull AccessTokenUtil create() {
        AccessTokenUtil accessTokenUtil = new AccessTokenUtil();
        accessTokenUtil.verifiedToken = new VerifiedToken();
        return accessTokenUtil;
    }

    /**
     * <h3>创建一个 {@code AccessToken}</h3>
     *
     * @param id           {@code TokenID}
     * @param expireSecond 有效期（秒）
     * @return {@code AccessTokenUtil}
     */
    public AccessTokenUtil setPayloadId(Long id, long expireSecond) {
        return addPayload(RootEntity.STRING_ID, id)
                .setExpireMillisecond(expireSecond * DateTimeUtil.MILLISECONDS_PER_SECOND);
    }

    /**
     * <h3>创建一个 {@code AccessToken}</h3>
     *
     * @param id {@code TokenID}
     * @return {@code AccessTokenUtil}
     * @apiNote 不设置令牌过期时间
     */
    public AccessTokenUtil setPayloadId(Long id) {
        return addPayload(RootEntity.STRING_ID, id);
    }

    /**
     * <h3>生成 {@code Token}</h3>
     *
     * @param secret 密钥
     * @return {@code AccessToken}
     */
    public final String build(String secret) {
        PARAM_INVALID.whenEmpty(secret,
                "身份令牌创建失败，" + SET_ENV_TOKEN_SECRET_FIRST);
        if (verifiedToken.getPayloads().isEmpty()) {
            throw new ServiceException("没有任何负载数据");
        }
        String payloadBase = Base64.getUrlEncoder().encodeToString(
                Json.toString(verifiedToken.getPayloads()).getBytes(UTF_8)
        );
        String content = verifiedToken.getExpireTimestamps() +
                STRING_DOT +
                hmacSha256(secret, verifiedToken.getExpireTimestamps() + STRING_DOT + payloadBase) +
                STRING_DOT +
                payloadBase;
        return Base64.getUrlEncoder().encodeToString(content.getBytes(UTF_8));
    }

    /**
     * <h3>添加负载</h3>
     *
     * @param key   负载的 {@code Key}
     * @param value 负载的 {@code Value}
     * @return {@code AccessTokenUtil}
     */
    @Contract("_, _ -> this")
    public final AccessTokenUtil addPayload(String key, Object value) {
        verifiedToken.getPayloads().put(key, value);
        return this;
    }

    /**
     * <h3>移除负载</h3>
     *
     * @param key 负载 {@code Key}
     * @return {@code AccessTokenUtil}
     */
    @Contract("_ -> this")
    public final AccessTokenUtil removePayload(String key) {
        verifiedToken.getPayloads().remove(key);
        return this;
    }

    /**
     * <h3>设置过期时间 {@code 毫秒}</h3>
     *
     * @param millisecond 过期毫秒
     * @return {@code AccessTokenUtil}
     */
    @Contract("_ -> this")
    public final AccessTokenUtil setExpireMillisecond(long millisecond) {
        PARAM_INVALID.when(millisecond <= 0, "过期毫秒数必须大于0");
        verifiedToken.setExpireTimestamps(System.currentTimeMillis() + millisecond);
        return this;
    }

    /**
     * <h3>验证 {@code AccessToken} 并返回 {@code VerifiedToken}</h3>
     *
     * @param accessToken {@code AccessToken}
     * @param secret      密钥
     * @return {@code VerifiedToken}
     */
    public final VerifiedToken verify(@NotNull String accessToken, String secret) {
        PARAM_INVALID.whenEmpty(secret, SET_ENV_TOKEN_SECRET_FIRST);
        String source;
        try {
            source = new String(Base64.getUrlDecoder().decode(accessToken.getBytes(UTF_8)));
        } catch (Exception exception) {
            throw new ServiceException(UNAUTHORIZED, ACCESS_TOKEN_INVALID);
        }
        UNAUTHORIZED.when(!StringUtils.hasText(source), ACCESS_TOKEN_INVALID);
        String[] list = source.split(REGEX_DOT);
        if (list.length != TOKEN_PART_COUNT) {
            throw new ServiceException(UNAUTHORIZED, ACCESS_TOKEN_INVALID);
        }
        //noinspection AlibabaUndefineMagicConstant
        if (!Objects.equals(hmacSha256(secret, list[0] + STRING_DOT + list[2]), list[1])) {
            throw new ServiceException(UNAUTHORIZED, ACCESS_TOKEN_INVALID);
        }
        if (Long.parseLong(list[0]) < System.currentTimeMillis() && Long.parseLong(list[0]) != 0) {
            throw new ServiceException(UNAUTHORIZED, ACCESS_TOKEN_EXPIRED);
        }
        Map<String, Object> payloads = Json.parse2Map(new String(
                Base64.getUrlDecoder().decode(list[2].getBytes(UTF_8)))
        );
        return new VerifiedToken().setExpireTimestamps(Long.parseLong(list[0])).setPayloads(payloads);
    }

    /**
     * <h3>{@code HMacSha256}</h3>
     *
     * @param secret  密钥
     * @param content 数据
     * @return 签名
     */
    private @NotNull String hmacSha256(@NotNull String secret, @NotNull String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(UTF_8), HMAC_SHA_256);
            mac.init(secretKeySpec);
            StringBuilder hexString = new StringBuilder();
            for (byte b : mac.doFinal(content.getBytes(UTF_8))) {
                hexString.append(String.format("%02x", b & 0xff));
            }
            return hexString.toString();
        } catch (Exception exception) {
            log.error(HMAC_SHA_256_ERROR, exception);
            throw new ServiceException(HMAC_SHA_256_ERROR);
        }
    }

    /**
     * <h3>已验证的身份令牌</h3>
     *
     * @author Hamm.cn
     */
    @Data
    @Accessors(chain = true)
    public static class VerifiedToken {
        /**
         * <h3>负载数据</h3>
         */
        private Map<String, Object> payloads = new HashMap<>();

        /**
         * <h3>过期时间 {@code 毫秒}</h3>
         */
        private long expireTimestamps = 0;

        /**
         * <h3>获取负载</h3>
         *
         * @param key 负载的 {@code Key}
         * @return 负载的 {@code Value}
         */
        public final @Nullable Object getPayload(String key) {
            return payloads.get(key);
        }

        /**
         * <h3>获取负载的 {@code ID}</h3>
         *
         * @return {@code ID}
         */
        public final long getPayloadId() {
            Object userId = getPayload(RootEntity.STRING_ID);
            UNAUTHORIZED.whenNull(userId);
            return Long.parseLong(userId.toString());
        }
    }
}
