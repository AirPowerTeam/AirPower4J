package cn.hamm.airpower.root;

import cn.hamm.airpower.annotation.*;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.util.CollectionUtil;
import cn.hamm.airpower.util.DesensitizeUtil;
import cn.hamm.airpower.util.ReflectUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import static cn.hamm.airpower.config.Constant.STRING_GET;

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
     * <h3>忽略只读字段</h3>
     */
    public final void ignoreReadOnlyFields() {
        ReflectUtil.getFieldList(getClass()).stream()
                .filter(field -> Objects.nonNull(ReflectUtil.getAnnotation(ReadOnly.class, field)))
                .forEach(field -> ReflectUtil.clearFieldValue(this, field));
    }

    /**
     * <h3>复制一个新对象</h3>
     *
     * @return 返回实例
     */
    public final @NotNull M copy() {
        try {
            M target = (M) getClass().getConstructor().newInstance();
            BeanUtils.copyProperties(this, target);
            return target;
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * <h3>过滤和脱敏</h3>
     *
     * @param filterClass   过滤器类
     * @param isDesensitize 是否需要脱敏
     * @return 实体
     * @see #filterAndDesensitize(Filter, boolean)
     * @see #desensitize(Field)
     * @see #filter(Class)
     */
    public final M filterAndDesensitize(@NotNull Class<?> filterClass, boolean isDesensitize) {
        Class<M> clazz = (Class<M>) getClass();
        Exclude exclude = clazz.getAnnotation(Exclude.class);
        // 类中没有标排除 则所有字段全暴露 走黑名单
        boolean isExpose = Objects.nonNull(exclude) && Arrays.asList(exclude.filters()).contains(filterClass);
        BiConsumer<@NotNull Field, @NotNull Class<?>> task = isExpose ? this::exposeBy : this::excludeBy;
        List<Field> allFields = ReflectUtil.getFieldList(clazz);
        allFields.forEach(field -> {
            if (!Objects.equals(Void.class, filterClass)) {
                task.accept(field, filterClass);
                filterField(field, filterClass, isDesensitize);
            }
            if (isDesensitize) {
                desensitize(field);
            }
        });
        return (M) this;
    }

    /**
     * <h3>过滤字段</h3>
     *
     * @param filterClass 过滤器
     * @return 实体
     * @see #filterAndDesensitize(Class, boolean)
     * @see #filterAndDesensitize(Filter, boolean)
     * @see #desensitize(Field)
     */
    public final M filter(Class<?> filterClass) {
        return filterAndDesensitize(filterClass, false);
    }

    /**
     * <h3>过滤和脱敏</h3>
     *
     * @param filter        过滤器注解
     * @param isDesensitize 是否需要脱敏
     * @return 实体
     * @see #filterAndDesensitize(Class, boolean)
     * @see #filter(Class)
     * @see #desensitize(Field)
     */
    public final M filterAndDesensitize(@Nullable Filter filter, boolean isDesensitize) {
        if (Objects.isNull(filter)) {
            return filterAndDesensitize(Void.class, isDesensitize);
        }
        return filterAndDesensitize(filter.value(), isDesensitize);
    }

    /**
     * <h3>通过指定的过滤器排除字段</h3>
     *
     * @param field       字段
     * @param filterClass 过滤器
     */
    private void excludeBy(@NotNull Field field, @NotNull Class<?> filterClass) {
        Class<?>[] excludeClasses = null;
        final String fieldGetter = STRING_GET + StringUtils.capitalize(field.getName());
        try {
            Method getMethod = getClass().getMethod(fieldGetter);
            Exclude methodExclude = ReflectUtil.getAnnotation(Exclude.class, getMethod);
            if (Objects.nonNull(methodExclude)) {
                // 属性的Getter上标记了排除
                excludeClasses = methodExclude.filters();
            }
        } catch (NoSuchMethodException exception) {
            log.error(exception.getMessage(), exception);
        }
        if (Objects.isNull(excludeClasses)) {
            Exclude fieldExclude = ReflectUtil.getAnnotation(Exclude.class, field);
            if (Objects.isNull(fieldExclude)) {
                // 属性Getter没标记 也没有属性本身标记 则暴露
                return;
            }
            // 属性Getter没标记 但是属性本身标记了
            excludeClasses = fieldExclude.filters();
        }

        boolean isNeedClear = true;
        if (excludeClasses.length > 0) {
            isNeedClear = Arrays.asList(excludeClasses).contains(filterClass);
        }
        if (isNeedClear) {
            ReflectUtil.clearFieldValue(this, field);
        }
    }

    /**
     * <h3>通过指定的过滤器暴露字段</h3>
     *
     * @param field       字段
     * @param filterClass 过滤器
     */
    private void exposeBy(@NotNull Field field, @NotNull Class<?> filterClass) {
        final String fieldGetter = STRING_GET + StringUtils.capitalize(field.getName());
        Class<?>[] exposeClasses = null;
        try {
            Method getMethod = getClass().getMethod(fieldGetter);
            Expose methodExpose = ReflectUtil.getAnnotation(Expose.class, getMethod);
            if (Objects.nonNull(methodExpose)) {
                // 属性的Getter标记了暴露
                exposeClasses = methodExpose.filters();
            }
            // 属性的Getter没有标记
        } catch (NoSuchMethodException exception) {
            log.error(exception.getMessage(), exception);
        }
        if (Objects.isNull(exposeClasses)) {
            Expose fieldExpose = ReflectUtil.getAnnotation(Expose.class, field);
            if (Objects.isNull(fieldExpose)) {
                // 属性以及Getter都没有标记暴露 则排除
                ReflectUtil.clearFieldValue(this, field);
                return;
            }
            exposeClasses = fieldExpose.filters();
        }
        if (exposeClasses.length == 0) {
            // 虽然标记但未指定过滤器 所有场景都暴露
            return;
        }
        boolean isExpose = Arrays.asList(exposeClasses).contains(filterClass);
        if (!isExpose) {
            // 当前场景不在标记的暴露场景中 则排除
            ReflectUtil.clearFieldValue(this, field);
        }
    }

    /**
     * <h3>递归过滤和脱敏</h3>
     *
     * @param field         字段
     * @param isDesensitize 是否需要脱敏
     */
    private void filterField(@NotNull Field field, Class<?> filterClass, boolean isDesensitize) {
        Object fieldValue = ReflectUtil.getFieldValue(this, field);
        if (fieldValue instanceof Collection<?>) {
            Class<?> fieldClass = field.getType();
            if (!ReflectUtil.isModel(fieldClass)) {
                return;
            }
            Collection<RootModel<?>> collection = CollectionUtil.getCollectWithoutNull(
                    (Collection<RootModel<?>>) fieldValue, fieldClass
            );
            collection.forEach(item -> item.filterAndDesensitize(filterClass, isDesensitize));
            ReflectUtil.setFieldValue(this, field, collection);
            return;
        }
        if (!ReflectUtil.isModel(field.getType())) {
            return;
        }
        if (Objects.isNull(fieldValue)) {
            return;
        }
        ReflectUtil.setFieldValue(this, field,
                ((RootModel<?>) fieldValue).filterAndDesensitize(filterClass, isDesensitize)
        );
    }

    /**
     * <h3>字段脱敏</h3>
     *
     * @param field 字段
     */
    private void desensitize(@NotNull Field field) {
        Desensitize desensitize = ReflectUtil.getAnnotation(Desensitize.class, field);
        if (Objects.isNull(desensitize)) {
            return;
        }
        Object value = ReflectUtil.getFieldValue(this, field);
        if (Objects.isNull(value)) {
            return;
        }
        if (!(value instanceof String valueString)) {
            return;
        }
        if (desensitize.replace()) {
            ReflectUtil.setFieldValue(this, field, desensitize.symbol());
            return;
        }
        ReflectUtil.setFieldValue(this, field,
                DesensitizeUtil.desensitize(
                        valueString,
                        desensitize.value(),
                        desensitize.head(),
                        desensitize.tail(),
                        desensitize.symbol()
                )
        );
    }
}
