package cn.hamm.airpower.validate;

import cn.hamm.airpower.interfaces.IDictionary;
import cn.hamm.airpower.util.DictionaryUtil;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记进行字典校验</h1>
 *
 * @author Hamm.cn
 * @apiNote 请注意, 请自行做非空验证, 字典必须实现 {@link IDictionary} 接口
 */
@Constraint(validatedBy = Dictionary.DictionaryValidator.class)
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Documented
public @interface Dictionary {
    /**
     * 错误信息
     */
    String message() default "不允许的枚举字典值";

    /**
     * 使用的枚举类
     *
     * @see IDictionary
     */
    Class<? extends IDictionary> value();

    /**
     * 验证组
     */
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 字典验证实现类
     */
    @Component
    class DictionaryValidator implements ConstraintValidator<Dictionary, Integer> {
        /**
         * 标记的枚举类
         */
        private Class<? extends IDictionary> enumClazz = null;

        /**
         * 验证
         *
         * @param value   验证的值
         * @param context 验证器会话
         * @return 验证结果
         */
        @Contract("null, _ -> true")
        @Override
        public final boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (null == value) {
                return true;
            }
            try {
                DictionaryUtil.getDictionary(enumClazz, value);
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        /**
         * 初始化
         *
         * @param dictionary 字典类
         */
        @Contract(mutates = "this")
        @Override
        public final void initialize(@NotNull Dictionary dictionary) {
            enumClazz = dictionary.value();
        }
    }

}