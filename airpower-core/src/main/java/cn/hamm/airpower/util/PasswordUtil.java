package cn.hamm.airpower.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static cn.hamm.airpower.exception.ServiceError.PARAM_MISSING;

/**
 * <h1>密码工具类</h1>
 *
 * @author Hamm.cn
 */
public class PasswordUtil {
    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private PasswordUtil() {
    }

    /**
     * 密码和盐获取密码的散列摘要
     *
     * @param password 明文密码
     * @param salt     盐
     * @return {@code sha1} 散列摘要
     */
    public static @NotNull String encode(@NotNull String password, @NotNull String salt) {
        PARAM_MISSING.whenEmpty(password, "密码不能为空");
        PARAM_MISSING.whenEmpty(salt, "盐不能为空");
        return DigestUtils.sha1Hex(
                DigestUtils.sha1Hex(password + salt) + DigestUtils.sha1Hex(salt + password)
        );
    }
}
