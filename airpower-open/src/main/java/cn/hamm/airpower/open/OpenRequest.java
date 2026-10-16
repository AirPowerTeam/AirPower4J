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

    public static void main(String[] args) {
        RsaUtil rsaUtil = RsaUtil.create().setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw58zeNJOE2fNUSQoiFga2DbJAH64VuMue0CBlQ5+T4HOXQ+8m4yJOCFsQ3Z+K/qat8htOv3JYHK38StTvLPnv2mGuRhNLAkOdWsM2U92fyxuCRGZURCLS7HtaVyig2HRsBb4VQcmH0LxW/VlJOVCjoWqR7V9eIZBfvVHioWV/dKphZlUdLQBpGwY+d4Qvazgkf/Gp2SpT0zUtS15mOw0rEDO2UiHe+ziGG2sW+AGwLDO/medtHBlhCL+9i/HL1mE0lxCg1u3zqG1GZW5/kEMLc9jd2DsxTUasCzii7o1fPo44LXzJu7PmbDDQLsknerVjjBqoPwvQcGLyEsYyqoHOwIDAQAB")
                .setPrivateKey("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCWE27HsBcXGYDbHUimSgkmmHSVgTpnLhn3+BWxmZqouS7OdIY5jZQCCh12t2vUuF5RsZLZFzC5iMJKkdRDdbilZYnbc6SmuP9TUmmXcmgKS9N8LV5MsqjCVOgCiV8WIQhPaUtFXSv9kaXGrD72q5CuT7Q4BShUnLb3+mK+vpAtoB8dYWCdcvlJnGybA0uKggL2LTDTzK9MB5u1cxRsgINo/MM5pi9hzDVxvoGTSVFCU2uk3CghJ3SClTyOi0WiESalC/aKwkqcATjRUe/HBK/ii6cde+rRPLuBu/GmHfR4HwDPe99dhSj0Nk1giqCbI8qrb3p62yuxw7+dIHB9TFivAgMBAAECggEABJkK0KZbRr4QHbvAxotydVLAEPaDwLZ/stelvn+wu9Dp7LGoN7+CfyoqgJZvq3WMj4nWWxKj+liExKkWwjcAlbH4g8HVaQrg6P9s3xLGZOOxfSvx/X05wLO+Oe8Ae+/ozm9bkCqhwY6CmtrFwQ6SNleYc2DrvTqMkSiuzsji0kBPa5qFxqG9AfIonaxkwjOP5Foub6/U2tkHWWxivoClImQC2ZWK6dtU59kCGnHab35GZAfePxI97A4RlVBaqLmzXlAdkwQY/6cNg7TwGc/yGfsV70k9+YZ4B0I0zSiJWIcuo6lXeiWDnnb1nq3jP2ppguQX2zt3XmBGDhHTrkX92QKBgQDCCem2qc/rTRd0LTFbDsE/DTamyq8BBpgIIN6EK4d1dAaRDZc4Ycj2slp8ICd/pj/V9/CqFrKQqgySmuvb8SJl1lAW6TVY9LL1UgH4ssoyaInVOi3LjL1BWQXZpPUS4F6fYcj0a8V3yqHa4bSeu/e56v/m4/vjxg2gzrnnV5f36QKBgQDF/7Dc/vuQlllWCS2p23GeM7YoN4jW9q0yqi7W0YJKnxJM6fEb1tzKnTtFFBtwEeyKsVQB7T08oifv4SSiGZt2SB3UZOxgVa1xXUwJg3IR9d8Qwxa2C6PO2qwZO6kgBHEz3fHCBKQpGcfyXGN6gy5pidotkY+faAGheMyGir+E1wKBgBUuY9bOvqWe6rqB3GtmNu4dESQv596iy6HHhMLExaqqmbTn2OQMuJVFbt8x213x1fITeK/4flGQXoL6ali9/9VCCcgWeP5W59ojs5dkXPIXu0QNb8ZSfK5Pe3YArcf7uhiH4hncUdf7xKNSFBdnyWkAICMiK/L3QbGWH3EDfNFRAoGADsFjWNTXwoqx1auRb5se+H86D/0zgXGYrrHoE7JBV3ZLUtgMU9rLUX4uMJOzB8MOMbfNXudjLOWYvFO2+y2VXFc7gdyTkQpRhqVBSWQ2WAhOu10OkDqF1qCNOsQrcmtiVE8x+ReHdFirgfBIqZSlHNBdJhA299Y0aOgrtcsZQrkCgYBUvGHX/BXSQlHOPlMZyZxdBJVEtzmHwhxCefLoXjkknEkG/fcMqtzn/EFPhMKkZFvyJPE9E+/PBibkDbw7RqsuXIEJPAfvW+2PeBH4RQ4ULScZIL4yKR5pBZqSq50GPPBJH/9cEi2a0lb7DeSShSDcG16sR7uZ+R2rtq2l61VHfA==");

        String s = Json.toString(Map.of(
                "timestamp", System.currentTimeMillis(),
                "nonce", UUID.randomUUID().toString(),
                "content", Json.toString(Map.of(
                        "name", "Hamm", "age", 18
                ))
        ));
//        s = rsaUtil.publicKeyEncrypt(s);
        String sign = rsaUtil.privateKeySignature(s);
        System.out.println(Json.toString(Map.of(
                "appKey", "sn5qohPeKWzqOO5EfhIw9Xb3nk4CIKx6",
                "signature", sign,
                "payload", s
        )));
    }
}
