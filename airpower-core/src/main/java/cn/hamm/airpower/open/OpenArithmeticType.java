package cn.hamm.airpower.open;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.dictionary.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>开放应用加密方式</h1>
 *
 * @author Hamm
 */
@AllArgsConstructor
@Getter
@Description("开放应用加密方式")
public enum OpenArithmeticType implements IDictionary {
    /**
     * 不加密
     */
    NO(0, "NO"),

    /**
     * AES 算法
     */
    AES(1, "AES"),

    /**
     * RSA 算法
     */
    RSA(2, "RSA");

    private final int key;
    private final String label;
}
