package cn.hamm.airpower.root;

import cn.hamm.airpower.curd.query.QueryExport;
import cn.hamm.airpower.curd.query.QueryListRequest;
import cn.hamm.airpower.desensitize.Desensitize;
import cn.hamm.airpower.export.ExportHelper;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.airpower.reflect.ReflectUtil;
import cn.hamm.airpower.util.CollectionUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
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
     * Redis帮助类
     */
    @Autowired
    protected RedisHelper redisHelper;

    /**
     * 创建导出任务
     *
     * @param list 查到的数据列表
     * @return 导出任务ID
     * @see #beforeExportQuery(QueryListRequest)
     * @see #afterExportQuery(List)
     * @see #createExportStream(List)
     */
    public final String createExportTask(List<E> list) {
        return exportHelper.createExportTask(() -> saveExportFile(createExportStream(list)));
    }

    /**
     * 导出查询前置方法
     *
     * @param queryListRequest 查询请求
     * @return 处理后的查询请求
     */
    protected QueryListRequest<E> beforeExportQuery(QueryListRequest<E> queryListRequest) {
        return queryListRequest;
    }

    /**
     * 创建导出数据的文件字节流
     *
     * @param exportList 导出的数据
     * @return 导出的文件的字节流
     * @apiNote 支持完全重写导出文件生成逻辑
     *
     * <ul>
     *     <li>默认导出为 {@code CSV} 表格，如需自定义导出方式或格式，可直接重写此方法</li>
     *     <li>如仅需 <b>自定义导出存储位置</b>，可重写 {@link #saveExportFile(InputStream)}</li>
     * </ul>
     */
    protected InputStream createExportStream(List<E> exportList) {
        return CollectionUtil.toCsvInputStream(exportList, getFirstParameterizedTypeClass());
    }

    /**
     * 保存导出生成的文件
     *
     * @param exportFileStream 导出的文件字节流
     * @return 存储后的可访问路径
     * @apiNote 可重写此方法存储至其他地方后返回可访问绝对路径
     */
    protected String saveExportFile(InputStream exportFileStream) {
        // 准备导出的相对路径
        return exportHelper.saveExportFileStream(exportFileStream);
    }

    /**
     * 导出查询后置方法
     *
     * @param exportList 导出的数据列表
     * @return 处理后的数据列表
     */
    protected List<E> afterExportQuery(@NotNull List<E> exportList) {
        return exportList;
    }

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
