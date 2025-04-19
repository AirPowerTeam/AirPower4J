package cn.hamm.airpower.enums;

import cn.hamm.airpower.util.DateTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>格式化模板</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum DateTimeFormatter {
    /**
     * 年
     */
    YEAR("yyyy"),

    /**
     * 月
     */
    MONTH("MM"),

    /**
     * 日
     */
    DAY("dd"),

    /**
     * 时
     */
    HOUR("HH"),

    /**
     * 分
     */
    MINUTE("mm"),

    /**
     * 秒
     */
    SECOND("ss"),

    /**
     * 年月日
     */
    FULL_DATE("yyyy-MM-dd"),

    /**
     * 时分秒
     */
    FULL_TIME("HH:mm:ss"),

    /**
     * 年月日时分秒
     */
    FULL_DATETIME("yyyy-MM-dd HH:mm:ss"),

    /**
     * 月日时分
     */
    SHORT_DATETIME("MM-dd HH:mm"),
    ;

    private final String value;

    /**
     * 使用这个模板格式化毫秒时间戳
     *
     * @param milliSecond 毫秒时间戳
     * @return 格式化后的字符串
     */
    public final @NotNull String format(long milliSecond) {
        return DateTimeUtil.format(milliSecond, this);
    }

    /**
     * 使用这个模板格式化当前时间
     *
     * @return 格式化后的字符串
     */
    public final @NotNull String formatCurrent() {
        return format(System.currentTimeMillis());
    }
}
