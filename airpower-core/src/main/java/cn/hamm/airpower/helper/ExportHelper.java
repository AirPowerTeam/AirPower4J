package cn.hamm.airpower.helper;

import cn.hamm.airpower.config.Constant;
import cn.hamm.airpower.config.ServiceConfig;
import cn.hamm.airpower.util.DateTimeUtil;
import cn.hamm.airpower.util.FileUtil;
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

import static cn.hamm.airpower.config.Constant.*;
import static cn.hamm.airpower.enums.DateTimeFormatter.FULL_TIME;
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
    private static final String EXPORT_DIR = "export";
    /**
     * <h3>导出文件夹前缀</h3>
     */
    private static final String EXPORT_CACHE_PREFIX = EXPORT_DIR + STRING_UNDERLINE;
    /**
     * <h3>导出文件后缀</h3>
     */
    private static final String EXPORT_FILE_CSV = ".csv";

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ServiceConfig serviceConfig;

    /**
     * <h3>创建异步任务</h3>
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
        redisHelper.set(fileCacheKey, Constant.STRING_EMPTY);
        TaskUtil.runAsync(() -> redisHelper.set(fileCacheKey, supplier.get()));
        return fileCode;
    }

    /**
     * <h3>获取导出文件URL</h3>
     *
     * @param fileCode 文件编码
     * @return 文件URL
     */
    public final String getExportFileUrl(String fileCode) {
        Object object = redisHelper.get(EXPORT_CACHE_PREFIX + fileCode);
        DATA_NOT_FOUND.whenNull(object, "错误的FileCode");
        DATA_NOT_FOUND.whenEmpty(object, "文件暂未准备完毕");
        return object.toString();
    }

    /**
     * <h3>保存导出文件流为CSV</h3>
     *
     * @param inputStream 文件流
     * @return 保存后的文件名
     */
    public final @NotNull String saveExportFileStream(InputStream inputStream) {
        return saveExportFileStream(inputStream, EXPORT_FILE_CSV);
    }

    /**
     * <h3>保存导出文件流为CSV</h3>
     *
     * @param inputStream 文件流
     * @param suffix      文件后缀
     * @return 保存后的文件名
     */
    public final @NotNull String saveExportFileStream(@NotNull InputStream inputStream, String suffix) {
        final String absolutePath = FileUtil.formatDirectory(serviceConfig.getSaveFilePath());
        SERVICE_ERROR.when(!StringUtils.hasText(absolutePath), "导出失败，未配置导出文件目录");

        // 相对目录 默认为今天的文件夹
        String relativeDirectory = FileUtil.getTodayDirectory(EXPORT_DIR);

        // 存储的文件名
        final String fileName = DateTimeUtil.format(System.currentTimeMillis(),
                FULL_TIME.getValue()
                        .replaceAll(STRING_COLON, STRING_EMPTY)
        ) + STRING_UNDERLINE + RandomUtil.randomString() + suffix;

        try {
            FileUtil.saveFile(absolutePath + relativeDirectory, fileName, inputStream.readAllBytes());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return relativeDirectory + fileName;
    }
}
