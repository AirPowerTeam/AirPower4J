package cn.hamm.airpower.access;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.datetime.DateTimeUtil;
import cn.hamm.airpower.exception.ServiceException;
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

import static cn.hamm.airpower.exception.ServiceError.PARAM_INVALID;
import static cn.hamm.airpower.exception.ServiceError.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <h1>AccessToken 工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class AccessTokenUtil {
    /**
     * 无效的令牌
     */
    private static final String ACCESS_TOKEN_INVALID = "身份令牌无效，请重新获取身份令牌";

    /**
     * 请先设置密钥环境变量
     */
    private static final String SET_ENV_TOKEN_SECRET_FIRST = "请在环境变量配置 airpower.accessTokenSecret";

    /**
     * 算法
     */
    private static final String HMAC_SHA_256 = "HmacSHA256";

    /**
     * Token 分隔符
     */
    private static final String TOKEN_DELIMITER = ".";

    /**
     * {@code HMAC-SHA-256}错误
     */
    private static final String HMAC_SHA_256_ERROR = "HMAC-SHA-256发生错误";

    /**
     * {@code Token} 由 {@code 3} 部分组成
     */
    private static final int TOKEN_PART_COUNT = 3;

    /**
     * 验证后的 Token
     */
    private VerifiedToken verifiedToken;

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private AccessTokenUtil() {
    }

    /**
     * 创建实例
     *
     * @return AccessTokenUtil 实例
     */
    public static @NotNull AccessTokenUtil create() {
        AccessTokenUtil accessTokenUtil = new AccessTokenUtil();
        accessTokenUtil.verifiedToken = new VerifiedToken();
        return accessTokenUtil;
    }

    /**
     * 创建一个 AccessToken
     *
     * @param id TokenID
     * @return AccessTokenUtil 实例
     * @apiNote 不设置令牌过期时间
     */
    public AccessTokenUtil setPayloadId(Long id) {
        return addPayload(CurdEntity.STRING_ID, id);
    }

    /**
     * 生成 {@code Token}
     *
     * @param secret 密钥
     * @return AccessToken
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
                TOKEN_DELIMITER +
                hmacSha256(secret, verifiedToken.getExpireTimestamps() + TOKEN_DELIMITER + payloadBase) +
                TOKEN_DELIMITER +
                payloadBase;
        return Base64.getUrlEncoder().encodeToString(content.getBytes(UTF_8));
    }

    /**
     * 添加负载
     *
     * @param key   负载的 Key
     * @param value 负载的 Value
     * @return AccessTokenUtil 实例
     */
    @Contract("_, _ -> this")
    public final AccessTokenUtil addPayload(String key, Object value) {
        verifiedToken.getPayloads().put(key, value);
        return this;
    }

    /**
     * 移除负载
     *
     * @param key 负载 Key
     * @return AccessTokenUtil 实例
     */
    @Contract("_ -> this")
    public final AccessTokenUtil removePayload(String key) {
        verifiedToken.getPayloads().remove(key);
        return this;
    }

    /**
     * 设置过期时间 {@code 毫秒}
     *
     * @param millisecond 过期毫秒
     * @return AccessTokenUtil 实例
     */
    @Contract("_ -> this")
    public final AccessTokenUtil setExpireMillisecond(long millisecond) {
        PARAM_INVALID.when(millisecond <= 0, "过期毫秒数必须大于0");
        verifiedToken.setExpireTimestamps(System.currentTimeMillis() + millisecond);
        return this;
    }

    /**
     * 设置过期时间 {@code 秒}
     *
     * @param second 秒数
     * @return AccessTokenUtil 实例
     */
    @Contract("_ -> this")
    public final AccessTokenUtil setExpireSecond(long second) {
        PARAM_INVALID.when(second <= 0, "过期秒数必须大于0");
        return setExpireMillisecond(second * DateTimeUtil.MILLISECONDS_PER_SECOND);
    }

    /**
     * 验证 AccessToken 并返回 VerifiedToken
     *
     * @param accessToken AccessToken
     * @param secret      密钥
     * @return VerifiedToken
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
        String[] list = source.split("\\" + TOKEN_DELIMITER);
        if (list.length != TOKEN_PART_COUNT) {
            throw new ServiceException(UNAUTHORIZED, ACCESS_TOKEN_INVALID);
        }
        //noinspection AlibabaUndefineMagicConstant
        if (!Objects.equals(hmacSha256(secret, list[0] + TOKEN_DELIMITER + list[2]), list[1])) {
            throw new ServiceException(UNAUTHORIZED, ACCESS_TOKEN_INVALID);
        }
        if (Long.parseLong(list[0]) < System.currentTimeMillis() && Long.parseLong(list[0]) != 0) {
            throw new ServiceException(UNAUTHORIZED, "身份令牌已过期，请重新获取身份令牌");
        }
        Map<String, Object> payloads = Json.parse2Map(new String(
                Base64.getUrlDecoder().decode(list[2].getBytes(UTF_8)))
        );
        return new VerifiedToken().setExpireTimestamps(Long.parseLong(list[0])).setPayloads(payloads);
    }

    /**
     * HMacSha256 签名
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
     * 已验证的身份令牌
     *
     * @author Hamm.cn
     */
    @Data
    @Accessors(chain = true)
    public static class VerifiedToken {
        /**
         * 负载数据
         */
        private Map<String, Object> payloads = new HashMap<>();

        /**
         * 过期时间 {@code 毫秒}
         */
        private long expireTimestamps = 0;

        /**
         * 获取负载
         *
         * @param key 负载的 Key
         * @return 负载的 Value
         */
        public final @Nullable Object getPayload(String key) {
            return payloads.get(key);
        }

        /**
         * 获取负载的 {@code ID}
         *
         * @return {@code ID}
         */
        public final long getPayloadId() {
            Object userId = getPayload(CurdEntity.STRING_ID);
            UNAUTHORIZED.whenNull(userId);
            return Long.parseLong(userId.toString());
        }
    }
}
