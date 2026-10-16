package cn.hamm.airpower.file.platform.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <h1>本地存储配置单元测试</h1>
 *
 * <p>该值会被 {@code LocalFileHelper} 直接拼在待保存路径前面，
 * 并作为 {@code delete} 的越权校验基准，末尾斜杠缺失会让「根目录」判定整体偏移。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("本地存储配置单元测试")
class LocalFileConfigTest {

    @Test
    @DisplayName("默认目录为 /home/static/")
    void defaultDirectory() {
        assertEquals("/home/static/", new LocalFileConfig().getLocalAbsoluteDirectory(),
                "生产默认落在 Web 容器常用的静态资源目录");
    }

    @Test
    @DisplayName("缺少尾斜杠时补一个")
    void addsTrailingSeparator() {
        LocalFileConfig config = new LocalFileConfig();
        config.setLocalAbsoluteDirectory("/data/static");
        assertEquals("/data/static/", config.getLocalAbsoluteDirectory());
    }

    @Test
    @DisplayName("已有尾斜杠时不重复追加")
    void keepsSingleTrailingSeparator() {
        LocalFileConfig config = new LocalFileConfig();
        config.setLocalAbsoluteDirectory("/data/static/");
        assertEquals("/data/static/", config.getLocalAbsoluteDirectory(),
                "多一个斜杠不会报错，但 delete 的根目录比较会多出一层无意义的路径");
    }

    @Test
    @DisplayName("配置成空串会退化成文件系统根目录")
    void emptyStringBecomesRoot() {
        LocalFileConfig config = new LocalFileConfig();
        config.setLocalAbsoluteDirectory("");
        assertEquals(File.separator, config.getLocalAbsoluteDirectory(),
                "⚠️ 已记录缺陷：空串让存储根目录变成 " + File.separator
                        + "，delete 的越权校验会以系统根目录为基准，形同虚设");
    }

    @Test
    @DisplayName("不会做路径规范化")
    void noNormalization() {
        LocalFileConfig config = new LocalFileConfig();
        config.setLocalAbsoluteDirectory("/data/static/../static");
        assertEquals("/data/static/../static/", config.getLocalAbsoluteDirectory(),
                "格式化只补斜杠；delete 内部才调用 toAbsolutePath().normalize()");
    }
}
