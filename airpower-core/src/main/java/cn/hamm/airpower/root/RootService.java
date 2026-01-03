package cn.hamm.airpower.root;

import cn.hamm.airpower.curd.query.QueryExport;
import cn.hamm.airpower.desensitize.Desensitize;
import cn.hamm.airpower.export.ExportHelper;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.airpower.reflect.ReflectUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Objects;

/**
 * <h1>根服务</h1>
 *
 * @author Hamm.cn
 */
@Getter
@Slf4j
@EqualsAndHashCode
public class RootService<E extends RootModel<E>> {
    /**
     * 导出文件帮助类
     */
    @Autowired
    protected ExportHelper exportHelper;

    /**
     * Redis 帮助类
     */
    @Autowired
    protected RedisHelper redisHelper;

    /**
     * 查询导出结果
     *
     * @param queryExport 查询导出模型
     * @return 导出文件地址
     */
    public final String queryExport(@NotNull QueryExport queryExport) {
        return exportHelper.getExportFileUrl(queryExport.getFileCode());
    }

    /**
     * 获取泛型参数
     *
     * @return 类
     */
    public final @NotNull Class<E> getFirstParameterizedTypeClass() {
        //noinspection unchecked
        return (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    /**
     * 脱敏
     *
     * @param entity 待脱敏实体
     * @return 脱敏后的实体
     */
    @Contract("_ -> param1")
    protected E desensitize(E entity) {
        List<Field> fieldList = ReflectUtil.getFieldList(getFirstParameterizedTypeClass());
        fieldList.forEach(field -> {
            Desensitize desensitize = ReflectUtil.getAnnotation(Desensitize.class, field);
            if (Objects.isNull(desensitize)) {
                // 非脱敏注解标记属性
                return;
            }
            // 脱敏字段
            Object fieldValue = ReflectUtil.getFieldValue(entity, field);
            if (Objects.isNull(fieldValue)) {
                // 值本身是空的
                return;
            }
            if (desensitize.replace() && Objects.equals(desensitize.symbol(), fieldValue.toString())) {
                // 如果是替换 且没有修改内容
                ReflectUtil.setFieldValue(entity, field, null);
            }
            if (!desensitize.replace() && fieldValue.toString().contains(desensitize.symbol())) {
                // 如果值包含脱敏字符
                ReflectUtil.setFieldValue(entity, field, null);
            }
        });
        return entity;
    }
}
