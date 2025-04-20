package cn.hamm.airpower.file;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;

import static cn.hamm.airpower.datetime.DateTimeFormatter.FULL_DATE;

/**
 * <h1>文件工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class FileUtil {
    /**
     * 文件大小进制
     */
    public static final long FILE_SCALE = 1024L;

    /**
     * 文件单位
     */
    public static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};

    /**
     * 文件名分隔符
     */
    public static final String EXTENSION_SEPARATOR = ".";

    /**
     * 未知文件大小
     */
    private static final String UNKNOWN_FILE_SIZE = "-";

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private FileUtil() {

    }

    /**
     * 获取文件名后缀
     *
     * @param fileName 文件名
     * @return 后缀
     */
    public static @NotNull String getExtension(@NotNull String fileName) {
        return fileName.substring(fileName.lastIndexOf(EXTENSION_SEPARATOR) + 1).toLowerCase();
    }

    /**
     * 格式化文件大小
     *
     * @param size 文件大小
     * @return 格式化后的文件大小
     */
    public static String formatSize(long size) {
        if (size <= 0) {
            log.error("错误的文件大小: {}", size);
            return UNKNOWN_FILE_SIZE;
        }
        double fileSize = size;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        for (String unit : UNITS) {
            if (fileSize < FILE_SCALE) {
                return decimalFormat.format(fileSize) + unit;
            }
            fileSize /= FILE_SCALE;
        }
        return UNKNOWN_FILE_SIZE;
    }

    /**
     * 创建文件夹
     *
     * @param pathString 文件夹路径
     */
    public static void createDirectories(String pathString) {
        Path path = Paths.get(pathString);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException("自动创建文件夹失败，请确认权限是否正常");
            }
        }
    }

    /**
     * 获取今日文件夹
     *
     * @param directory 文件夹路径
     * @return 今日文件夹路径
     */
    public static @NotNull String getTodayDirectory(String directory) {
        String todayDirectory = FULL_DATE.formatCurrent().replaceAll("-", "");
        directory = formatDirectory(directory);
        return directory + todayDirectory + File.separator;
    }

    /**
     * 格式化文件夹
     *
     * @param directory 文件夹
     * @return 格式化后的文件夹
     */
    @Contract(pure = true)
    public static @NotNull String formatDirectory(@NotNull String directory) {
        if (!directory.endsWith(File.separator)) {
            directory += File.separator;
        }
        return directory;
    }

    /**
     * 保存文件
     *
     * @param absoluteDirectory 目录绝对路径
     * @param fileName          文件名
     * @param bytes             文件字节数组
     */
    public static void saveFile(@NotNull String absoluteDirectory, @NotNull String fileName, byte @NotNull [] bytes) {
        absoluteDirectory = formatDirectory(absoluteDirectory);
        createDirectories(absoluteDirectory);
        try {
            Path path = Paths.get(absoluteDirectory + fileName);
            Files.write(path, bytes);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("文件保存失败，请确认权限是否正常");
        }
    }
}
