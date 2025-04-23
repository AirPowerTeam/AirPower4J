package cn.hamm.airpower.curd.export;

import cn.hamm.airpower.file.FileUtil;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.airpower.util.RandomUtil;
import cn.hamm.airpower.util.TaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.hamm.airpower.datetime.DateTimeFormatter.FULL_TIME;
import static cn.hamm.airpower.exception.ServiceError.DATA_NOT_FOUND;
import static cn.hamm.airpower.exception.ServiceError.SERVICE_ERROR;

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
     * 导出文件夹前缀
     */
    private static final String EXPORT_CACHE_PREFIX = EXPORT_DIR + "_";

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ExportConfig exportConfig;

    /**
     * 创建异步任务
     *
     * @param supplier 上报任务结果
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
        final String absolutePath = FileUtil.formatDirectory(exportConfig.getSaveFilePath());
        SERVICE_ERROR.when(!StringUtils.hasText(absolutePath), "导出失败，未配置导出文件目录");

        // 相对目录 默认为今天的文件夹
        String relativeDirectory = FileUtil.getTodayDirectory(EXPORT_DIR);

        // 存储的文件名
        final String fileName = FULL_TIME.formatCurrent().replaceAll(":", "") +
                "_" + RandomUtil.randomString() + FileUtil.EXTENSION_SEPARATOR + extension;

        try {
            FileUtil.saveFile(absolutePath + relativeDirectory, fileName, inputStream.readAllBytes());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return relativeDirectory + fileName;
    }
}
