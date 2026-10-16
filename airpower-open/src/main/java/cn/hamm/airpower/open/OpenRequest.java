package cn.hamm.airpower.open;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.common.RootModel;
import cn.hamm.airpower.core.crypto.RsaUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.validate.ValidateUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static cn.hamm.airpower.exception.Errors.*;

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
    @NotBlank(message = "AppKey 不能为空")
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
    public final <T extends RootModel> T parse(Class<T> clazz) {
        String json = decodeContent();
        T model;
        try {
            model = Json.parse(json, clazz);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(JSON_DECODE_FAIL);
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
        try {
            // 私钥解密
            request = RsaUtil.create().setPrivateKey(OpenConfig.PRIVATE_KEY)
                    .privateKeyDecrypt(request);
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
    final void checkSignature(@org.jetbrains.annotations.NotNull IOpenApp openApp) {
        this.openApp = openApp;
        String publicKey = openApp.getPublicKey();
        // 公钥验签
        boolean success = RsaUtil.create().setPublicKey(publicKey).publicKeyVerifySignature(content, signature);
        SIGNATURE_INVALID.when(!success);
    }
}
