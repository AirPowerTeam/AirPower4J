package cn.hamm.airpower.helper;

import cn.hamm.airpower.config.Constant;
import cn.hamm.airpower.config.ServiceConfig;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.util.DateTimeUtil;
import cn.hamm.airpower.util.RandomUtil;
import cn.hamm.airpower.util.TaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.hamm.airpower.config.Constant.*;
import static cn.hamm.airpower.enums.DateTimeFormatter.FULL_DATE;
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
    /**
     * <h3>导出文件夹前缀</h3>
     */
    private static final String EXPORT_DIR_PREFIX = "export_";
    /**
     * <h3>导出文件后缀</h3>
     */
    private static final String EXPORT_FILE_CSV = ".csv";
    @Autowired
    private RedisHelper redisHelper;
    @Autowired
    private ServiceConfig serviceConfig;

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
     * <h3>创建异步任务</h3>
     *
     * @param supplier 上报任务结果
     * @return 文件编码
     */
    public final String createExportTask(Supplier<String> supplier) {
        String fileCode = RandomUtil.randomString().toLowerCase();
        final String fileCacheKey = EXPORT_DIR_PREFIX + fileCode;
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
        Object object = redisHelper.get(EXPORT_DIR_PREFIX + fileCode);
        DATA_NOT_FOUND.whenNull(object, "错误的FileCode");
        DATA_NOT_FOUND.whenEmpty(object, "文件暂未准备完毕");
        return object.toString();
    }

    /**
     * <h3>保存导出文件流为CSV</h3>
     *
     * @param inputStream 文件流
     * @param suffix      文件后缀
     * @return 保存后的文件名
     */
    public final @NotNull String saveExportFileStream(InputStream inputStream, String suffix) {
        final String absolutePath = serviceConfig.getSaveFilePath() + File.separator;
        SERVICE_ERROR.when(!StringUtils.hasText(absolutePath), "导出失败，未配置导出文件目录");
        try {
            long milliSecond = System.currentTimeMillis();

            // 追加今日文件夹 定时任务将按存储文件夹进行删除过时文件
            String todayDir = DateTimeUtil.format(milliSecond,
                    FULL_DATE.getValue()
                            .replaceAll(STRING_LINE, STRING_EMPTY)
            );
            String exportFilePath = EXPORT_DIR_PREFIX;
            exportFilePath += todayDir + File.separator;

            if (!Files.exists(Paths.get(absolutePath + exportFilePath))) {
                Files.createDirectory(Paths.get(absolutePath + exportFilePath));
            }

            // 存储的文件名
            final String fileName = todayDir + STRING_UNDERLINE + DateTimeUtil.format(milliSecond,
                    FULL_TIME.getValue()
                            .replaceAll(STRING_COLON, STRING_EMPTY)
            ) + STRING_UNDERLINE + RandomUtil.randomString() + suffix;

            // 拼接最终存储路径
            exportFilePath += fileName;
            Path path = Paths.get(absolutePath + exportFilePath);
            Files.write(path, inputStream.readAllBytes());
            return exportFilePath;
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }
}
