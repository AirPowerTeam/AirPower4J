package cn.hamm.airpower.file.fixture;

/**
 * <h2>测试用文件平台：自身不标注解，靠 {@code @Inherited} 继承父类的平台标识</h2>
 *
 * <p>{@link cn.hamm.airpower.file.FilePlatform} 标注了 {@code @Inherited}，
 * 子类即使不重复标注，{@code getClass().getAnnotation()} 也会返回父类的注解。
 * 这类子类在 {@code FileHelper} 注册时会被算成<b>和父类同一个 key</b>。</p>
 *
 * @author Hamm.cn
 */
public class InheritedKeyPlatform extends TestFilePlatform {
}
