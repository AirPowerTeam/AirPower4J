package cn.hamm.airpower.util;

import cn.hamm.airpower.annotation.ExcelColumn;
import cn.hamm.airpower.interfaces.IDictionary;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.root.RootEntity;
import cn.hamm.airpower.validate.dictionary.Dictionary;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

import static cn.hamm.airpower.config.Constant.*;

/**
 * <h1>内置的集合工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class CollectionUtil {
    /**
     * <h3>禁止外部实例化</h3>
     */
    @Contract(pure = true)
    private CollectionUtil() {
    }

    /**
     * <h3>获取集合中的 {@code 非null} 元素</h3>
     *
     * @param list       原始集合
     * @param fieldClass 数据类型
     * @param <T>        数据类型
     * @return 处理后的集合
     */
    public static @NotNull <T> Collection<T> getCollectWithoutNull(Collection<T> list, Class<?> fieldClass) {
        if (Objects.equals(Set.class, fieldClass)) {
            return Objects.isNull(list) ? new HashSet<>() : list;
        }
        return Objects.isNull(list) ? new ArrayList<>() : list;
    }

    /**
     * <h3>将集合转换为CSV文件</h3>
     *
     * @param list      集合
     * @param itemClass 元素的类名
     * @param <E>       元素类型
     * @return InputStream
     */
    @Contract("_, _ -> new")
    public static <E extends RootEntity<E>> @NotNull InputStream toCsvInputStream(List<E> list, Class<E> itemClass) {
        List<Field> fieldList = new ArrayList<>();
        ReflectUtil.getFieldList(itemClass).forEach(field -> {
            ExcelColumn excelColumn = ReflectUtil.getAnnotation(ExcelColumn.class, field);
            if (Objects.isNull(excelColumn)) {
                return;
            }
            fieldList.add(field);
        });

        List<String> rowList = new ArrayList<>();
        // 添加表头
        rowList.add(String.join(STRING_COMMA, fieldList.stream().map(ReflectUtil::getDescription).toList()));

        String json = Json.toString(list);
        List<Map<String, Object>> mapList = Json.parse2MapList(json);
        mapList.forEach(map -> {
            List<String> columnList = new ArrayList<>();
            fieldList.forEach(field -> {
                final String fieldName = field.getName();
                Object value = map.get(fieldName);
                value = prepareExcelColumn(fieldName, value, fieldList);
                columnList.add(value.toString()
                        .replaceAll(STRING_COMMA, STRING_BLANK)
                        .replaceAll(REGEX_LINE_BREAK, STRING_BLANK));
            });
            rowList.add(String.join(STRING_COMMA, columnList));
        });
        return new ByteArrayInputStream(String.join(REGEX_LINE_BREAK, rowList).getBytes());
    }

    /**
     * <h3>准备导出列</h3>
     *
     * @param fieldName 字段名
     * @param value     当前值
     * @param fieldList 字段列表
     * @return 处理后的值
     */
    private static @NotNull Object prepareExcelColumn(String fieldName, Object value, List<Field> fieldList) {
        if (Objects.isNull(value) || !StringUtils.hasText(value.toString())) {
            value = STRING_LINE;
        }
        try {
            Field field = fieldList.stream()
                    .filter(item -> Objects.equals(item.getName(), fieldName))
                    .findFirst()
                    .orElse(null);
            if (Objects.isNull(field)) {
                return value;
            }
            ExcelColumn excelColumn = ReflectUtil.getAnnotation(ExcelColumn.class, field);
            if (Objects.isNull(excelColumn)) {
                return value;
            }
            return switch (excelColumn.value()) {
                case DATETIME -> REGEX_TAB + DateTimeUtil.format(Long.parseLong(value.toString()));
                case TEXT -> REGEX_TAB + value;
                case BOOLEAN -> (boolean) value ? STRING_YES : STRING_NO;
                case DICTIONARY -> {
                    cn.hamm.airpower.validate.dictionary.Dictionary dictionary = ReflectUtil.getAnnotation(Dictionary.class, field);
                    if (Objects.isNull(dictionary)) {
                        yield value;
                    } else {
                        IDictionary dict = DictionaryUtil.getDictionary(
                                dictionary.value(), Integer.parseInt(value.toString())
                        );
                        yield dict.getLabel();
                    }
                }
                default -> value;
            };
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            return value;
        }
    }
}
