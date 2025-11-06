package cn.hamm.airpower.open;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.crypto.AesUtil;
import cn.hamm.airpower.crypto.RsaUtil;
import cn.hamm.airpower.dictionary.DictionaryUtil;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.root.RootModel;
import cn.hamm.airpower.validate.ValidateUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import static cn.hamm.airpower.exception.ServiceError.*;

/**
 * <h1>Open API 请求体</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Setter
public class OpenRequest {
    /**
     * AppKey
     */
    @NotBlank(message = "AppKey不能为空")
    @Getter
    private String appKey;

    /**
     * 版本号
     */
    @NotNull(message = "版本号不能为空")
    @Getter
    private Integer version;

    /**
     * 请求毫秒时间戳
     */
    @NotNull(message = "请求毫秒时间戳不能为空")
    @Getter
    private Long timestamp;

    /**
     * 加密后的业务数据
     */
    @NotBlank(message = "业务数据包体不能为空")
    private String content;

    /**
     * 签名字符串
     */
    @NotBlank(message = "签名字符串不能为空")
    private String signature;

    /**
     * 请求随机串
     */
    @NotBlank(message = "请求随机串不能为空")
    @Getter
    private String nonce;

    /**
     * 当前请求的应用
     */
    @Getter
    private IOpenApp openApp;

    /**
     * 强转请求数据到指定的类对象
     *
     * @param clazz 业务数据对象类型
     */
    public final <T extends RootModel<T>> T parse(Class<T> clazz) {
        String json = decodeContent();
        T model;
        try {
            model = Json.parse(json, clazz);
        } catch (Exception e) {
            JSON_DECODE_FAIL.show();
            throw new ServiceException(e);
        }
        ValidateUtil.valid(model);
        return model;
    }

    /**
     * 解密请求数据
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
     * 签名验证
     *
     * @param openApp 应用对象
     */
    final void checkSignature(IOpenApp openApp) {
        this.openApp = openApp;
        log.info("签名传入 {}", signature);
        String sign = sign();
        log.info("签名计算 {}", sign);
        SIGNATURE_INVALID.whenNotEquals(signature, sign);
    }

    /**
     * 签名
     *
     * @return 签名后的字符串
     */
    private @org.jetbrains.annotations.NotNull String sign() {
        log.info("签名计算参数 {}", openApp.getAppSecret() + appKey + version + timestamp + nonce + content);
        return DigestUtils.sha1Hex(openApp.getAppSecret() + appKey + version + timestamp + nonce + content);
    }
}
