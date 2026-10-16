package cn.hamm.airpower.api.export;

import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.redis.RedisHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>导出文件帮助类单元测试</h2>
 *
 * <p>本类盯的是 CSV 导出的两个「看起来差不多、后果差很远」的细节：</p>
 * <ul>
 *     <li><b>UTF-8 BOM 只能有且仅有一处</b>。写在文件头 Excel 才能正确识别编码；
 *     写在文件中间会在表格里插出一个不可见字符，把数据行撑出空行</li>
 *     <li><b>追加写入必须带 CREATE</b>。NIO 的 {@code APPEND} 自身不蕴含
 *     「不存在则创建」，只传 {@code APPEND} 时第一次写表头就会
 *     {@code NoSuchFileException}</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("导出文件帮助类单元测试")
class ExportHelperTest {

    /**
     * CSV 行分隔符
     */
    private static final String ROW = "\n";

    /**
     * UTF-8 BOM
     */
    private static final String BOM = "﻿";

    /**
     * 等待后台线程写 Redis 的超时时间（毫秒）
     */
    private static final long WRITE_TIMEOUT = 3000L;

    @TempDir
    Path tempDir;

    private ExportHelper exportHelper;
    private RedisHelper redisHelper;

    @BeforeEach
    void setUp() {
        exportHelper = new ExportHelper();
        redisHelper = Mockito.mock(RedisHelper.class);
        inject(redisHelper, "redisHelper");
        ExportConfig exportConfig = new ExportConfig();
        exportConfig.setExportPath(tempDir.toString());
        inject(exportConfig, "exportConfig");
    }

