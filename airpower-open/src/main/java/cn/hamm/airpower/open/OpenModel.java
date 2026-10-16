package cn.hamm.airpower.open;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpenModel {
    /**
     * AppKey
     */
    private String appKey;

    /**
     * 请求负载
     */
    private String payload;

    /**
     * 签名字符串
     */
    private String signature;
}
