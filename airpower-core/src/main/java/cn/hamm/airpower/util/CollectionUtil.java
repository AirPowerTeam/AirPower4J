package cn.hamm.airpower.util;

import cn.hamm.airpower.datetime.DateTimeUtil;
import cn.hamm.airpower.dictionary.Dictionary;
import cn.hamm.airpower.dictionary.DictionaryUtil;
import cn.hamm.airpower.dictionary.IDictionary;
import cn.hamm.airpower.export.Export;
import cn.hamm.airpower.reflect.ReflectUtil;
import cn.hamm.airpower.root.RootModel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

/**
 * <h1>内置的集合工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class CollectionUtil {
    /**
     * CSV 列分隔符
     */
    public static final String CSV_COLUMN_DELIMITER = ",";
    /**
     * CSV 行分隔符
     */
    public static final String CSV_ROW_DELIMITER = "\n";
    /**
     * CSV 缩进符号
     */
    private static final String INDENT = "\t";

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
        return toCsvInputStream(itemClass, (fieldList) -> getCsvValueList(list, fieldList));
    }

    /**
     * 将集合转换为 CSV 文件
     *
     * @param itemClass         元素的类名
     * @param valueListFunction 列数据列表函数
     * @param <M>               元素类型
     * @return InputStream
     */
    @Contract("_, _ -> new")
    private static <M extends RootModel<M>> @NotNull InputStream toCsvInputStream(Class<M> itemClass, @NotNull Function<List<Field>, List<String>> valueListFunction) {
        List<Field> fieldList = getExportFieldList(itemClass);
        List<String> rowList = getCsvHeaderList(fieldList);
        List<String> valueList = valueListFunction.apply(fieldList);
        rowList.addAll(valueList);
        return new ByteArrayInputStream(String.join(CSV_ROW_DELIMITER, rowList).getBytes());
    }

    /**
     * 获取 CSV 行数据列表
     *
     * @param list      列表
     * @param fieldList 列数组
     * @param <M>       元素类型
     * @return 列表数据
     */
    public static <M extends RootModel<M>> @NotNull List<String> getCsvValueList(@NotNull List<M> list, List<Field> fieldList) {
        List<String> rowList = new ArrayList<>();
        list.forEach(entity -> {
            List<String> columnList = new ArrayList<>();
            fieldList.forEach(field -> {
                Object value = getCsvColumnValue(entity, field);
                columnList.add(value.toString()
                        .replaceAll(CSV_COLUMN_DELIMITER, " ")
                        .replaceAll(CSV_ROW_DELIMITER, " "));
            });
            rowList.add(String.join(CSV_COLUMN_DELIMITER, columnList));
        });
        return rowList;
    }

    /**
     * 获取 CSV 表头行
     *
     * @param fieldList 字段列表
     * @return 列数据
     */
    public static @NotNull List<String> getCsvHeaderList(@NotNull List<Field> fieldList) {
        List<String> rowList = new ArrayList<>();
        // 添加表头
        rowList.add(String.join(CSV_COLUMN_DELIMITER, fieldList.stream().map(ReflectUtil::getDescription).toList()));
        return rowList;
    }

    /**
     * 获取导出字段列表
     *
     * @param itemClass 类
     * @param <M>       元素类型
     * @return 字段列表
     */
    public static <M extends RootModel<M>> @NotNull List<Field> getExportFieldList(Class<M> itemClass) {
        List<CsvField> fieldList = new ArrayList<>();
        ReflectUtil.getFieldList(itemClass).forEach(field -> {
            Export export = ReflectUtil.getAnnotation(Export.class, field);
            if (Objects.isNull(export)) {
                return;
            }
            fieldList.add(new CsvField().setField(field).setSort(export.sort()));
        });
        // sort 排序 从小到大
        fieldList.sort(Comparator.comparing(CsvField::getSort).reversed());
        return fieldList.stream().map(CsvField::getField).toList();
    }

    /**
     * 获取导出列的数据
     *
     * @param model 数据
     * @param field 字段
     * @return 处理后的值
     */
    private static <M extends RootModel<M>> @NotNull Object getCsvColumnValue(@NotNull M model, @NotNull Field field) {
        Object value = ReflectUtil.getFieldValue(model, field);
        if (Objects.isNull(value) || !StringUtils.hasText(value.toString())) {
            value = "-";
        }
        try {
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

    /**
     * CSV列
     */
    @Accessors(chain = true)
    @Data
    static class CsvField {
        /**
         * 字段
         */
        private Field field;

        /**
         * 排序
         */
        private Integer sort;
    }
}
