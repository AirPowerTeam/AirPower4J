package cn.hamm.airpower.root;

import cn.hamm.airpower.annotation.ReadOnly;
import cn.hamm.airpower.desensitize.Desensitize;
import cn.hamm.airpower.desensitize.DesensitizeUtil;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.reflect.ReflectUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

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
     * 忽略只读字段
     */
    public final void ignoreReadOnlyFields() {
        ReflectUtil.getFieldList(getClass()).stream()
                .filter(field -> Objects.nonNull(ReflectUtil.getAnnotation(ReadOnly.class, field)))
                .forEach(field -> ReflectUtil.clearFieldValue(this, field));
    }

    /**
     * 复制一个新对象
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
     * 脱敏
     *
     * @return 实体
     */
    public final M desensitize() {
        Class<M> clazz = (Class<M>) getClass();
        List<Field> allFields = ReflectUtil.getFieldList(clazz);
        allFields.forEach(this::desensitize);
        return (M) this;
    }

    /**
     * 字段脱敏
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
