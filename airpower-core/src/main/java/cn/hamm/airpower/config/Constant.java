package cn.hamm.airpower.config;

import org.jetbrains.annotations.Contract;

/**
 * <h1>内置常量</h1>
 *
 * @author Hamm.cn
 */
public class Constant {
    /**
     * <h3>{@code AirPower}</h3>
     */
    public static final String AIRPOWER = "airpower";

    /**
     * <h3>空字符串 {@code }</h3>
     */
    public static final String STRING_EMPTY = "";

    /**
     * <h3>下划线 {@code _}</h3>
     */
    public final static String STRING_UNDERLINE = "_";

    /**
     * <h3>半角空格 {@code  }</h3>
     */
    public final static String STRING_BLANK = " ";

    /**
     * <h3>半角逗号 {@code ,}</h3>
     */
    public final static String STRING_COMMA = ",";

    /**
     * <h3>半角冒号 {@code :}</h3>
     */
    public final static String STRING_COLON = ":";

    /**
     * <h3>分号 {@code ;}</h3>
     */
    public final static String STRING_SEMICOLON = ";";

    /**
     * <h3>斜线 {@code /}</h3>
     */
    public final static String STRING_SLASH = "/";

    /**
     * <h3>横线 {@code -}</h3>
     */
    public final static String STRING_LINE = "-";

    /**
     * <h3>等号 {@code =}</h3>
     */
    public final static String STRING_EQUAL = "=";

    /**
     * <h3>点 {@code .}</h3>
     */
    public final static String STRING_DOT = ".";

    /**
     * <h3>点的正则</h3>
     */
    public static final String REGEX_DOT = "\\.";

    /**
     * <h3>星号 {@code *}</h3>
     */
    public final static String STRING_ASTERISK = "*";

    /**
     * <h3>@ {@code @}</h3>
     */
    public final static String STRING_AT = "@";

    /**
     * <h3>{@code And &}</h3>
     */
    public static final String STRING_AND = "&";

    /**
     * <h3>百分号 {@code %}</h3>
     */
    public static final String STRING_PERCENT = "%";

    /**
     * <h3>{@code get}</h3>
     */
    public static final String STRING_GET = "get";

    /**
     * <h3>{@code Key}</h3>
     */
    public static final String STRING_KEY = "key";

    /**
     * <h3>{@code Label}</h3>
     */
    public static final String STRING_LABEL = "label";

    /**
     * <h3>{@code Error}</h3>
     */
    public static final String STRING_ERROR = "error";

    /**
     * <h3>{@code Code}</h3>
     */
    public static final String STRING_CODE = "code";

    /**
     * <h3>{@code Success}</h3>
     */
    public static final String STRING_SUCCESS = "success";

    /**
     * <h3>{@code Value}</h3>
     */
    public static final String STRING_VALUE = "value";

    /**
     * <h3>String {@code "0"}</h3>
     */
    public static final String STRING_ZERO = "0";

    /**
     * <h3>String {@code "1"}</h3>
     */
    public static final String STRING_ONE = "1";

    /**
     * <h3>String {@code "true"}</h3>
     */
    public static final String STRING_TRUE = "true";

    /**
     * <h3>String {@code "false"}</h3>
     */
    public static final String STRING_FALSE = "false";

    /**
     * <h3>换行</h3>
     */
    public static final String REGEX_LINE_BREAK = "\n";

    /**
     * <h3>{@code TAB}</h3>
     */
    public static final String REGEX_TAB = "\t";

    /**
     * <h3>{@code "是"}</h3>
     */
    public static final String STRING_YES = "是";

    /**
     * <h3>{@code "否"}</h3>
     */
    public static final String STRING_NO = "否";

    /**
     * <h3>{@code unknown}</h3>
     */
    public static final String STRING_UNKNOWN = "unknown";

    /**
     * <h3>缓存的 {@code REQUEST_ID}</h3>
     */
    public static final String REQUEST_ID = "REQUEST_ID";

    /**
     * <h3>禁止外部实例化</h3>
     */
    @Contract(pure = true)
    private Constant() {
    }
}
