package cn.hamm.airpower.web.export;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.core.RandomUtil;
import cn.hamm.airpower.core.TaskUtil;
import cn.hamm.airpower.web.file.FileConfig;
import cn.hamm.airpower.web.redis.RedisHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.hamm.airpower.core.enums.DateTimeFormatter.FULL_TIME;
import static cn.hamm.airpower.web.exception.ServiceError.DATA_NOT_FOUND;
import static cn.hamm.airpower.web.exception.ServiceError.SERVICE_ERROR;

/**
 * <h1>导出文件帮助类</h1>
 *
 * @author Hamm.cn
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
    private static final String EXPORT_CACHE_PREFIX = EXPORT_DIR + "_";

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private FileConfig fileConfig;

    /**
     * 保存 CSV 数据
     *
     * @param exportFile 导出文件
     * @param valueList  数据列表
     */
    public static void saveCsvListToFile(@NotNull ExportFile exportFile, List<String> valueList) {
        String rowString = String.join(CollectionUtil.CSV_ROW_DELIMITER, valueList);
        // 写入文件
        FileUtil.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), rowString + CollectionUtil.CSV_ROW_DELIMITER, StandardOpenOption.APPEND);
    }

    /**
     * 创建异步任务
     *
     * @param supplier 自行保存文件并返回路径
     * @return 文件编码
     */
    public final String createExportTask(Supplier<String> supplier) {
        String fileCode = RandomUtil.randomString().toLowerCase();
        final String fileCacheKey = EXPORT_CACHE_PREFIX + fileCode;
        Object object = redisHelper.get(fileCacheKey);
        if (Objects.nonNull(object)) {
            return createExportTask(supplier);
        }
        redisHelper.set(fileCacheKey, "");
        TaskUtil.runAsync(() -> redisHelper.set(fileCacheKey, supplier.get()));
        return fileCode;
    }

    /**
     * 获取导出文件 URL
     *
     * @param fileCode 文件编码
     * @return 文件 URL
     */
    public final String getExportFileUrl(String fileCode) {
        Object object = redisHelper.get(EXPORT_CACHE_PREFIX + fileCode);
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
        try {
            FileUtil.saveFile(exportFile.getAbsoluteDirectory(), exportFile.getFileName(), inputStream.readAllBytes());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return exportFile.getRelativeFile();
    }


    /**
     * 获取导出文件相对路径
     *
     * @param extension 文件后缀
     * @return 文件相对路径
     */
    public final @NotNull ExportFile getExportFilePath(String extension) {
        final String exportRootDirectory = fileConfig.getFileDirectory();
        SERVICE_ERROR.when(!StringUtils.hasText(exportRootDirectory), "导出失败，未配置导出文件目录");

        // 相对目录 默认为今天的文件夹
        String relativeDirectory = fileConfig.getExportDirectory() + FileUtil.getTodayDirectory();

        // 存储的文件名
        final String fileName = FULL_TIME.formatCurrent().replaceAll(":", "") +
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
         * 获取相对文件地址
         *
         * @return 相对文件地址
         */
        public String getRelativeFile() {
            return relativeDirectory + fileName;
        }
    }
}
