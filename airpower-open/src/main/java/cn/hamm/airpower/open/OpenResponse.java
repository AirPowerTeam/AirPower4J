package cn.hamm.airpower.open;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.crypto.RsaUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static cn.hamm.airpower.exception.Errors.ENCRYPT_DATA_FAIL;

/**
 * <h1>Open API 响应对象</h1>
 *
 * @author Hamm.cn
 */
public class OpenResponse {
    /**
     * 加密响应数据
     *
     * @param openApp 应用
     * @param data    数据
     * @return 加密后的数据
     */
    public static <A extends IOpenApp> @Nullable String encodeResponse(A openApp, Object data) {
        if (Objects.isNull(data)) {
            // 数据负载为空 直接返回
            return null;
        }
        String response = Json.toString(data);
        try {
            // 公钥加密
            response = RsaUtil.create().setPublicKey(openApp.getPublicKey()).publicKeyEncrypt(response);
        } catch (Exception e) {
            ENCRYPT_DATA_FAIL.show();
        }
        return response;
    }
}
