package cn.hamm.airpower.open;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.crypto.AesUtil;
import cn.hamm.airpower.crypto.RsaUtil;
import cn.hamm.airpower.dictionary.DictionaryUtil;
import cn.hamm.airpower.exception.ServiceException;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static cn.hamm.airpower.exception.ServiceError.ENCRYPT_DATA_FAIL;

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
        OpenArithmeticType appArithmeticType = DictionaryUtil.getDictionary(
                OpenArithmeticType.class, openApp.getArithmetic()
        );
        try {
            switch (appArithmeticType) {
                case AES -> response = AesUtil.create().setKey(openApp.getAppSecret())
                        .encrypt(response);
                case RSA -> response = RsaUtil.create().setPrivateKey(openApp.getPrivateKey())
                        .publicKeyEncrypt(response);
                case NO -> {
                }
                default -> throw new ServiceException(ENCRYPT_DATA_FAIL, "暂不支持的OpenApi加密算法");
            }
        } catch (Exception e) {
            ENCRYPT_DATA_FAIL.show();
        }
        return response;
    }
}
