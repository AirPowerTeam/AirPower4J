package cn.hamm.airpower.open;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.common.RootModel;
import cn.hamm.airpower.core.crypto.RsaUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.validate.ValidateUtil;
import cn.hamm.airpower.exception.Errors;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.hamm.airpower.open.OpenApiError.*;

/**
 * <h2>Open API 请求体</h2>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class OpenRequest extends OpenModel {

    /**
     * 当前请求的应用
     */
    private IOpenApp openApp;

    /**
     * 获取请求负载对象
     *
     * @return 请求负载对象
     */
    public OpenPayload getOpenPayload() {
        String payload = getPayload();
        Boolean isEncrypted = openApp.getIsEncrypted();
        if (Objects.nonNull(isEncrypted) && isEncrypted) {
            payload = decodeContent(payload);
        }
        return Json.parse(payload, OpenPayload.class);
    }

    /**
     * 强转请求数据到指定的类对象
     *
     * @param clazz 业务数据对象类型
     */
    public final <T extends RootModel> T parse(Class<T> clazz) {
        T model;
        try {
            model = Json.parse(getOpenPayload().getContent(), clazz);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(JSON_DECODE_FAIL);
        }
        ValidateUtil.valid(model);
        return model;
    }

    /**
     * 解密请求负载
     *
     * @return 解密后的负载
     */
    private String decodeContent(String payload) {
        PAYLOAD_REQUIRED.whenEmpty(payload);
        try {
            // 平台私钥解密
            String privateKey = OpenConfig.PRIVATE_KEY;
            Errors.SERVICE_ERROR.whenEmpty(privateKey, "请配置平台私钥");
            payload = RsaUtil.create().setPrivateKey(privateKey)
                    .privateKeyDecrypt(payload);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            DECRYPT_DATA_FAIL.show();
        }
        return payload;
    }
}
