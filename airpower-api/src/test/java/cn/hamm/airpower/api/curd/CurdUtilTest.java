package cn.hamm.airpower.api.curd;

import cn.hamm.airpower.api.fixture.DemoEntity;
import cn.hamm.airpower.api.fixture.NoDefaultConstructorEntity;
import cn.hamm.airpower.api.fixture.PlainEntity;
import cn.hamm.airpower.api.fixture.UndescribedEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>实体元数据扫描单元测试</h2>
 *
 * <p>扫描结果直接驱动前端表单与 DDL 迁移，字段漏扫或多扫都会变成
 * 「界面少一个字段」或「建表少一列」这种难查的问题。</p>
 *
 * <p>扫描按 {@code *Entity} 命名约定进行，异常被吞掉只记日志，
 * 因此结果为空时第一反应应该是去看日志，而不是怀疑包名写错。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("实体元数据扫描单元测试")
class CurdUtilTest {

    /**
     * 测试实体所在的包
     */
    private static final String FIXTURE_PACKAGE = "cn.hamm.airpower.api.fixture";

    @Nested
    @DisplayName("1. 基础扫描")
    class BasicScan {

        @Test
        @DisplayName("能扫到包内的实体")
        void entitiesFound() {
            List<CurdUtil.EntityMeta> metas = CurdUtil.scanEntity(FIXTURE_PACKAGE);
            assertFalse(metas.isEmpty(), "未扫到任何实体");
        }

        @Test
        @DisplayName("实体名是简单类名")
        void nameIsSimpleClassName() {
            CurdUtil.EntityMeta meta = findByName(metas(), DemoEntity.class.getSimpleName());
            assertNotNull(meta);
            assertEquals(DemoEntity.class.getSimpleName(), meta.getName());
        }

        @Test
        @DisplayName("自身描述优先于从父类继承来的描述")
        void ownDescriptionWinsOverInherited() {
            // 描述是沿继承链「就近」查找的：子类自己写了就用自己的
            assertEquals("演示实体", findByName(metas(), DemoEntity.class.getSimpleName()).getDescription());
        }

        @Test
        @DisplayName("自身没写描述时会继承到基类上的描述")
        void inheritedDescriptionUsedWhenOwnMissing() {
            // ReflectUtil#getDescription 递归向上找，而 CurdEntity 上有 @Description("实体")，
            // 所以「没写描述」的实体拿到的不是自己的类名，而是统一的 "实体"。
            //
            // 影响：前端表单里会出现一堆同名的「实体」，多实体场景下无法区分。
            // 真正的类名回落只在「整条继承链都没有 @Description」时才会发生，
            // 而继承 CurdEntity 的实体永远走不到那一步——除非基类上的注解被去掉
            assertEquals("实体",
                    findByName(metas(), UndescribedEntity.class.getSimpleName()).getDescription(),
                    "未写描述时继承基类的描述，而不是回落到简单类名");
        }

        @Test
        @DisplayName("类名回落到简单类名只对完全不继承 CurdEntity 的类生效")
        void simpleNameFallbackNeedsNoDescriptionInHierarchy() {
            assertEquals("实体", cn.hamm.airpower.core.reflect.ReflectUtil
                    .getDescription(UndescribedEntity.class));
            // 对照组：与继承链无关的普通类确实回落到简单类名
            assertEquals("String", cn.hamm.airpower.core.reflect.ReflectUtil
                    .getDescription(String.class));
        }

        @Test
        @DisplayName("字段列表非 null")
        void fieldsNotNull() {
            assertNotNull(findByName(metas(), DemoEntity.class.getSimpleName()).getFields());
        }

        @Test
        @DisplayName("扫描不存在的包返回空列表而不是抛异常")
        void unknownPackageReturnsEmpty() {
            // 异常被吞掉只记日志，因此「配错包名」和「真的没有实体」表现完全一样，
            // 排查必须先看日志
            assertTrue(CurdUtil.scanEntity("com.not.exists.package").isEmpty());
        }

        @Test
        @DisplayName("扫描包名下的类必须以 Entity 结尾才会被处理")
        void onlyEntitySuffixIsScanned() {
            // 约定即纪律：不叫 XxxEntity 的类即使标了 @Entity 也不会被扫到
            for (CurdUtil.EntityMeta meta : metas()) {
                assertTrue(meta.getName().endsWith("Entity"),
                        "扫到了不符合命名约定的类: " + meta.getName());
            }
        }

        /**
         * 执行一次扫描
         *
         * @return 扫描结果
         */
        private List<CurdUtil.EntityMeta> metas() {
            return CurdUtil.scanEntity(FIXTURE_PACKAGE);
        }
    }

    @Nested
    @DisplayName("2. 字段元数据")
    class FieldMetadata {

        @Test
        @DisplayName("带 @Column 的字段被收录")
        void columnFieldsIncluded() {
            CurdUtil.EntityMeta.EntityFieldMeta meta = fieldOf(DemoEntity.class, "username");
            assertNotNull(meta, "username 未被收录");
            assertEquals("username", meta.getName());
        }

        @Test
        @DisplayName("非数据库字段被排除")
        void nonColumnFieldExcluded() {
            // @Transient 的字段不该出现在表单与 DDL 里
            assertNull(fieldOf(DemoEntity.class, "transientField"),
                    "@Transient 字段不应被扫描");
        }

