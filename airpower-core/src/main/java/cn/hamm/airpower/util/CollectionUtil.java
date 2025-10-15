package cn.hamm.airpower.util;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.datetime.DateTimeUtil;
import cn.hamm.airpower.dictionary.Dictionary;
import cn.hamm.airpower.dictionary.DictionaryUtil;
import cn.hamm.airpower.dictionary.IDictionary;
import cn.hamm.airpower.export.Export;
import cn.hamm.airpower.reflect.ReflectUtil;
import cn.hamm.airpower.root.RootModel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

/**
 * <h1>内置的集合工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class CollectionUtil {
    /**
     * CSV 缩进符号
     */
    private static final String INDENT = "\t";

    /**
     * CSV 列分隔符
     */
    private static final String CSV_COLUMN_DELIMITER = ",";

    /**
     * CSV 行分隔符
     */
    private static final String CSV_ROW_DELIMITER = "\n";

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private CollectionUtil() {
    }

    /**
     * 获取集合中的 {@code 非null} 元素
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
     * 将集合转换为 CSV 文件
     *
     * @param list      集合
     * @param itemClass 元素的类名
     * @param <M>       元素类型
     * @return InputStream
     */
    @Contract("_, _ -> new")
    public static <M extends RootModel<M>> @NotNull InputStream toCsvInputStream(List<M> list, Class<M> itemClass) {
        List<Field> fieldList = new ArrayList<>();
        ReflectUtil.getFieldList(itemClass).forEach(field -> {
            Export export = ReflectUtil.getAnnotation(Export.class, field);
            if (Objects.isNull(export)) {
                return;
            }
            fieldList.add(field);
        });

        List<String> rowList = new ArrayList<>();
        // 添加表头
        rowList.add(String.join(CSV_COLUMN_DELIMITER, fieldList.stream().map(ReflectUtil::getDescription).toList()));

        String json = Json.toString(list);
        List<Map<String, Object>> mapList = Json.parse2MapList(json);
        mapList.forEach(map -> {
            List<String> columnList = new ArrayList<>();
            fieldList.forEach(field -> {
                final String fieldName = field.getName();
                Object value = map.get(fieldName);
                value = prepareExcelColumn(fieldName, value, fieldList);
                columnList.add(value.toString()
                        .replaceAll(CSV_COLUMN_DELIMITER, " ")
                        .replaceAll(CSV_ROW_DELIMITER, " "));
            });
            rowList.add(String.join(CSV_COLUMN_DELIMITER, columnList));
        });
        return new ByteArrayInputStream(String.join(CSV_ROW_DELIMITER, rowList).getBytes());
    }

    /**
     * 准备导出列
     *
     * @param fieldName 字段名
     * @param value     当前值
     * @param fieldList 字段列表
     * @return 处理后的值
     */
    private static @NotNull Object prepareExcelColumn(String fieldName, Object value, List<Field> fieldList) {
        if (Objects.isNull(value) || !StringUtils.hasText(value.toString())) {
            value = "-";
        }
        try {
            Field field = fieldList.stream()
                    .filter(item -> Objects.equals(item.getName(), fieldName))
                    .findFirst()
                    .orElse(null);
            if (Objects.isNull(field)) {
                return value;
            }
            Export export = ReflectUtil.getAnnotation(Export.class, field);
            if (Objects.isNull(export)) {
                return value;
            }
            return switch (export.value()) {
                case DATETIME -> INDENT + DateTimeUtil.format(Long.parseLong(value.toString()));
                case TEXT -> INDENT + value;
                case BOOLEAN -> (boolean) value ? "是" : "否";
                case DICTIONARY -> {
                    Dictionary dictionary = ReflectUtil.getAnnotation(Dictionary.class, field);
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
