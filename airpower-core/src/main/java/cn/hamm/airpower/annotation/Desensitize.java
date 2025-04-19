package cn.hamm.airpower.annotation;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记字段在 API 输出时自动脱敏</h1>
 *
 * @author Hamm.cn
 * @apiNote 如需标记不脱敏的接口，可使用 {@link DesensitizeExclude}
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface Desensitize {
    /**
     * 脱敏类型
     */
    Type value();

    /**
     * 开始保留位数
     */
    int head() default 0;

    /**
     * 结束保留位数
     */
    int tail() default 0;

    /**
     * 脱敏符号
     *
     * @apiNote <ul>
     * <li>{@link #replace()} == {@code false} 提交的数据包含脱敏符号，则该类数据不更新到数据库</li>
     * <li>{@link #replace()} == {@code true} 提交的数据和脱敏符号一致，则该类数据不更新到数据库</li>
     * </ul>
     */
    String symbol() default "*";

    /**
     * 是否替换
     *
     * @apiNote 如标记为 {@code true}, 则整体脱敏为符号，而不是单个字符替换
     */
    boolean replace() default false;

    /**
     * 脱敏方式
     */
    @AllArgsConstructor
    @Getter
    enum Type {
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
}
