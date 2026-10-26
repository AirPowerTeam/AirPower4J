package cn.hamm.airpower.file.fixture;

import cn.hamm.airpower.file.AbstractFilePlatformFactory;

import java.io.InputStream;

/**
 * <h2>测试用文件平台：没有标注 {@code @FilePlatform}</h2>
 *
 * <p>{@code FileHelper} 会直接跳过它；{@code getKey()} 则会明确抛异常。</p>
 *
 * @author Hamm.cn
 */
public class UnannotatedPlatform extends AbstractFilePlatformFactory {
    @Override
    protected void save(InputStream inputStream, String directory, String fileName) {
        // 测试里无需落盘
    }
}
