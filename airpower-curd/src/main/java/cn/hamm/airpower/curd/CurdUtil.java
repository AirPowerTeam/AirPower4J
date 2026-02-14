package cn.hamm.airpower.curd;

import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.ReflectUtil;
import cn.hamm.airpower.core.annotation.Dictionary;
import cn.hamm.airpower.core.interfaces.IDictionary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <h1>CURD 工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class CurdUtil {
    /**
     * <h1>扫描实体</h1>
     *
     * @param packageName 包名
     * @return 扫描到的实体列表
     */
    public static @NotNull List<EntityMeta> scanEntity(
            String packageName
    ) {
        List<EntityMeta> entityMetaList = new ArrayList<>();
        try {
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(packageName) + "/**/*Entity.class";
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);


            for (Resource resource : resources) {
                // 用于读取类信息
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                // 扫描到的 class
                String className = metadataReader.getClassMetadata().getClassName();
                Class<?> clazz = Class.forName(className);

                Entity entityAnnotation = ReflectUtil.getAnnotation(Entity.class, clazz);
                if (Objects.isNull(entityAnnotation)) {
                    // 不是实体
                    continue;
                }

                EntityMeta entityMeta = new EntityMeta()
                        .setName(clazz.getSimpleName())
                        .setDescription(ReflectUtil.getDescription(clazz));


                // 取出所有属性
                List<Field> fields = ReflectUtil.getFieldList(clazz);
                List<EntityMeta.EntityFieldMeta> entityFieldMetas = new ArrayList<>();
                for (Field field : fields) {
                    Column columnAnnotation = ReflectUtil.getAnnotation(Column.class, field);
                    if (Objects.isNull(columnAnnotation)) {
                        // 非数据库实体
                        continue;
                    }
                    EntityMeta.EntityFieldMeta entityFieldMeta = new EntityMeta.EntityFieldMeta()
                            .setIsUnique(columnAnnotation.unique())
                            .setDefinition(columnAnnotation.columnDefinition())
                            .setName(field.getName())
                            .setDescription(ReflectUtil.getDescription(field))
                            .setType(field.getType().getSimpleName());
                    Dictionary dictionaryAnnotation = ReflectUtil.getAnnotation(Dictionary.class, field);
                    if (Objects.nonNull(dictionaryAnnotation)) {
                        Class<? extends IDictionary> dictionaryClass = dictionaryAnnotation.value();
                        List<Map<String, Object>> dictionaryList = DictionaryUtil.getDictionaryList(dictionaryClass);
                        entityFieldMeta.setOptions(dictionaryList);
                    }

                    Id idAnnotation = ReflectUtil.getAnnotation(Id.class, field);
                    if (Objects.nonNull(idAnnotation)) {
                        entityFieldMeta.setIsId(true);
                    }
                    entityFieldMetas.add(entityFieldMeta);
                }
                entityMeta.setFields(entityFieldMetas);
                entityMetaList.add(entityMeta);
            }
        } catch (Exception exception) {
            log.error("扫描实体出错", exception);
        }
        return entityMetaList;
    }

    @Data
    @Accessors(chain = true)
    public static class EntityMeta {
        /**
         * 名称
         */
        private String name;

        /**
         * 描述
         */
        private String description;

        /**
         * 属性列表
         */
        private List<EntityFieldMeta> fields;

        @Data
        @Accessors(chain = true)
        public static class EntityFieldMeta {
            /**
             * 属性名
             */
            private String name;

            /**
             * 属性描述
             */
            private String description;

            /**
             * 属性类型
             */
            private String type;

            /**
             * 字典
             */
            private List<Map<String, Object>> options;

            /**
             * 是否唯一
             */
            private Boolean isUnique = false;

            /**
             * 数据库定义
             */
            private String definition;

            /**
             * 是否主键
             */
            private Boolean isId = false;
        }
    }
}
