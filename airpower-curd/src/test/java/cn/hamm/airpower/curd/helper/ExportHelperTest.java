package cn.hamm.airpower.curd.helper;

import cn.hamm.airpower.core.CollectionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>导出文件帮助类单元测试</h1>
 *
 * <p>只覆盖 {@link ExportHelper#saveCsvListToFile}：它是 CSV 落地磁盘的唯一入口，
 * 且被分页导出按页反复调用，因此「首次写入」与「后续追加」的行为必须分别被钉住。</p>
 *
 * <p>两个曾经真实存在的缺陷都由本测试守住：</p>
 * <ul>
 *     <li>只传 {@code APPEND} 而不传 {@code CREATE} 时，NIO 不会创建文件，第一次写表头就抛
 *     {@link java.nio.file.NoSuchFileException}，导出 100% 失败</li>
 *     <li>CSV 不带 UTF-8 BOM 时，Excel 在 Windows 上退回系统 ANSI 代码页解码，中文全部乱码</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("导出文件帮助类单元测试")
class ExportHelperTest {

    /**
     * 测试用临时目录
     */
    @TempDir
    Path tempDir;

    /**
     * 构造一个指向临时目录的导出文件描述
     *
     * @param fileName 文件名
     * @return 导出文件描述
     */
    private ExportHelper.ExportFile exportFileOf(String fileName) {
        return new ExportHelper.ExportFile()
                .setExportRootDirectory(tempDir.toString() + "/")
                .setRelativeDirectory("20261001/")
                .setFileName(fileName);
    }

    /**
     * 读取文件全部字节
     *
     * @param path 文件路径
     * @return 文件字节
     * @throws IOException 读取异常
     */
    private static byte[] readBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    /**
     * 统计文本中 BOM 字符出现的次数
     *
     * @param text 文本
     * @return 出现次数
     */
    private static int countBom(String text) {
        return text.split(CollectionUtil.UTF8_BOM, -1).length - 1;
    }

    @Nested
    @DisplayName("saveCsvListToFile 保存 CSV 数据")
    class SaveCsvListToFileTest {

        @Test
        @DisplayName("首次写入应创建文件（只传 APPEND 不会创建，导出原本必然失败）")
        void createsFileOnFirstWrite() throws IOException {
            ExportHelper.ExportFile exportFile = exportFileOf("first.csv");

            assertDoesNotThrow(() -> ExportHelper.saveCsvListToFile(exportFile, List.of("表头A,表头B")),
                    "首次写入必须能创建文件");

            assertTrue(Files.exists(exportFile.getAbsoluteFile()),
                    "导出文件应真实落盘，否则客户端永远拿不到地址");
        }

        @Test
        @DisplayName("文件应以 UTF-8 BOM 开头，Excel 才不会用系统代码页解码")
        void startsWithUtf8Bom() throws IOException {
            ExportHelper.ExportFile exportFile = exportFileOf("bom.csv");
            ExportHelper.saveCsvListToFile(exportFile, List.of("表头A,表头B"));

            byte[] raw = readBytes(exportFile.getAbsoluteFile());
            assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                    java.util.Arrays.copyOf(raw, 3), "前三字节必须是 UTF-8 BOM（EF BB BF）");
        }

        @Test
        @DisplayName("分页追加时 BOM 只写一次，不能在文件中间重复插入")
        void bomWrittenOnlyOnFirstWrite() throws IOException {
            ExportHelper.ExportFile exportFile = exportFileOf("paged.csv");
            // 复刻 CurdService.createExportTask：先写表头，再逐页追加数据
            ExportHelper.saveCsvListToFile(exportFile, List.of("表头A,表头B"));
            ExportHelper.saveCsvListToFile(exportFile, List.of("1,张三"));
            ExportHelper.saveCsvListToFile(exportFile, List.of("2,李四"));

            String text = new String(readBytes(exportFile.getAbsoluteFile()), StandardCharsets.UTF_8);
            assertEquals(1, countBom(text), "全文只能有一个 BOM，多页追加会把它插到文件中间");
            assertEquals(CollectionUtil.UTF8_BOM + "表头A,表头B\n1,张三\n2,李四\n", text,
                    "应为 BOM + 表头 + 两页数据，页与页之间不丢行也不多行");
        }

        @Test
        @DisplayName("不同导出文件互不影响，各自都带 BOM")
        void separateFilesEachGetBom() throws IOException {
            ExportHelper.ExportFile first = exportFileOf("a.csv");
            ExportHelper.ExportFile second = exportFileOf("b.csv");
            ExportHelper.saveCsvListToFile(first, List.of("表头A"));
            ExportHelper.saveCsvListToFile(second, List.of("表头B"));

            assertEquals(1, countBom(new String(readBytes(first.getAbsoluteFile()), StandardCharsets.UTF_8)),
                    "第一个文件应带 BOM");
            assertEquals(1, countBom(new String(readBytes(second.getAbsoluteFile()), StandardCharsets.UTF_8)),
                    "第二个文件应各自带 BOM，而不是继承第一个文件的状态");
        }

        @Test
        @DisplayName("空数据列表不应让文件消失，也不应重复写入 BOM")
        void emptyValueList() throws IOException {
            ExportHelper.ExportFile exportFile = exportFileOf("empty.csv");
            ExportHelper.saveCsvListToFile(exportFile, List.of("表头A"));
            ExportHelper.saveCsvListToFile(exportFile, List.of());

            String text = new String(readBytes(exportFile.getAbsoluteFile()), StandardCharsets.UTF_8);
            assertEquals(CollectionUtil.UTF8_BOM + "表头A\n\n", text,
                    "空数据页只应产生一个空行，且不重复追加 BOM");
        }
    }
}
