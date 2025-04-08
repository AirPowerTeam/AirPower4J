package cn.hamm.airpower.open;

import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.root.RootModel;
import cn.hamm.airpower.util.AesUtil;
import cn.hamm.airpower.util.DictionaryUtil;
import cn.hamm.airpower.util.RsaUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import static cn.hamm.airpower.exception.ServiceError.*;

/**
 * <h1>{@code OpenApi} 请求体</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Setter
public class OpenRequest {
    /**
     * <h3>{@code AppKey}</h3>
     */
    @NotBlank(message = "AppKey不能为空")
    @Getter
    private String appKey;

    /**
     * <h3>版本号</h3>
     */
    @NotNull(message = "版本号不能为空")
    @Getter
    private int version;

    /**
     * <h3>请求毫秒时间戳</h3>
     */
    @NotNull(message = "请求毫秒时间戳不能为空")
    @Getter
    private long timestamp;

    /**
     * <h3>加密后的业务数据</h3>
     */
    @NotBlank(message = "业务数据包体不能为空")
    private String content;

    /**
     * <h3>签名字符串</h3>
     */
    @NotBlank(message = "签名字符串不能为空")
    private String signature;

    /**
     * <h3>请求随机串</h3>
     */
    @NotBlank(message = "请求随机串不能为空")
    @Getter
    private String nonce;

    /**
     * <h3>当前请求的应用</h3>
     */
    @Getter
    private IOpenApp openApp;

    /**
     * <h3>强转请求数据到指定的类对象</h3>
     *
     * @param clazz 业务数据对象类型
     */
    public final <T extends RootModel<T>> T parse(Class<T> clazz) {
        String json = decodeContent();
        try {
            return Json.parse(json, clazz);
        } catch (Exception e) {
            JSON_DECODE_FAIL.show();
            throw new ServiceException(e);
        }
    }

    /**
     * <h3>解密请求数据</h3>
     *
     * @return 请求数据
     */
    final String decodeContent() {
        String request = content;
        OpenArithmeticType appArithmeticType = DictionaryUtil.getDictionary(
                OpenArithmeticType.class, openApp.getArithmetic()
        );
        try {
            switch (appArithmeticType) {
                case AES -> request = AesUtil.create().setKey(openApp.getAppSecret())
                        .decrypt(request);
                case RSA -> request = RsaUtil.create().setPrivateKey(openApp.getPrivateKey())
                        .privateKeyDecrypt(request);
                case NO -> {
                }
                default -> throw new ServiceException("解密失败，不支持的加密算法类型");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            DECRYPT_DATA_FAIL.show();
        }
        return request;
    }

    /**
     * <h3>签名验证</h3>
     *
     * @param openApp 应用对象
     */
    final void checkSignature(IOpenApp openApp) {
        this.openApp = openApp;
        SIGNATURE_INVALID.whenNotEquals(signature, sign());
    }

    /**
     * <h3>签名</h3>
     *
     * @return 签名后的字符串
     */
    private @org.jetbrains.annotations.NotNull String sign() {
        return DigestUtils.sha1Hex(openApp.getAppSecret() + appKey + version + timestamp + nonce + content);
    }
}
