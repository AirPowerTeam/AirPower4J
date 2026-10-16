package cn.hamm.airpower.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * <h1>文件模块配置单元测试</h1>
 *
 * <p>{@link FileConfig} 的每个字段都会被 {@code AbstractFilePlatformFactory} 直接拼进
 * 上传路径，所以这里的「边界」不是数值大小，而是<b>目录字符串的形状</b>：
 * 补不补尾斜杠、会不会被规范化、空值会退化成什么。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("文件模块配置单元测试")
class FileConfigTest {

    @Nested
    @DisplayName("默认值")
    class Defaults {

        @Test
        @DisplayName("上传大小上限应为 10MB")
        void uploadMaxSizeDefault() {
            assertEquals(1024L * 1024 * 10, new FileConfig().getUploadMaxSize(),
                    "默认上限是 10MB，下游按这个值判断「文件大小超出限制」");
        }

        @Test
        @DisplayName("上传根目录应为 upload 并补齐尾斜杠")
        void uploadDirectoryDefault() {
            assertEquals("upload/", new FileConfig().getUploadDirectory(),
                    "getUploadDirectory() 承诺以分隔符结尾，业务侧直接做字符串拼接");
        }

        @Test
        @DisplayName("默认存储平台应为 LOCAL")
        void defaultPlatformDefault() {
            assertEquals("LOCAL", new FileConfig().getDefaultPlatform(),
                    "默认值必须与 LocalFileHelper 上的 @FilePlatform(\"LOCAL\") 一致，否则启动即取不到平台");
        }
    }

    @Nested
    @DisplayName("上传根目录格式化")
    class UploadDirectoryFormat {

        @Test
        @DisplayName("缺少尾斜杠时补一个")
        void addsTrailingSeparator() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("data/file");
            assertEquals("data/file/", config.getUploadDirectory());
        }

        @Test
        @DisplayName("已有尾斜杠时不能重复追加")
        void keepsSingleTrailingSeparator() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("data/file/");
            assertEquals("data/file/", config.getUploadDirectory(),
                    "重复追加会让路径变成 data/file//，云端与本地对双斜杠的处理并不一致");
        }

        @Test
        @DisplayName("绝对路径同样补斜杠")
        void absolutePath() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("/data/upload");
            assertEquals("/data/upload/", config.getUploadDirectory());
        }

        @Test
        @DisplayName("配置成空串会退化成文件系统根目录")
        void emptyStringBecomesRoot() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("");
            assertEquals(File.separator, config.getUploadDirectory(),
                    "⚠️ 已记录缺陷：空串不是「配置缺失」而是「根目录」，"
                            + "文件会直接落到 " + File.separator + " 下，且没有任何告警");
        }

        @Test
        @DisplayName("不会做路径规范化，穿越写法原样保留")
        void noNormalization() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("upload/../etc");
            assertEquals("upload/../etc/", config.getUploadDirectory(),
                    "formatDirectory 只补斜杠，不解析 ..；防穿越的责任在 FileUtil.saveFile 与各平台的路径校验上");
        }

        @Test
        @DisplayName("无论怎么配，getter 拿到的永远是补过斜杠的值")
        void getterAlwaysFormats() {
            FileConfig config = new FileConfig();
            config.setUploadDirectory("data/file");
            assertEquals("data/file/", config.getUploadDirectory());
            assertEquals("data/file/", config.getUploadDirectory(),
                    "@Data 生成的 getter 被手写方法覆盖，读原始字段的入口被关掉了，调用方无法判断配置里到底写的什么");
        }
    }

    @Nested
    @DisplayName("配置项读写")
    class Properties {

        @Test
        @DisplayName("上传大小上限可覆盖为 0（等于禁止一切非空上传）")
        void zeroMaxSize() {
            FileConfig config = new FileConfig();
            config.setUploadMaxSize(0);
            assertEquals(0L, config.getUploadMaxSize());
        }

        @Test
        @DisplayName("上传大小上限可覆盖为负数，此时任何非空流都会被拦下")
        void negativeMaxSize() {
            FileConfig config = new FileConfig();
            config.setUploadMaxSize(-1);
            assertEquals(-1L, config.getUploadMaxSize());
            assertNotEquals(new FileConfig().getUploadMaxSize(), config.getUploadMaxSize(),
                    "上限没有下界校验，配错成负数不会在启动时被发现");
        }

        @Test
        @DisplayName("默认平台可改为空串")
        void defaultPlatformCanBeBlank() {
            FileConfig config = new FileConfig();
            config.setDefaultPlatform("");
            assertEquals("", config.getDefaultPlatform(),
                    "空默认平台不在启动时校验，直到第一次 getPlatform() 才报「暂未支持的文件存储平台 」");
        }
    }
}
