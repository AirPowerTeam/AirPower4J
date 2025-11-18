package cn.hamm.airpower.file;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
     * @param options           保存选项
     */
    public static void saveFile(@NotNull String absoluteDirectory, @NotNull String fileName, byte @NotNull [] bytes, OpenOption @NotNull ... options) {
        absoluteDirectory = formatDirectory(absoluteDirectory);
        createDirectories(absoluteDirectory);
        try {
            Path path = Paths.get(absoluteDirectory + fileName);
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            Files.write(path, bytes, options);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("文件保存失败，请确认权限是否正常");
        }
    }

    /**
     * 保存文件
     *
     * @param absoluteDirectory 目录绝对路径
     * @param fileName          文件名
     * @param string            文件字符串内容
     * @param options           保存选项
     */
    public static void saveFile(@NotNull String absoluteDirectory, @NotNull String fileName, @NotNull String string, OpenOption @NotNull ... options) {
        saveFile(absoluteDirectory, fileName, string.getBytes(StandardCharsets.UTF_8), options);
    }

    /**
     * 将整个文件夹压缩为ZIP文件
     *
     * @param sourceDirPath 源文件夹路径
     * @param zipFilePath   ZIP文件输出路径
     * @throws IOException IO异常
     */
    public static void zip(String sourceDirPath, String zipFilePath) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        if (!Files.exists(sourceDir)) {
            throw new IOException("源文件夹不存在: " + sourceDirPath);
        }
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDirectory(sourceDir, sourceDir.getFileName().toString(), zos);
        }
    }

    /**
     * 递归压缩目录
     *
     * @param dir 要压缩的目录
     * @param zos ZIP输出流
     * @throws IOException IO异常
     * @paramDirectoryName 相对于根目录的名称
     */
    private static void zipDirectory(Path dir, String dirName, ZipOutputStream zos) throws IOException {
        // 添加目录条目
        dirName = formatDirectory(dirName);

        ZipEntry dirEntry = new ZipEntry(dirName);
        zos.putNextEntry(dirEntry);
        zos.closeEntry();

        // 遍历目录中的所有文件和子目录
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                String entryName = dirName + path.getFileName().toString();

                if (Files.isDirectory(path)) {
                    // 递归处理子目录
                    zipDirectory(path, entryName, zos);
                } else {
                    // 添加文件条目
                    ZipEntry fileEntry = new ZipEntry(entryName);
                    zos.putNextEntry(fileEntry);

                    // 写入文件内容
                    try (FileInputStream fis = new FileInputStream(path.toFile())) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * 删除文件夹以及当前文件夹内的文件，但是不删除父级文件夹
     *
     * @param pathName 文件夹路径
     */
    public static void deleteDirectory(String pathName) {
        Path path = Paths.get(pathName);
        if (Files.exists(path)) {
            try {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