        @Test
        @DisplayName("字段类型取简单类名")
        void typeIsSimpleName() {
            assertEquals("String", fieldOf(DemoEntity.class, "username").getType());
        }

        @Test
        @DisplayName("字段描述取自 @Description")
        void descriptionFromAnnotation() {
            assertEquals("用户名", fieldOf(DemoEntity.class, "username").getDescription());
        }

        @Test
        @DisplayName("唯一标记被正确识别")
        void uniqueFlagRecognised() {
            assertTrue(fieldOf(DemoEntity.class, "username").getIsUnique());
            assertFalse(fieldOf(DemoEntity.class, "nickname").getIsUnique());
        }

        @Test
        @DisplayName("主键标记被正确识别")
        void idFlagRecognised() {
            // 继承自 CurdEntity 的 id 也必须能扫到，否则前端不会把它标成只读
            assertTrue(fieldOf(DemoEntity.class, "id").getIsId());
            assertFalse(fieldOf(DemoEntity.class, "username").getIsId());
        }

        @Test
        @DisplayName("DDL 定义原文被保留")
        void columnDefinitionPreserved() {
            // DDL 迁移直接用它拼建表语句，任何改写都会与生产库对不上
            assertEquals("varchar(64) comment '用户名'", fieldOf(DemoEntity.class, "username").getDefinition());
        }

        @Test
        @DisplayName("未标字典的字段没有选项")
        void optionsNullWithoutDictionary() {
            assertNull(fieldOf(DemoEntity.class, "username").getOptions());
        }

        @Test
        @DisplayName("非唯一列的实体也能被扫描")
        void entityWithoutUniqueColumnScanned() {
            assertNotNull(findByName(CurdUtil.scanEntity(FIXTURE_PACKAGE),
                    PlainEntity.class.getSimpleName()));
        }

        @Test
        @DisplayName("缺少无参构造的实体不影响扫描")
        void entityWithoutDefaultConstructorScanned() {
            // 扫描只读元数据，不做实例化，因此不该被无参构造的要求卡住
            CurdUtil.EntityMeta meta = findByName(metas(),
                    NoDefaultConstructorEntity.class.getSimpleName());
            assertNotNull(meta, "扫描不应依赖无参构造");
        }
    }

    @Nested
    @DisplayName("3. 扫描结果的可用性")
    class ResultUsability {

        @Test
        @DisplayName("每个实体的字段列表都可遍历")
        void fieldsTraversable() {
            for (CurdUtil.EntityMeta meta : CurdUtil.scanEntity(FIXTURE_PACKAGE)) {
                assertNotNull(meta.getFields(), meta.getName() + " 的字段列表为 null");
                assertNotNull(meta.getName());
            }
        }

        @Test
        @DisplayName("恰好有一个主键被标记")
        void exactlyOneIdMarked() {
            // 多于一个主键会让 DDL 生成错误，零个则主键信息丢失
            CurdUtil.EntityMeta meta = findByName(metas(), DemoEntity.class.getSimpleName());
            long idCount = meta.getFields().stream()
                    .filter(CurdUtil.EntityMeta.EntityFieldMeta::getIsId)
                    .count();
            assertEquals(1, idCount, "主键数量异常: " + meta.getFields());
        }

        @Test
        @DisplayName("唯一标记与主键标记的默认值是 false 而非 null")
        void flagsDefaultToFalseNotNull() {
            // null 会让 JSON 序列化出 null，前端拿到后判断会出错
            for (CurdUtil.EntityMeta.EntityFieldMeta field : allFields()) {
                assertNotNull(field.getIsUnique(), field.getName() + " 的 isUnique 为 null");
                assertNotNull(field.getIsId(), field.getName() + " 的 isId 为 null");
            }
        }

        @Test
        @DisplayName("重复扫描得到相同结果")
        void repeatedScanIsStable() {
            assertEquals(CurdUtil.scanEntity(FIXTURE_PACKAGE).size(),
                    CurdUtil.scanEntity(FIXTURE_PACKAGE).size());
        }

        /**
         * 取出全部实体的全部字段
         *
         * @return 字段列表
         */
        private List<CurdUtil.EntityMeta.EntityFieldMeta> allFields() {
            return CurdUtil.scanEntity(FIXTURE_PACKAGE).stream()
                    .flatMap(meta -> meta.getFields().stream())
                    .toList();
        }
    }

    /**
     * 按名称找实体元数据
     *
     * @param metas 扫描结果
     * @param name  实体名
     * @return 实体元数据，未找到返回 {@code null}
     */
    private static CurdUtil.EntityMeta findByName(List<CurdUtil.EntityMeta> metas, String name) {
        return metas.stream().filter(meta -> meta.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * 列出扫描结果
     *
     * @return 扫描结果
     */
    private static List<CurdUtil.EntityMeta> metas() {
        return CurdUtil.scanEntity(FIXTURE_PACKAGE);
    }

    /**
     * 按名字取实体字段元数据
     *
     * @param clazz 实体类
     * @param field 字段名
     * @return 字段元数据，未找到返回 {@code null}
     */
    private static CurdUtil.EntityMeta.EntityFieldMeta fieldOf(Class<?> clazz, String field) {
        CurdUtil.EntityMeta meta = findByName(metas(), clazz.getSimpleName());
        if (null == meta || null == meta.getFields()) {
            return null;
        }
        return meta.getFields().stream()
                .filter(item -> item.getName().equals(field))
                .findFirst()
                .orElse(null);
    }
}
