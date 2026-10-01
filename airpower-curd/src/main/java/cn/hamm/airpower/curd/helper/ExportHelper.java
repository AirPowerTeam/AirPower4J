package cn.hamm.airpower.curd.helper;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.core.RandomUtil;
import cn.hamm.airpower.core.TaskUtil;
import cn.hamm.airpower.curd.config.ExportConfig;
import cn.hamm.airpower.redis.RedisHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static cn.hamm.airpower.core.enums.DateTimeFormatter.FULL_TIME;
import static cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND;
import static cn.hamm.airpower.exception.Errors.SERVICE_ERROR;

/**
 * <h1>导出文件帮助类</h1>
 *
 * @author Hamm.cn
 * @apiNote 采用「先占位、后台写盘、写完把文件地址写回 Redis」的两段式：创建接口立即返回
 * 文件编码，客户端轮询该编码取地址，避免长时间请求占用连接
 */
@Component
@Slf4j
public class ExportHelper {
    /**
     * 导出文件夹
     */
    private static final String EXPORT_DIR = "export";

    /**
     * 导出文件前缀
     */
    private static final String EXPORT_TASK_KEY_PREFIX = EXPORT_DIR + ":task:";

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ExportConfig exportConfig;

    /**
     * 保存 CSV 数据
     *
     * @param exportFile 导出文件
     * @param valueList  数据列表
     * @apiNote 以追加方式写入，便于分页导出时多次追加；表头行由调用方先写一次。
     * <b>只有首次写入（文件尚不存在，即写表头那一行）会前置 UTF-8 BOM</b>，
     * 后续分页在文件尾部追加，若每页都补 BOM 会在文件中间插入不可见字符，把表格撑出空行
     */
    public static void saveCsvListToFile(@NotNull ExportFile exportFile, List<String> valueList) {
        // 预估总长直接建 StringBuilder：String.join 的结果若再拼一个换行，
        // 会把整页内容在堆里再复制一份
        int rows = valueList.size();
        int total = CollectionUtil.CSV_ROW_DELIMITER.length() * Math.max(rows, 1);
        for (String row : valueList) {
            total += row.length();
        }
        StringBuilder rowString = new StringBuilder(total);
        for (String row : valueList) {
            rowString.append(row).append(CollectionUtil.CSV_ROW_DELIMITER);
        }
        // 首次写入才补 BOM：Excel 靠 BOM 判定编码，没有它会用系统 ANSI 代码页解码，中文全乱码
        if (Files.notExists(exportFile.getAbsoluteFile())) {
            rowString.insert(0, CollectionUtil.UTF8_BOM);
        }
        // CREATE 必须显式带上：NIO 的 APPEND 自身不蕴含「不存在则创建」，
        // 只传 APPEND 时第一次写表头就会抛 NoSuchFileException，导出直接失败
        FileUtil.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), rowString.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * 创建异步任务
     *
     * @param supplier 自行保存文件并返回路径
     * @return 文件编码
     * @apiNote 先用空串占位再后台执行，客户端提前轮询到空值说明还没写完；
     * 随机串撞车时递归重试
     */
    public final String createExportTask(Supplier<String> supplier) {
        String fileCode = UUID.randomUUID().toString().replace("-", "");
        final String fileCacheKey = EXPORT_TASK_KEY_PREFIX + fileCode;
        Object object = redisHelper.get(fileCacheKey);
        if (Objects.nonNull(object)) {
            return createExportTask(supplier);
        }
        redisHelper.set(fileCacheKey, "");
        TaskUtil.run(() -> redisHelper.set(fileCacheKey, supplier.get()));
        return fileCode;
    }

    /**
     * 获取导出文件 URL
     *
     * @param fileCode 文件编码
     * @return 文件 URL
     * @apiNote 任务未完成时 Redis 中的值为空串，抛「文件暂未准备完毕」由客户端重试
     */
    public final String getExportFileUrl(String fileCode) {
        Object object = redisHelper.get(EXPORT_TASK_KEY_PREFIX + fileCode);
        DATA_NOT_FOUND.whenEmpty(object, "文件暂未准备完毕");
        return object.toString();
    }

    /**
     * 保存导出文件流为 CSV
     *
     * @param inputStream 文件流
     * @return 保存后的文件名
     */
    public final @NotNull String saveExportFileStream(InputStream inputStream) {
        return saveExportFileStream(inputStream, "csv");
    }

    /**
     * 保存导出文件流为 CSV
     *
     * @param inputStream 文件流
     * @param extension   文件后缀
     * @return 保存后的文件名
     */
    public final @NotNull String saveExportFileStream(@NotNull InputStream inputStream, String extension) {
        ExportFile exportFile = getExportFilePath(extension);
        // 不用 readAllBytes()：那会把整个流读成一个 byte[] 常驻堆里，
        // FileUtil 的流式重载用固定 8KB 缓冲边读边落盘，堆占用与文件大小无关。
        // 写盘失败由 FileUtil 统一包成 ServiceException（消息里带异常类型），这里不再重复包装
        FileUtil.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), inputStream);
        return exportFile.getRelativeFile();
    }

    /**
     * 获取导出文件相对路径
     *
     * @param extension 文件后缀
     * @return 文件相对路径
     * @apiNote 文件名带完整时间戳加随机串，文件落在「日期/文件名」的相对目录下，
     * 便于按天清理
     */
    public final @NotNull ExportFile getExportFilePath(String extension) {
        final String exportRootDirectory = exportConfig.getExportPath();
        SERVICE_ERROR.when(!StringUtils.hasText(exportRootDirectory), "导出失败，未配置导出文件目录");

        // 相对目录 默认为今天的文件夹
        String relativeDirectory = FileUtil.getTodayDirectory();

        // 存储的文件名
        final String fileName = FULL_TIME.formatCurrent().replace(":", "") +
                "_" + RandomUtil.randomString() + FileUtil.EXTENSION_SEPARATOR + extension;

        return new ExportFile()
                .setExportRootDirectory(exportRootDirectory)
                .setRelativeDirectory(relativeDirectory)
                .setFileName(fileName);
    }

    @Setter
    @Accessors(chain = true)
    public static class ExportFile {
        /**
         * 导出根目录
         */
        private String exportRootDirectory;

        /**
         * 文件名
         */
        @Getter
        private String fileName;

        /**
         * 相对目录
         */
        private String relativeDirectory;

        /**
         * 获取绝对目录
         *
         * @return 绝对目录
         */
        public String getAbsoluteDirectory() {
            return exportRootDirectory + relativeDirectory;
        }

        /**
         * 获取导出文件的绝对路径
         *
         * @return 绝对文件路径
         * @apiNote 供写入方判断「是否首次写入」，从而不重复追加 CSV 的 UTF-8 BOM
         */
        public Path getAbsoluteFile() {
            return Paths.get(getAbsoluteDirectory(), getFileName());
        }

        /**
         * 获取相对文件地址
         *
         * @return 相对文件地址
         * @apiNote 存 Redis 的是这个相对路径，不含根目录，换部署目录不影响已生成的地址
         */
        public String getRelativeFile() {
            return relativeDirectory + fileName;
        }
    }
}