    /**
     * 反射注入私有字段
     *
     * @param value 值
     */
    private void inject(Object value, String name) {
        try {
            Field field = ExportHelper.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(exportHelper, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入字段失败: " + name, e);
        }
    }

    /**
     * 构造一个落在临时目录里的导出文件
     *
     * @param fileName 文件名
     * @return 导出文件
     */
    private ExportHelper.ExportFile fileOf(String fileName) {
        return new ExportHelper.ExportFile()
                .setExportRootDirectory(tempDir.toString())
                .setRelativeDirectory("")
                .setFileName(fileName);
    }

    /**
     * 读出文件内容
     *
     * @param exportFile 导出文件
     * @return 内容
     */
    private String read(ExportHelper.ExportFile exportFile) {
        try {
            return Files.readString(exportFile.getAbsoluteFile(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取导出文件失败", e);
        }
    }

    @Nested
    @DisplayName("1. CSV 写入")
    class CsvWriting {

        @Test
        @DisplayName("首次写入要带 UTF-8 BOM")
        void firstWriteAddsBom() {
            // 没有 BOM 时 Excel 会用系统 ANSI 代码页解码，中文全部乱码
            ExportHelper.ExportFile file = fileOf("first.csv");
            ExportHelper.saveCsvListToFile(file, List.of("a,b,c"));
            String content = read(file);
            assertTrue(content.startsWith(BOM), "首写缺少 BOM: " + content);
        }

        @Test
        @DisplayName("后续追加不得再写 BOM")
        void laterWritesDoNotAddBom() {
            // 每页都补 BOM 会在文件中间插入不可见字符，把表格撑出空行
            ExportHelper.ExportFile file = fileOf("paged.csv");
            ExportHelper.saveCsvListToFile(file, List.of("a,b,c"));
            ExportHelper.saveCsvListToFile(file, List.of("1,2,3"));

            String content = read(file);
            assertEquals(1, countBom(content), "BOM 出现了多次: " + content.length() + " 字符");
        }

        @Test
        @DisplayName("追加的内容接在上一次之后")
        void contentAppendedInOrder() {
            ExportHelper.ExportFile file = fileOf("ordered.csv");
            ExportHelper.saveCsvListToFile(file, List.of("头"));
            ExportHelper.saveCsvListToFile(file, List.of("第二页"));
            ExportHelper.saveCsvListToFile(file, List.of("第三页"));
            assertEquals(BOM + "头" + ROW + "第二页" + ROW + "第三页" + ROW, read(file));
        }

        @Test
        @DisplayName("每行末尾都补换行符")
        void everyRowTerminated() {
            ExportHelper.ExportFile file = fileOf("rows.csv");
            ExportHelper.saveCsvListToFile(file, List.of("a", "b", "c"));
            String content = read(file);
            assertEquals(BOM + "a" + ROW + "b" + ROW + "c" + ROW, content);
        }

        @Test
        @DisplayName("传入空列表时只留下 BOM，不报错")
        void emptyListStillCreatesFile() {
            ExportHelper.ExportFile file = fileOf("empty.csv");
            assertDoesNotThrow(() -> ExportHelper.saveCsvListToFile(file, List.of()));
            assertEquals(BOM, read(file));
            assertTrue(Files.exists(file.getAbsoluteFile()), "文件应被创建");
        }

        @Test
        @DisplayName("空列表后追加数据不会再补 BOM")
        void bomAddedOnlyOnceEvenAfterEmptyWrite() {
            // 边界：第一次调用写的是空列表，文件已存在，
            // 第二次追加时必须判定为「非首写」
            ExportHelper.ExportFile file = fileOf("edge.csv");
            ExportHelper.saveCsvListToFile(file, List.of());
            ExportHelper.saveCsvListToFile(file, List.of("数据"));
            assertEquals(1, countBom(read(file)));
        }

        @Test
        @DisplayName("内容中的换行与中文原样保留")
        void contentPreservedVerbatim() {
            ExportHelper.ExportFile file = fileOf("cjk.csv");
            ExportHelper.saveCsvListToFile(file, List.of("姓名,备注", "张三,\"含,逗号\""));
            String content = read(file);
            assertTrue(content.contains("张三"), content);
            assertTrue(content.contains("含,逗号"), content);
        }

        /**
         * 统计 BOM 出现次数
         *
         * @param content 内容
         * @return 次数
         */
        private int countBom(String content) {
            int count = 0;
            int index = content.indexOf(BOM);
            while (index >= 0) {
                count++;
                index = content.indexOf(BOM, index + BOM.length());
            }
            return count;
        }
    }

    @Nested
    @DisplayName("2. 导出文件路径")
    class ExportFilePath {

        @Test
        @DisplayName("未配置导出目录时报错")
        void missingExportPathRejected() {
            // 目录为空时直接写文件会写到工作目录，把数据落到意料之外的位置
            inject(new ExportConfig(), "exportConfig");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> exportHelper.getExportFilePath("csv"));
            assertEquals(cn.hamm.airpower.exception.Errors.SERVICE_ERROR.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("导出目录为纯空白时同样报错")
        void blankExportPathRejected() {
            ExportConfig config = new ExportConfig();
            config.setExportPath("    ");
            inject(config, "exportConfig");
            assertThrows(ServiceException.class, () -> exportHelper.getExportFilePath("csv"));
        }

        @Test
        @DisplayName("文件名带时间戳与随机串，后缀按传入值")
        void fileNameShape() {
            ExportHelper.ExportFile file = exportHelper.getExportFilePath("csv");
            assertTrue(file.getFileName().endsWith(".csv"), file.getFileName());
            // 文件名里不能带冒号：Windows 上非法，且多数 FTP 也不接受
            assertTrue(file.getFileName().indexOf(':') < 0, file.getFileName());
        }

        @Test
        @DisplayName("同一时刻的两次生成文件名不同")
        void fileNamesDiffer() {
            // 文件名相同会互相覆盖，两个并发导出任务的数据会串在一起
            assertNotEquals(exportHelper.getExportFilePath("csv").getFileName(),
                    exportHelper.getExportFilePath("csv").getFileName());
        }

        @Test
        @DisplayName("绝对路径由「根目录 + 日期目录 + 文件名」拼成")
        void absolutePathComposition() {
            ExportHelper.ExportFile file = exportHelper.getExportFilePath("csv");
            assertTrue(file.getAbsoluteDirectory().startsWith(tempDir.toString()),
                    "绝对目录应落在根目录下: " + file.getAbsoluteDirectory());
            assertTrue(file.getAbsoluteFile().toString().endsWith(file.getFileName()),
                    file.getAbsoluteFile().toString());
        }

        @Test
        @DisplayName("导出目录不带尾斜杠时，日期目录仍正确落在其下")
        void missingTrailingSlashStillCorrect() {
            // 修复前是裸字符串拼接 `根目录 + 相对目录`，
            // 配置 `export-path: /data/export`（不带尾斜杠，最自然的写法）
            // 会得到 `/data/export20261016`——文件落到了配置目录**之外**，
            // 挂在这个目录上的磁盘配额与按目录清理任务一并失效。
            // 现在改用 Paths，两种写法都得到 /data/export/20261016
            ExportHelper.ExportFile noSlash = new ExportHelper.ExportFile()
                    .setExportRootDirectory("/data/export")
                    .setRelativeDirectory("20261016/")
                    .setFileName("x.csv");
            assertEquals("/data/export/20261016/x.csv", noSlash.getAbsoluteFile().toString());
            assertEquals("/data/export/20261016", noSlash.getAbsoluteDirectory());
        }

        @Test
        @DisplayName("导出目录带尾斜杠时结果一致")
        void trailingSlashGivesSameResult() {
            ExportHelper.ExportFile withSlash = new ExportHelper.ExportFile()
                    .setExportRootDirectory("/data/export/")
                    .setRelativeDirectory("20261016/")
                    .setFileName("x.csv");
            assertEquals("/data/export/20261016/x.csv", withSlash.getAbsoluteFile().toString(),
                    "带不带尾斜杠都应得到同一个路径");
        }

        @Test
        @DisplayName("相对目录为空时只得到根目录本身")
        void emptyRelativeDirectory() {
            // 不能因此拼出一个多余的层级
            ExportHelper.ExportFile file = new ExportHelper.ExportFile()
                    .setExportRootDirectory("/data/export")
                    .setRelativeDirectory("")
                    .setFileName("x.csv");
            assertEquals("/data/export/x.csv", file.getAbsoluteFile().toString());
        }

        @Test
        @DisplayName("多级相对目录也能正确拼接")
        void nestedRelativeDirectory() {
            ExportHelper.ExportFile file = new ExportHelper.ExportFile()
                    .setExportRootDirectory("/data/export")
                    .setRelativeDirectory("2026/10/16/")
                    .setFileName("x.csv");
            assertEquals("/data/export/2026/10/16/x.csv", file.getAbsoluteFile().toString());
        }

        @Test
        @DisplayName("相对路径不受影响，仍是正确的「日期/文件名」")
        void relativeFileIsStillCorrect() {
            // 存进 Redis 的是相对路径，客户端拿它拼下载地址。
            // 这里必须恒为 `/` 分隔，因此刻意不用 Paths（Windows 上会输出 `\`）
            ExportHelper.ExportFile file = new ExportHelper.ExportFile()
                    .setExportRootDirectory("/data/export")
                    .setRelativeDirectory("20261016/")
                    .setFileName("x.csv");
            assertEquals("20261016/x.csv", file.getRelativeFile());
        }

        @Test
        @DisplayName("相对路径不含根目录")
        void relativeFileExcludesRoot() {
            // 存进 Redis 的是相对路径：换部署目录不影响已生成的任务记录
            ExportHelper.ExportFile file = exportHelper.getExportFilePath("csv");
            assertTrue(file.getRelativeFile().endsWith(file.getFileName()));
            assertTrue(!file.getRelativeFile().contains(tempDir.toString()), file.getRelativeFile());
        }
    }

    @Nested
    @DisplayName("3. 导出任务状态")
    class ExportTask {

        @Test
        @DisplayName("创建任务立即返回文件编码")
        void createReturnsCodeImmediately() {
            // 真正的导出在后台跑，接口不能等
            String code = exportHelper.createExportTask(() -> "20240101/x.csv");
            assertEquals(32, code.length(), "应为去掉横线的 UUID: " + code);
            assertTrue(code.matches("[0-9a-f]{32}"), code);
        }

        @Test
        @DisplayName("文件编码在 Redis 里的键带上 export:task: 前缀")
        void redisKeyHasNamespacePrefix() {
            exportHelper.createExportTask(() -> "x.csv");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            // 用带超时的 verify：真正的写入发生在后台线程上，
            // 立刻 verify 会偶发失败——那不是缺陷，是这条链路的固有异步性
            Mockito.verify(redisHelper, Mockito.timeout(WRITE_TIMEOUT))
                    .set(captor.capture(), Mockito.any());
            assertTrue(captor.getValue().startsWith("export:task:"), captor.getValue());
        }

        @Test
        @DisplayName("文件编码撞车时重新生成")
        void codeCollisionRetries() {
            // 撞车概率极低，但一旦撞上两个任务会共用一个键，
            // A 的文件地址被 B 覆盖，客户端拿到错的数据
            Mockito.when(redisHelper.get(Mockito.anyString()))
                    .thenReturn("已被占用").thenReturn(null);
            String code = exportHelper.createExportTask(() -> "x.csv");
            assertEquals(32, code.length());
            Mockito.verify(redisHelper, Mockito.times(2)).get(Mockito.anyString());
        }

        @Test
        @DisplayName("任务未完成时查询结果提示稍后重试")
        void pendingTaskTellsClientToRetry() {
            // 后台线程还没写回时 Redis 里是空串，前端据此轮询
            Mockito.when(redisHelper.get(Mockito.anyString())).thenReturn("");
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> exportHelper.getExportFileUrl("code"));
            assertEquals(cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("查询一个不存在的文件编码按「未找到」处理")
        void unknownCodeRejected() {
            Mockito.when(redisHelper.get(Mockito.anyString())).thenReturn(null);
            assertThrows(ServiceException.class, () -> exportHelper.getExportFileUrl("nope"));
        }

        @Test
        @DisplayName("任务完成后返回文件相对路径")
        void finishedTaskReturnsPath() {
            Mockito.when(redisHelper.get(Mockito.anyString())).thenReturn("20240101/demo.csv");
            assertEquals("20240101/demo.csv", exportHelper.getExportFileUrl("code"));
        }

        @Test
        @DisplayName("写入与读取用的是同一个键")
        void lookupKeyMatchesCreateKey() {
            // 两处键必须完全一致，否则创建时写进去的文件地址永远查不出来，
            // 客户端只能一直轮询到超时
            String code = exportHelper.createExportTask(() -> "x.csv");
            ArgumentCaptor<String> createKey = ArgumentCaptor.forClass(String.class);
            Mockito.verify(redisHelper, Mockito.timeout(WRITE_TIMEOUT))
                    .set(createKey.capture(), Mockito.any());
            assertEquals("export:task:" + code, createKey.getValue());

            Mockito.reset(redisHelper);
            Mockito.when(redisHelper.get(Mockito.anyString())).thenReturn("done.csv");
            exportHelper.getExportFileUrl(code);
            ArgumentCaptor<String> lookupKey = ArgumentCaptor.forClass(String.class);
            Mockito.verify(redisHelper).get(lookupKey.capture());
            assertEquals(createKey.getValue(), lookupKey.getValue(),
                    "创建与查询用的键必须一致，否则客户端永远取不到文件");
        }
    }

    @Nested
    @DisplayName("4. 流式保存")
    class StreamSaving {

        @AfterEach
        void cleanUp() {
            Mockito.reset(redisHelper);
        }

        @Test
        @DisplayName("保存文件流后返回相对路径且文件确实落盘")
        void streamSaved() throws Exception {
            String relative = exportHelper.saveExportFileStream(
                    new java.io.ByteArrayInputStream("a,b".getBytes(StandardCharsets.UTF_8)));
            assertTrue(relative.endsWith(".csv"), relative);
            Path written = tempDir.resolve(relative);
            assertTrue(Files.exists(written), "文件未落盘: " + written);
            assertEquals("a,b", Files.readString(written, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("导出根目录不带尾斜杠时文件仍落在配置目录之下")
        void writeLandsUnderConfiguredRoot() throws Exception {
            // 修复前：配置 `/tmp/xxx` 写出来的文件在 `/tmp/xxx20261016/`，
            // 也就是跑到了配置目录外面；修复后必须落在 `/tmp/xxx/<日期>/` 下
            ExportConfig config = new ExportConfig();
            config.setExportPath(tempDir.toString());
            inject(config, "exportConfig");

            String relative = exportHelper.saveExportFileStream(
                    new java.io.ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
            Path expected = tempDir.resolve(relative);
            assertTrue(Files.exists(expected), "文件未落在配置目录之下: " + expected);
            assertTrue(expected.startsWith(tempDir), "文件跑到了配置目录之外: " + expected);
        }

        @Test
        @DisplayName("后缀可以自定义")
        void customExtension() throws Exception {
            String relative = exportHelper.saveExportFileStream(
                    new java.io.ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "txt");
            assertTrue(relative.endsWith(".txt"), relative);
            assertTrue(Files.exists(tempDir.resolve(relative)), relative);
        }

        @Test
        @DisplayName("流式写入不会给内容加 BOM")
        void streamWriteHasNoBom() throws Exception {
            // BOM 只在 CSV 表格场景需要；流式保存是原样落盘，
            // 多加一个不可见字符会破坏调用方自己的文件格式
            String relative = exportHelper.saveExportFileStream(
                    new java.io.ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8)), "txt");
            String content = Files.readString(tempDir.resolve(relative), StandardCharsets.UTF_8);
            assertEquals("raw", content);
        }
    }
}
