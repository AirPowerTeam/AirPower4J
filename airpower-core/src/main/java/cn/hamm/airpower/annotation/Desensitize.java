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
     * <h3>脱敏类型</h3>
     */
    Type value();

    /**
     * <h3>开始保留位数</h3>
     */
    int head() default 0;

    /**
     * <h3>结束保留位数</h3>
     */
    int tail() default 0;

    /**
     * <h3>脱敏符号</h3>
     *
     * @apiNote <ul>
     * <li>{@code replace==false} 提交的数据包含脱敏符号，则该类数据不更新到数据库</li>
     * <li>{@code replace==true} 提交的数据和脱敏符号一致，则该类数据不更新到数据库</li>
     * </ul>
     */
    String symbol() default "*";

    /**
     * <h3>是否替换</h3>
     *
     * @apiNote 如标记为 {@code true}, 则整体脱敏为符号，而不是单个字符替换
     */
    boolean replace() default false;

    /**
     * <h3>脱敏方式</h3>
     */
    @AllArgsConstructor
    @Getter
    enum Type {
        /**
         * <h3>座机号码</h3>
         */
        TELEPHONE(0, 0),

        /**
         * <h3>手机号码</h3>
         */
        MOBILE(3, 4),

        /**
         * <h3>身份证号</h3>
         */
        ID_CARD(6, 4),

        /**
         * <h3>银行卡号</h3>
         */
        BANK_CARD(4, 4),

        /**
         * <h3>车牌号</h3>
         */
        CAR_NUMBER(2, 1),

        /**
         * <h3>邮箱</h3>
         */
        EMAIL(2, 2),

        /**
         * <h3>中文名</h3>
         */
        CHINESE_NAME(1, 1),

        /**
         * <h3>地址</h3>
         */
        ADDRESS(3, 0),

        /**
         * <h3><code>IPv4</code>地址</h3>
         */
        IP_V4(0, 0),

        /**
         * <h3>自定义</h3>
         */
        CUSTOM(0, 0);

        /**
         * <h3>开始至少保留</h3>
         */
        private final int minHead;

        /**
         * <h3>结束至少保留</h3>
         */
        private final int minTail;
    }
}
