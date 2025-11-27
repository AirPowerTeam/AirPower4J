package cn.hamm.airpower.datetime;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

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
     * 格式化当前时间
     *
     * @return 格式化后的时间
     */
    public static @NotNull String formatCurrent() {
        return formatCurrent(FULL_DATETIME);
    }

    /**
     * 格式化当前时间
     *
     * @return 格式化后的时间
     */
    public static @NotNull String formatCurrent(@NotNull DateTimeFormatter formatter) {
        return formatCurrent(formatter.getValue());
    }

    /**
     * 格式化当前时间
     *
     * @param formatter 时间格式
     * @return 格式化后的时间
     */
    public static @NotNull String formatCurrent(String formatter) {
        return format(System.currentTimeMillis(), formatter);
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
     * 时间戳转换成日期格式
     *
     * @param milliSecond 毫秒时间戳
     * @return 时间戳对应的日期
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Date parse(long milliSecond) {
        return new Date(milliSecond);
    }

    /**
     * 字符串转换成日期格式
     *
     * @param dateTime 字符串
     * @return 时间戳对应的日期
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Date parse(String dateTime) throws ParseException {
        return parse(dateTime, FULL_DATETIME.getValue());
    }

    /**
     * 字符串转换成日期格式
     *
     * @param dateTime  字符串
     * @param formatter 时间格式
     * @return 时间戳对应的日期
     */
    public static @NotNull Date parse(String dateTime, String formatter) throws ParseException {
        java.time.format.DateTimeFormatter dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern(formatter);
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime, dateTimeFormatter);
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
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

    /**
     * 获取当前年份
     *
     * @return 年份
     */
    public static int getCurrentYear() {
        return getYear(new Date());
    }

    /**
     * 获取年份
     *
     * @param date 时间
     * @return 年份
     */
    public static int getYear(@NotNull Date date) {
        return getLocalDateTime(date.getTime()).getYear();
    }

    /**
     * 获取当前月份
     *
     * @return 月份
     */
    public static int getCurrentMonth() {
        return getMonth(new Date());
    }

    /**
     * 获取月份
     *
     * @param date 时间
     * @return 月份
     */
    public static int getMonth(@NotNull Date date) {
        return getLocalDateTime(date.getTime()).getMonthValue();
    }

    /**
     * 获取当前时间
     *
     * @param millisecond 毫秒时间戳
     * @return 时间
     */
    @Contract("_ -> new")
    public static @NotNull LocalDateTime getLocalDateTime(long millisecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millisecond), ZoneId.systemDefault());
    }

    /**
     * 获取当前日期
     *
     * @return 日期
     */
    public static int getCurrentDay() {
        return ZonedDateTime.now().getDayOfMonth();
    }

    /**
     * 获取日期第几天
     *
     * @param date 时间
     * @return 日期
     */
    public static int getDay(@NotNull Date date) {
        return getLocalDateTime(date.getTime()).getDayOfMonth();
    }

    /**
     * 获取当前小时
     *
     * @return 小时
     */
    public static int getCurrentHour() {
        return getHour(new Date());
    }

    /**
     * 获取小时
     *
     * @param date 时间
     * @return 小时
     */
    public static int getHour(@NotNull Date date) {
        return getLocalDateTime(date.getTime()).getHour();
    }

    /**
     * 获取当前分钟
     *
     * @return 分钟
     */
    public static int getCurrentMinute() {
        return getMinute(new Date());
    }

    /**
     * 获取分钟
     *
     * @param date 时间
     * @return 分钟
     */
    public static int getMinute(@NotNull Date date) {
        return getLocalDateTime(date.getTime()).getMinute();
    }

    /**
     * 获取当前秒
     *
     * @return 秒
     */
    public static int getCurrentSecond() {
        return getSecond(new Date());
    }

    /**
     * 获取秒
     *
     * @param date 时间
     * @return 秒
     */
    public static int getSecond(@NotNull Date date) {
        return getLocalDateTime(date.getTime()).getSecond();
    }

    /**
     * 添加时间
     *
     * @param date          日期
     * @param calendarField 日历属性
     * @param amount        数量
     * @return 新日期
     */
    private static @NotNull Date add(Date date, int calendarField, int amount) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        //noinspection MagicConstant
        c.add(calendarField, amount);
        return c.getTime();
    }

    /**
     * 添加天
     *
     * @param date   日期
     * @param amount 天数
     * @return 新日期
     */
    public static @NotNull Date addDays(Date date, int amount) {
        return add(date, Calendar.DATE, amount);
    }

    /**
     * 添加天
     *
     * @param amount 天数
     * @return 新日期
     */
    public static @NotNull Date addDays(int amount) {
        return addDays(new Date(), amount);
    }

    /**
     * 添加小时
     *
     * @param date   日期
     * @param amount 小时数
     * @return 新日期
     */
    public static @NotNull Date addHours(Date date, int amount) {
        return add(date, Calendar.HOUR_OF_DAY, amount);
    }

    /**
     * 添加小时
     *
     * @param amount 小时数
     * @return 新日期
     */
    public static @NotNull Date addHours(int amount) {
        return addHours(new Date(), amount);
    }

    /**
     * 添加毫秒
     *
     * @param date   日期
     * @param amount 毫秒数
     * @return 新日期
     */
    public static @NotNull Date addMilliseconds(Date date, int amount) {
        return add(date, Calendar.MILLISECOND, amount);
    }

    /**
     * 添加毫秒
     *
     * @param amount 毫秒数
     * @return 新日期
     */
    public static @NotNull Date addMilliseconds(int amount) {
        return addMilliseconds(new Date(), amount);
    }

    /**
     * 添加分钟
     *
     * @param date   日期
     * @param amount 分钟数
     * @return 新日期
     */
    public static @NotNull Date addMinutes(Date date, int amount) {
        return add(date, Calendar.MINUTE, amount);
    }

    /**
     * 添加分钟
     *
     * @param amount 分钟数
     * @return 新日期
     */
    public static @NotNull Date addMinutes(int amount) {
        return addMinutes(new Date(), amount);
    }

    /**
     * 添加月
     *
     * @param date   日期
     * @param amount 月数
     * @return 新日期
     */
    public static @NotNull Date addMonths(Date date, int amount) {
        return add(date, Calendar.MONTH, amount);
    }

    /**
     * 添加月
     *
     * @param amount 月数
     * @return 新日期
     */
    public static @NotNull Date addMonths(int amount) {
        return addMonths(new Date(), amount);
    }

    /**
     * 添加秒
     *
     * @param date   日期
     * @param amount 秒数
     * @return 新日期
     */
    public static @NotNull Date addSeconds(Date date, int amount) {
        return add(date, Calendar.SECOND, amount);
    }

    /**
     * 添加秒
     *
     * @param amount 秒数
     * @return 新日期
     */
    public static @NotNull Date addSeconds(int amount) {
        return addSeconds(new Date(), amount);
    }

    /**
     * 添加周
     *
     * @param date   日期
     * @param amount 周数
     * @return 新日期
     */
    public static @NotNull Date addWeeks(Date date, int amount) {
        return add(date, Calendar.WEEK_OF_YEAR, amount);
    }

    /**
     * 添加周
     *
     * @param amount 周数
     * @return 新日期
     */
    public static @NotNull Date addWeeks(int amount) {
        return addWeeks(new Date(), amount);
    }

    /**
     * 添加年
     *
     * @param date   日期
     * @param amount 年数
     * @return 新日期
     */
    public static @NotNull Date addYears(Date date, int amount) {
        return add(date, Calendar.YEAR, amount);
    }

    /**
     * 添加年
     *
     * @param amount 年数
     * @return 新日期
     */
    public static @NotNull Date addYears(int amount) {
        return addYears(new Date(), amount);
    }

    /**
     * 获取指定日期所在月份的第一天
     *
     * @param date 日期
     * @return 新日期
     */
    public static @NotNull Date getStartOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取当前月份开始的日期时间
     *
     * @return 当月开始
     */
    public static @NotNull Date getStartOfMonth() {
        return getStartOfMonth(new Date());
    }

    /**
     * 获取指定日期所在年的第一天
     *
     * @param date 日期
     * @return 新日期
     */
    public static @NotNull Date getStartOfYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取当前年的第一天
     *
     * @return 当年开始
     */
    public static @NotNull Date getStartOfYear() {
        return getStartOfYear(new Date());
    }
}
