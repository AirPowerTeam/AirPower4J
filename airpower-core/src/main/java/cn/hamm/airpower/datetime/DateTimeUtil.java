package cn.hamm.airpower.datetime;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static cn.hamm.airpower.datetime.DateTimeFormatter.FULL_DATETIME;

/**
 * <h1>时间日期格式化工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class DateTimeUtil {
    /**
     * 一天 {@code 24} 小时
     */
    public static final int HOUR_PER_DAY = 24;

    /**
     * 毫秒转秒
     */
    public static final int MILLISECONDS_PER_SECOND = 1000;

    /**
     * 一年 {@code 365} 天
     */
    public static final int DAY_PER_YEAR = 365;

    /**
     * 一个月 {@code 30} 天
     */
    public static final int DAY_PER_MONTH = 30;

    /**
     * 一周 {@code 7} 天
     */
    public static final int DAY_PER_WEEK = 7;

    /**
     * 一分钟 {@code 60} 秒
     */
    public static final int SECOND_PER_MINUTE = 60;

    /**
     * 一小时的秒数
     */
    public static final int SECOND_PER_HOUR = SECOND_PER_MINUTE * SECOND_PER_MINUTE;

    /**
     * 一天的秒数
     */
    public static final int SECOND_PER_DAY = SECOND_PER_HOUR * HOUR_PER_DAY;

    /**
     * 时间步长
     */
    private static final long[] STEP_SECONDS = {
            0,
            SECOND_PER_MINUTE,
            SECOND_PER_HOUR,
            SECOND_PER_DAY,
            SECOND_PER_DAY * DAY_PER_WEEK,
            SECOND_PER_DAY * DAY_PER_MONTH,
            SECOND_PER_DAY * DAY_PER_YEAR
    };

    /**
     * 默认时区
     */
    private static final String ASIA_CHONGQING = "Asia/Chongqing";

    /**
     * 时间步长标签
     */
    private static final String[] STEP_LABELS = {
            "秒",
            "分钟",
            "小时",
            "天",
            "周",
            "月",
            "年"
    };

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private DateTimeUtil() {
    }

    /**
     * 格式化时间
     *
     * @param milliSecond 毫秒
     * @return 格式化后的时间
     */
    public static @NotNull String format(long milliSecond) {
        return format(milliSecond, FULL_DATETIME.getValue());
    }

    /**
     * 格式化时间
     *
     * @param milliSecond 毫秒
     * @param formatter   格式化模板
     * @return 格式化后的时间
     */
    public static @NotNull String format(long milliSecond, @NotNull DateTimeFormatter formatter) {
        return format(milliSecond, formatter.getValue());
    }

    /**
     * 格式化时间
     *
     * @param milliSecond 毫秒
     * @param formatter   格式化模板
     * @return 格式化后的时间
     */
    public static @NotNull String format(long milliSecond, String formatter) {
        return format(milliSecond, formatter, ASIA_CHONGQING);
    }

    /**
     * 格式化时间
     *
     * @param milliSecond 毫秒
     * @param formatter   格式化模板
     * @param zone        时区
     * @return 格式化后的时间
     */
    public static @NotNull String format(long milliSecond, String formatter, String zone) {
        Instant instant = Instant.ofEpochMilli(milliSecond);
        ZonedDateTime beijingTime = instant.atZone(ZoneId.of(zone));
        return beijingTime.format(java.time.format.DateTimeFormatter.ofPattern(formatter));
    }

    /**
     * 友好格式化时间
     *
     * @param milliSecond 毫秒时间戳
     * @return 友好格式化后的时间
     */
    public static @NotNull String friendlyFormatMillisecond(long milliSecond) {
        long second = milliSecond / MILLISECONDS_PER_SECOND;
        return friendlyFormatSecond(second);
    }

    /**
     * 友好格式化时间
     *
     * @param second {@code Unix}秒时间戳
     * @return 友好格式化后的时间
     */
    public static @NotNull String friendlyFormatSecond(long second) {
        long currentSecond = System.currentTimeMillis() / MILLISECONDS_PER_SECOND;
        long diff = Math.abs(currentSecond - second);
        if (second < 0) {
            log.error("时间戳错误：{}", second);
            return "-";
        }
        if (second < currentSecond && diff < SECOND_PER_MINUTE) {
            // 过去时间，且小于60s
            return "刚刚";
        }
        String suffix = second > currentSecond ? "后" : "前";
        for (int i = STEP_SECONDS.length - 1; i >= 0; i--) {
            long step = STEP_SECONDS[i];
            if (diff >= step) {
                if (step == 0) {
                    return String.format("%d%s%s", diff, STEP_LABELS[i], suffix);
                }
                return String.format("%d%s%s", diff / step, STEP_LABELS[i], suffix);
            }
        }
        return "未知时间";
    }
}
