package cn.hamm.airpower.file.fixture;

import cn.hamm.airpower.file.FilePlatform;

/**
 * <h2>测试用文件平台：子类自己重新标注平台标识</h2>
 *
 * <p>用于钉住「子类上直接标注的注解，优先于从父类继承来的注解」，
 * 即 {@code @Inherited} 只在子类<b>没有</b>自己标注时才生效。</p>
 *
 * @author Hamm.cn
 */
@FilePlatform("OVERRIDDEN")
public class OverriddenKeyPlatform extends TestFilePlatform {
}
