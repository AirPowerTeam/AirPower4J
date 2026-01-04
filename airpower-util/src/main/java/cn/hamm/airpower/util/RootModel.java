package cn.hamm.airpower.util;

import cn.hamm.airpower.util.Meta;
import cn.hamm.airpower.util.annotation.Desensitize;
import cn.hamm.airpower.util.annotation.ReadOnly;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * <h1>数据根模型</h1>
 *
 * @author Hamm.cn
 */
@Getter
@Slf4j
@EqualsAndHashCode
@SuppressWarnings("unchecked")
public class RootModel<M extends RootModel<M>> {
    /**
     * 是否是继承自 RootModel
     *
     * @param clazz 类
     * @return 布尔
     */
    public static boolean isModel(Class<?> clazz) {
        if (Objects.isNull(clazz)) {
            return false;
        }
        if (RootModel.class.equals(clazz)) {
            return true;
        }
        return isModel(clazz.getSuperclass());
    }

    /**
     * 忽略只读字段
     */
    public final void ignoreReadOnlyFields() {
        ReflectUtil.getFieldList(getClass()).stream()
                .filter(field -> Objects.nonNull(ReflectUtil.getAnnotation(ReadOnly.class, field)))
                .forEach(field -> ReflectUtil.clearFieldValue(this, field));
    }

    /**
     * 处理字段值
     */
    public final void fieldValueResolver(BiConsumer<M, Field> consumer) {
        Class<M> clazz = (Class<M>) getClass();
        List<Field> allFields = ReflectUtil.getFieldList(clazz);
        allFields.forEach(field -> consumer.accept((M) this, field));
    }

    /**
     * 排除非元数据的字段
     *
     * @param field 字段
     */
    public final void excludeFieldNotMeta(M instance, @NotNull Field field) {
        Object value = ReflectUtil.getFieldValue(instance, field);
        if (Objects.isNull(value)) {
            return;
        }
        if (isModel(value.getClass())) {
            ((RootModel<?>) value).excludeFieldNotMeta();
            return;
        }
        Meta meta = ReflectUtil.getAnnotation(Meta.class, field);
        if (Objects.isNull(meta)) {
            // 判断 Getter 是否被标记
            String fieldGetter = ReflectUtil.getFieldGetter(field);
            try {
                Method getter = instance.getClass().getMethod(fieldGetter);
                meta = ReflectUtil.getAnnotation(Meta.class, getter);
                if (Objects.isNull(meta)) {
                    ReflectUtil.setFieldValue(this, field, null);
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    /**
     * 忽略非元数据的字段
     *
     * @return 当前实例
     */
    public final M excludeFieldNotMeta() {
        fieldValueResolver((model, field) -> excludeFieldNotMeta((M) this, field));
        return (M) this;
    }

    /**
     * 脱敏
     *
     * @param field 字段
     * @param value 值
     */
    public final void desensitize(Field field, @NotNull Object value) {
        Desensitize desensitize = ReflectUtil.getAnnotation(Desensitize.class, field);
        if (Objects.isNull(desensitize)) {
            return;
        }
        if ((value instanceof String valueString)) {
            if (desensitize.replace()) {
                ReflectUtil.setFieldValue(this, field, desensitize.symbol());
                return;
            }
            // 如果不是字符串，则置空
            ReflectUtil.setFieldValue(this, field,
                    DesensitizeUtil.desensitize(
                            valueString,
                            desensitize.value(),
                            desensitize.head(),
                            desensitize.tail(),
                            desensitize.symbol()
                    )
            );
            return;
        }
        ReflectUtil.setFieldValue(this, field, null);
    }


    /**
     * 模型字段值排除或脱敏
     *
     * @param exposeModelsFieldNotMeta 暴露所有字段的类列表
     * @param isDesensitize            是否需要脱敏
     */
    public final void exclude(@NotNull List<Class<? extends RootModel<?>>> exposeModelsFieldNotMeta, boolean isDesensitize) {
        this.fieldValueResolver((instance, field) -> {
            Object value = ReflectUtil.getFieldValue(instance, field);
            if (Objects.isNull(value)) {
                return;
            }
            if (value instanceof Collection<?> valueList) {
                // 是对象集合
                valueList.forEach(item -> {
                    if (RootModel.isModel(item.getClass())) {
                        @SuppressWarnings("unchecked")
                        M itemModel = (M) item;
                        itemModel.exclude(exposeModelsFieldNotMeta, isDesensitize);
                    }
                });
                return;
            }
            if (RootModel.isModel(value.getClass())) {
                // 如果是模型，则递归脱敏
                @SuppressWarnings("unchecked")
                M payload = ((M) value);
                payload.exclude(exposeModelsFieldNotMeta, isDesensitize);
                return;
            }
            if (!exposeModelsFieldNotMeta.contains(this.getClass())) {
                this.excludeFieldNotMeta(instance, field);
            }
            if (isDesensitize) {
                this.desensitize(field, value);
            }
        });
    }
}
