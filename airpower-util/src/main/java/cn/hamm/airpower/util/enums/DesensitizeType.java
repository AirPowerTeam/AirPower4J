package cn.hamm.airpower.util.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>脱敏方式</h1>
 *
 * @author Hamm.cn
 */
@AllArgsConstructor
@Getter
public enum DesensitizeType {
    /**
     * 座机号码
     */
    TELEPHONE(0, 0),

    /**
     * 手机号码
     */
    MOBILE(3, 4),

    /**
     * 身份证号
     */
    ID_CARD(6, 4),

    /**
     * 银行卡号
     */
    BANK_CARD(4, 4),

    /**
     * 车牌号
     */
    CAR_NUMBER(2, 1),

    /**
     * 邮箱
     */
    EMAIL(2, 2),

    /**
     * 中文名
     */
    CHINESE_NAME(1, 1),

    /**
     * 地址
     */
    ADDRESS(3, 0),

    /**
     * IPv4 地址
     */
    IP_V4(0, 0),

    /**
     * 自定义
     */
    CUSTOM(0, 0);

    /**
     * 开始至少保留
     */
    private final int minHead;

    /**
     * 结束至少保留
     */
    private final int minTail;
}
