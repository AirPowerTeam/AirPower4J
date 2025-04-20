package cn.hamm.airpower.desensitize;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.util.stream.IntStream;

/**
 * <h1>字符串脱敏处理工具类</h1>
 *
 * @author Hamm.cn
 */
public class DesensitizeUtil {
    /**
     * IPv4 的块长度
     */
    private static final int IPV4_PART_COUNT = 4;

    /**
     * 默认符号
     */
    private static final String DEFAULT_SYMBOL = "*";

    /**
     * IPv4 地址分隔符
     */
    private static final String IP_DELIMITER = ".";

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private DesensitizeUtil() {
    }

    /**
     * 字符串替换
     *
     * @param text   原始字符串
     * @param head   头部保留长度
     * @param tail   尾部保留长度
     * @param symbol 中间替换的单个符号
     * @return 替换后的字符串
     */
    public static @NotNull String replace(String text, int head, int tail, String symbol) {
        if (head < 0 || tail < 0 || head + tail >= text.length()) {
            return text;
        }
        StringBuilder stringBuilder = new StringBuilder();
        IntStream.range(0, text.length()).forEach(i -> {
            if (i >= head && i <= text.length() - tail - 1) {
                stringBuilder.append(symbol);
            } else {
                stringBuilder.append(text.charAt(i));
            }
        });
        return stringBuilder.toString();
    }

    /**
     * IPv4 地址脱敏
     *
     * @param ipv4   IPv4 地址
     * @param symbol 符号
     * @return 脱敏后的 IPv4 地址
     */
    public static @NotNull String desensitizeIpv4Address(@NotNull String ipv4, String symbol) {
        if (!StringUtils.hasText(symbol)) {
            symbol = DEFAULT_SYMBOL;
        }
        String[] strings = ipv4.split("\\" + IP_DELIMITER);
        if (strings.length != IPV4_PART_COUNT) {
            return ipv4;
        }
        strings[1] = symbol + symbol + symbol;
        strings[2] = strings[1];
        return String.join(IP_DELIMITER, strings);
    }

    /**
     * IPv4 地址脱敏
     *
     * @param ipv4 IPv4 地址
     * @return 脱敏后的 IPv4 地址
     */
    public static @NotNull String desensitizeIpv4Address(@NotNull String ipv4) {
        return desensitizeIpv4Address(ipv4, DEFAULT_SYMBOL);
    }

    /**
     * 文本脱敏
     *
     * @param text 原始文本
     * @param type 脱敏类型
     * @param head 头部保留
     * @param tail 尾部保留
     * @return 脱敏后的文本
     */
    @Contract(pure = true)
    public static @NotNull String desensitize(@NotNull String text, DesensitizeType type, int head, int tail) {
        return desensitize(text, type, head, tail, DEFAULT_SYMBOL);
    }

    /**
     * 文本脱敏
     *
     * @param valueString 原始文本
     * @param type        脱敏类型
     * @param head        头部保留
     * @param tail        尾部保留
     * @param symbol      脱敏符号
     * @return 脱敏后的文本
     */
    @Contract(pure = true)
    public static @NotNull String desensitize(
            @NotNull String valueString, @NotNull DesensitizeType type, int head, int tail, String symbol
    ) {
        switch (type) {
            case BANK_CARD,
                 ID_CARD,
                 MOBILE,
                 ADDRESS,
                 CAR_NUMBER,
                 EMAIL -> {
                head = Math.max(type.getMinHead(), head);
                tail = Math.max(type.getMinTail(), tail);
            }
            case IP_V4 -> {
                return desensitizeIpv4Address(valueString, symbol);
            }
            case CHINESE_NAME -> {
                head = Math.max(type.getMinHead(), head);
                tail = Math.max(type.getMinTail(), tail);
                if (valueString.length() <= head + tail) {
                    tail = 0;
                }
            }
            case TELEPHONE -> {
                // 包含区号 前后各留4 不包含则各留2
                int isContainRegionCode = valueString.length() > 8 ? 4 : 2;
                head = Math.max(isContainRegionCode, head);
                tail = Math.max(isContainRegionCode, tail);
            }
            default -> {
            }
        }
        return replace(valueString, head, tail, symbol);
    }
}
