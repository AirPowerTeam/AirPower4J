package cn.hamm.airpower.file.fixture;

import cn.hamm.airpower.file.AbstractFilePlatformFactory;
import cn.hamm.airpower.file.FilePlatform;

import java.io.InputStream;

/**
 * <h2>测试用文件平台：与 {@link TestFilePlatform} 使用同一个标识</h2>
 *
 * <p>用来触发 {@code FileHelper} 的「key 重复」检查。</p>
 *
 * @author Hamm.cn
 */
@FilePlatform("TEST")
public class DuplicateKeyPlatform extends AbstractFilePlatformFactory {
    @Override
    public void save(InputStream inputStream, String directory, String fileName) {
        // 测试里无需落盘
    }
}
