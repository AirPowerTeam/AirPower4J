package cn.hamm.airpower.util;

import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.interfaces.IDictionary;
import cn.hamm.airpower.interfaces.IFunction;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;

/**
 * <h1>枚举字典工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class DictionaryUtil {
    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private DictionaryUtil() {
    }

    /**
     * 查字典
     *
     * @param enumClass 枚举字典类
     * @param key       枚举字典值
     * @param <D>       [泛型] 字典类型
     * @return 查到的字典
     */
    public static <D extends IDictionary> @NotNull D getDictionary(@NotNull Class<D> enumClass, int key) {
        return getDictionary(enumClass, IDictionary::getKey, key);
    }

    /**
     * 查字典
     *
     * @param enumClass 枚举字典类
     * @param function  获取指定值的方法
     * @param value     比较的值
     * @param <D>       字典类型
     * @return 查到的字典
     */
    public static <D extends IDictionary> @NotNull D getDictionary(
            @NotNull Class<D> enumClass, Function<D, Object> function, Object value
    ) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(enumItem -> Objects.equals(function.apply(enumItem), value))
                .findFirst()
                .orElseThrow(new ServiceException(
                        "传入的值(" + enumClass.getSimpleName() + "=" + value + ")不在字典可选范围内",
                        getDictionaryList(enumClass))
                );
    }

    /**
     * 获取指定枚举类的 {@code ListMap} 数据
     *
     * @param clazz 枚举类
     * @return 枚举选项列表
     */
    public static <D extends IDictionary> @NotNull List<Map<String, Object>> getDictionaryList(
            @NotNull Class<D> clazz
    ) {
        return getDictionaryList(clazz, IDictionary::getKey, IDictionary::getLabel);
    }

    /**
     * 获取指定枚举类的 {@code ListMap} 数据
     *
     * @param clazz   枚举字典类
     * @param lambdas 需要获取的方法表达式
     * @param <D>     字典类型
     * @return 枚举选项列表
     */
    @SafeVarargs
    public static <D extends IDictionary> @NotNull List<Map<String, Object>> getDictionaryList(
            @NotNull Class<D> clazz, IFunction<D, Object>... lambdas
    ) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        //取出所有枚举类型
        Arrays.stream(clazz.getEnumConstants()).forEach(enumItem -> {
            Map<String, Object> item = new HashMap<>(lambdas.length);
            // 依次取出参数的值
            Arrays.stream(lambdas).forEach(lambda -> {
                try {
                    item.put(StringUtils.uncapitalize(ReflectUtil.getLambdaFunctionName(lambda)), lambda.apply(enumItem));
                } catch (Exception exception) {
                    log.error(exception.getMessage(), exception);
                }
            });
            mapList.add(item);
        });
        return mapList;
    }
}
