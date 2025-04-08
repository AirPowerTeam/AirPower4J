package cn.hamm.airpower.util;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

import static cn.hamm.airpower.config.Constant.STRING_DOT;
import static cn.hamm.airpower.config.Constant.STRING_LINE;

/**
 * <h1>文件工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class FileUtil {
    /**
     * <h3>文件大小进制</h3>
     */
    public static final long FILE_SCALE = 1024L;

    /**
     * <h3>文件单位</h3>
     */
    public static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};

    /**
     * <h3>禁止外部实例化</h3>
     */
    private FileUtil() {

    }

    /**
     * <h3>获取文件名后缀</h3>
     *
     * @param fileName 文件名
     * @return 后缀
     */
    public static @NotNull String getExtension(@NotNull String fileName) {
        return fileName.substring(fileName.lastIndexOf(STRING_DOT) + 1).toLowerCase();
    }

    /**
     * <h3>格式化文件大小</h3>
     *
     * @param size 文件大小
     * @return 格式化后的文件大小
     */
    public static String formatSize(long size) {
        if (size <= 0) {
            log.error("错误的文件大小: {}", size);
            return STRING_LINE;
        }
        double fileSize = size;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        for (String unit : UNITS) {
            if (fileSize < FILE_SCALE) {
                return decimalFormat.format(fileSize) + unit;
            }
            fileSize /= FILE_SCALE;
        }
        return STRING_LINE;
    }
}
