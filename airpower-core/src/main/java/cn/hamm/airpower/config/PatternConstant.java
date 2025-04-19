package cn.hamm.airpower.config;

import org.jetbrains.annotations.Contract;

import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

/**
 * <h1>正则常量</h1>
 *
 * @author Hamm.cn
 */
public class PatternConstant {
    /**
     * 数字
     */
    public static final Pattern NUMBER = compile("^(-?\\d+)(\\.\\d+)?$");

    /**
     * 字母
     */
    public static final Pattern LETTER = compile("^[A-Za-z]+$");

    /**
     * 整数
     */
    public static final Pattern INTEGER = compile("^-?[0-9]\\d*$");

    /**
     * 邮箱
     */
    public static final Pattern EMAIL = compile(
            "^[a-zA-Z0-9]+(\\.([a-zA-Z0-9]+))*@[a-zA-Z0-9]+(\\.([a-zA-Z0-9]+))+$"
    );

    /**
     * 字母或数字
     */
    public static final Pattern LETTER_OR_NUMBER = compile("^[A-Za-z0-9]+$");

    /**
     * 中文
     */
    public static final Pattern CHINESE = compile("^[\\u4e00-\\u9fa5]*$");

    /**
     * 手机
     */
    public static final Pattern MOBILE_PHONE = compile("^(\\+(\\d{1,4}))?1[3-9](\\d{9})$");

    /**
     * 座机电话
     */
    public static final Pattern TEL_PHONE = compile(
            "^(((0\\d{2,3})-)?((\\d{7,8})|(400\\d{7})|(800\\d{7}))(-(\\d{1,4}))?)$"
    );

    /**
     * 普通字符
     */
    public static final Pattern NORMAL_CODE = compile("^[@#%a-zA-Z0-9\\u4e00-\\u9fa5_\\-\\\\/+]+$");

    /**
     * 数字或字母
     */
    public static final Pattern NUMBER_OR_LETTER = compile("^[0-9a-zA-Z]+$");

    /**
     * 自然数
     */
    public static final Pattern NATURAL_NUMBER = compile("^[0-9]+((.)[0-9]+)?$");

    /**
     * 自然整数
     */
    public static final Pattern NATURAL_INTEGER = compile("^[0-9]+$");

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private PatternConstant() {
    }
}
