package cn.hamm.airpower.open;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpenPayload {
    /**
     * 请求毫秒时间戳
     */
    private Long timestamp;

    /**
     * 请求随机串
     */
    private String nonce;

    /**
     * 请求业务内容
     */
    private String content;
}
