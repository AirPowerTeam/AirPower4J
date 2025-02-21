package cn.hamm.airpower.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * <h1>数字工具类</h1>
 *
 * @author Hamm.cn
 */
public class NumberUtil {
    /**
     * <h3>计算的最大精度保留</h3>
     */
    private static final int DEFAULT_SCALE = 8;

    /**
     * <h3>默认除法的保留方式</h3>
     */
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * <h3>禁止外部实例化</h3>
     */
    @Contract(pure = true)
    private NumberUtil() {
    }

    /**
     * <h3>多个数求和</h3>
     *
     * @param first  加数
     * @param second 被加数
     * @param values 更多被加数
     * @return 和
     */
    public static double add(double first, double second, double... values) {
        return calculate(BigDecimal::add, BigDecimal.valueOf(first), BigDecimal.valueOf(second),
                Arrays.stream(values).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new)
        ).doubleValue();
    }

    /**
     * <h3>多个数求和</h3>
     *
     * @param first  加数
     * @param second 被加数
     * @param values 更多被加数
     * @return 和
     */
    public static long add(long first, long second, long... values) {
        return calculate(BigInteger::add, BigInteger.valueOf(first), BigInteger.valueOf(second),
                Arrays.stream(values).mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new)
        ).longValue();
    }

    /**
     * <h3>多个数相减</h3>
     *
     * @param first  被减数
     * @param second 减数
     * @param values 更多减数
     * @return 差
     */
    public static double subtract(double first, double second, double... values) {
        return calculate(BigDecimal::subtract, BigDecimal.valueOf(first), BigDecimal.valueOf(second),
                Arrays.stream(values).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new)
        ).doubleValue();
    }

    /**
     * <h3>多个数相减</h3>
     *
     * @param first  被减数
     * @param second 减数
     * @param values 更多减数
     * @return 差
     */
    public static long subtract(long first, long second, long... values) {
        return calculate(BigInteger::subtract, BigInteger.valueOf(first), BigInteger.valueOf(second),
                Arrays.stream(values).mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new)
        ).longValue();
    }

    /**
     * <h3>多个数相乘</h3>
     *
     * @param first  乘数
     * @param second 被乘数
     * @param values 更多被乘数
     * @return 乘积
     */
    public static double multiply(double first, double second, double... values) {
        return calculate(BigDecimal::multiply, BigDecimal.valueOf(first), BigDecimal.valueOf(second),
                Arrays.stream(values).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new)
        ).doubleValue();
    }

    /**
     * <h3>多个数相乘</h3>
     *
     * @param first  乘数
     * @param second 被乘数
     * @param values 更多被乘数
     * @return 乘积
     */
    public static long multiply(long first, long second, long... values) {
        return calculate(BigInteger::multiply, BigInteger.valueOf(first), BigInteger.valueOf(second),
                Arrays.stream(values).mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new)
        ).longValue();
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first  被除数
     * @param second 除数
     * @return 商
     */
    public static double divide(double first, double second) {
        return divide(first, second, DEFAULT_SCALE);
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first  被除数
     * @param second 除数
     * @param scale  保留位数
     * @return 商
     */
    public static double divide(double first, double second, int scale) {
        return divide(BigDecimal.valueOf(first), BigDecimal.valueOf(second), scale, DEFAULT_ROUNDING_MODE)
                .doubleValue();
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first        被除数
     * @param second       除数
     * @param scale        保留位数
     * @param roundingMode 舍弃方式
     * @return 商
     */
    public static double divide(double first, double second, int scale, RoundingMode roundingMode) {
        return divide(BigDecimal.valueOf(first), BigDecimal.valueOf(second), scale, roundingMode)
                .doubleValue();
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first  被除数
     * @param second 除数
     * @return 商
     */
    public static double divide(long first, long second) {
        return divide(first, second, DEFAULT_SCALE);
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first  被除数
     * @param second 除数
     * @param scale  保留位数
     * @return 商
     */
    public static double divide(long first, long second, int scale) {
        return divide(BigDecimal.valueOf(first), BigDecimal.valueOf(second), scale, DEFAULT_ROUNDING_MODE)
                .doubleValue();
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first        被除数
     * @param second       除数
     * @param scale        保留位数
     * @param roundingMode 舍弃方式
     * @return 商
     */
    public static double divide(long first, long second, int scale, RoundingMode roundingMode) {
        return divide(BigDecimal.valueOf(first), BigDecimal.valueOf(second), scale, roundingMode)
                .doubleValue();
    }

    /**
     * <h3>计算的业务逻辑</h3>
     *
     * @param function 计算执行方法
     * @param first    第一个数据
     * @param second   第二个数据
     * @param values   更多的数据
     * @param <T>      数据类型
     * @return 计算的结果
     */
    private static <T extends Number> T calculate(@NotNull BiFunction<T, T, T> function, T first, T second, T[] values) {
        T result = function.apply(first, second);
        if (Objects.nonNull(values)) {
            for (T value : values) {
                result = function.apply(result, value);
            }
        }
        return result;
    }

    /**
     * <h3>多个数相除</h3>
     *
     * @param first        被除数
     * @param second       除数
     * @param scale        保留位数
     * @param roundingMode 舍弃方式
     * @return 商
     */
    private static @NotNull BigDecimal divide(BigDecimal first, BigDecimal second, int scale, RoundingMode roundingMode) {
        try {
            if (Objects.equals(BigDecimal.valueOf(0), second)) {
                throw new RuntimeException("除数不能为0");
            }
            return first.divide(second, scale, roundingMode);
        } catch (Exception e) {
            throw new RuntimeException("计算出现异常");
        }
    }

    /**
     * <h3>向下省略</h3>
     *
     * @param value 数字
     * @param scale 位数
     * @return 省略后的数字
     */
    public static @NotNull BigDecimal floor(double value, int scale) {
        return round(value, scale, RoundingMode.DOWN);
    }

    /**
     * <h3>向上省略</h3>
     *
     * @param value 数字
     * @param scale 位数
     * @return 省略后的数字
     */
    public static @NotNull BigDecimal ceil(double value, int scale) {
        return round(value, scale, RoundingMode.UP);
    }

    /**
     * <h3>保留固定位数小数</h3>
     *
     * @param number       数字值
     * @param scale        保留小数位数
     * @param roundingMode 保留小数的模式 {@link RoundingMode}
     * @return 新值
     */
    public static @NotNull BigDecimal round(double number, int scale, @NotNull RoundingMode roundingMode) {
        if (scale < 0) {
            scale = 0;
        }
        return BigDecimal.valueOf(number).setScale(scale, roundingMode);
    }
}
