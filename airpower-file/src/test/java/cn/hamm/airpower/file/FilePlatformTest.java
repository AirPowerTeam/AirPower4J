package cn.hamm.airpower.file;

import cn.hamm.airpower.file.fixture.InheritedKeyPlatform;
import cn.hamm.airpower.file.fixture.OverriddenKeyPlatform;
import cn.hamm.airpower.file.fixture.TestFilePlatform;
import cn.hamm.airpower.file.fixture.UnannotatedPlatform;
import cn.hamm.airpower.file.platform.aliyun.AliyunOssHelper;
import cn.hamm.airpower.file.platform.local.LocalFileHelper;
import cn.hamm.airpower.file.platform.tencent.TencentCloudCosHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * <h2>文件存储平台注解单元测试</h2>
 *
 * <p>这个注解是「容器里到底有哪些平台」的唯一依据，
 * {@code FileHelper} 与 {@code AbstractFilePlatformFactory#getKey()} 都用
 * {@code getClass().getAnnotation()} 读它。三个元注解各管一件事：</p>
 * <ul>
 *     <li>{@code RUNTIME}：运行期读不到，平台就一个都注册不上</li>
 *     <li>{@code @Inherited}：子类沿用父类标识，这是「子类自动算作同一个平台」的根源</li>
 *     <li>{@code @Component}：被标注的类自动进容器，业务侧才能注入使用</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("文件存储平台注解单元测试")
class FilePlatformTest {
    /**
     * 本地平台标识，直接读注解而不复制字面量
     */
    private static final String LOCAL_KEY = LocalFileHelper.class.getAnnotation(FilePlatform.class).value();

    /**
     * 阿里云 OSS 平台标识
     */
    private static final String ALIYUN_KEY = AliyunOssHelper.class.getAnnotation(FilePlatform.class).value();

    /**
     * 腾讯云 COS 平台标识
     */
    private static final String TENCENT_KEY = TencentCloudCosHelper.class.getAnnotation(FilePlatform.class).value();

    @Test
    @DisplayName("必须是运行期保留")
    void runtimeRetention() {
        assertEquals(RetentionPolicy.RUNTIME, FilePlatform.class.getAnnotation(Retention.class).value(),
                "运行期读不到注解，FileHelper 启动时收集到的平台数为 0");
    }

    @Test
    @DisplayName("只能标在类上")
    void targetTypeOnly() {
        assertArrayEquals(new ElementType[]{ElementType.TYPE},
                FilePlatform.class.getAnnotation(Target.class).value(),
                "标注在方法/字段上对组件扫描无意义，编译期直接拒绝更省事");
    }

    @Test
    @DisplayName("必须可被子类继承")
    void inherited() {
        assertNotNull(FilePlatform.class.getAnnotation(Inherited.class),
                "@Inherited 决定了平台子类会沿用父类标识，见下方「继承」用例");
    }

    @Test
    @DisplayName("必须带上 @Documented")
    void documented() {
        assertNotNull(FilePlatform.class.getAnnotation(Documented.class));
    }

    @Test
    @DisplayName("本身就是 @Component，标注类自动进容器")
    void metaAnnotatedAsComponent() {
        assertNotNull(FilePlatform.class.getAnnotation(Component.class),
                "平台实现只写 @FilePlatform 就能被组件扫描收走，无需再写 @Service");
    }

    @Test
    @DisplayName("value() 返回标注时写下的平台标识")
    void valueIsTheKey() {
        assertEquals("TEST", TestFilePlatform.class.getAnnotation(FilePlatform.class).value());
    }

    @Test
    @DisplayName("未标注时读不到注解")
    void absentWhenNotAnnotated() {
        assertNull(UnannotatedPlatform.class.getAnnotation(FilePlatform.class),
                "未标注的类会被 FileHelper 直接跳过");
    }

    @Test
    @DisplayName("子类未标注时沿用父类标识")
    void inheritedKey() {
        FilePlatform annotation = InheritedKeyPlatform.class.getAnnotation(FilePlatform.class);
        assertNotNull(annotation, "@Inherited 生效时 getAnnotation 能返回父类的注解");
        assertEquals("TEST", annotation.value(),
                "⚠️ 已记录行为：平台子类不重复标注时会被算作同一个 key，注册时可能撞上「key 重复」");
    }

    @Test
    @DisplayName("子类自己标注时以子类的为准")
    void ownAnnotationWins() {
        FilePlatform annotation = OverriddenKeyPlatform.class.getAnnotation(FilePlatform.class);
        assertEquals("OVERRIDDEN", annotation.value(),
                "@Inherited 只在子类没有自己标注时兜底，不会把父类标识覆盖回去");
    }

    @Test
    @DisplayName("同一次取注解返回同一个对象")
    void annotationIsCached() {
        assertSame(TestFilePlatform.class.getAnnotation(FilePlatform.class),
                TestFilePlatform.class.getAnnotation(FilePlatform.class),
                "JDK 会缓存注解，注册循环里反复读取没有额外开销");
    }

    @Test
    @DisplayName("标识区分大小写")
    void keyIsCaseSensitive() {
        assertFalse("test".equals(TestFilePlatform.class.getAnnotation(FilePlatform.class).value()),
                "FileHelper 用 Map 按原样查找，getPlatform(\"test\") 取不到 TEST 平台");
    }

    @Test
    @DisplayName("内置平台的标识彼此不冲突")
    void builtinKeysAreDistinct() {
        assertEquals(3, Set.of(LOCAL_KEY, ALIYUN_KEY, TENCENT_KEY).size(),
                "三个内置平台标识必须两两不同，否则容器启动就会因 key 重复失败");
    }

    @Test
    @DisplayName("默认平台 LOCAL 必须与本地平台的标识一致")
    void defaultPlatformExists() {
        assertEquals(LOCAL_KEY, new FileConfig().getDefaultPlatform(),
                "FileConfig 的默认值必须与 LocalFileHelper 上的 @FilePlatform 值一致");
    }
}
