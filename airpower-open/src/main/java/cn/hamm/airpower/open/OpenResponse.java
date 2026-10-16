package cn.hamm.airpower.open;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.crypto.RsaUtil;
import cn.hamm.airpower.exception.Errors;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

import static cn.hamm.airpower.open.OpenApiError.ENCRYPT_DATA_FAIL;
import static cn.hamm.airpower.open.OpenApiError.OPEN_APP_PUBLIC_KEY_REQUIRED;

/**
 * <h2>Open API 响应对象</h2>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class OpenResponse {
    /**
     * 加密响应数据
     *
     * @param openApp 应用
     * @param data    数据
     * @return 加密后的数据
     */
    public static <A extends IOpenApp> @Nullable OpenModel create(A openApp, Object data) {
        if (Objects.isNull(data)) {
            // 数据负载为空 直接返回
            return null;
        }
        String response = Json.toString(data);
        OpenPayload openPayload = new OpenPayload()
                .setContent(response)
                .setNonce(UUID.randomUUID().toString())
                .setTimestamp(System.currentTimeMillis());
        String payload = Json.toString(openPayload);
        Boolean isEncrypted = openApp.getIsEncrypted();
        if (Objects.nonNull(isEncrypted) && isEncrypted) {
            try {
                // 应用公钥加密
                String publicKey = openApp.getPublicKey();
                OPEN_APP_PUBLIC_KEY_REQUIRED.whenEmpty(publicKey);
                payload = RsaUtil.create().setPublicKey(publicKey).publicKeyEncrypt(payload);
            } catch (Exception e) {
                ENCRYPT_DATA_FAIL.show();
            }
        }

        // 平台私钥签名
        String privateKey = OpenConfig.PRIVATE_KEY;
        Errors.SERVICE_ERROR.whenEmpty(privateKey, "请配置平台私钥");
        String signature = RsaUtil.create().setPrivateKey(privateKey).privateKeySignature(payload);
        return new OpenModel()
                .setAppKey(openApp.getAppKey())
                .setPayload(payload)
                .setSignature(signature);
    }
}
