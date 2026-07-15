package cn.hamm.airpower.file;

import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.platform.AliyunOss;
import cn.hamm.airpower.file.platform.LocalFile;
import cn.hamm.airpower.file.platform.TencentCloudOss;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;


/**
 * <h1>文件封装类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class FileHelper {
    @Autowired
    private FileConfig fileConfig;

    @Autowired
    private LocalFile localFile;

    @Autowired
    private AliyunOss aliyunOss;

    @Autowired
    private TencentCloudOss tencentCloudOss;

    /**
     * 将 MultipartFile 转换为 File
     *
     * @param multipartFile 文件
     * @return File  文件
     * @throws IOException 获取文件失败
     */
    public static @NotNull File multipartFileToFile(@NotNull MultipartFile multipartFile) throws IOException {
        // 创建临时文件（JVM 退出后可自动删除）
        File tempFile = Files.createTempFile("upload-", ".tmp").toFile();
        multipartFile.transferTo(tempFile);
        // JVM 退出时删除
        tempFile.deleteOnExit();
        return tempFile;
    }

    /**
     * 获取文件的扩展名
     *
     * @param multipartFile 文件
     * @return 文件扩展名
     */
    public static @NotNull String getFileExtension(MultipartFile multipartFile) {
        if (Objects.isNull(multipartFile)) {
            throw new ServiceException("文件不能为空");
        }
        String originalFilename = multipartFile.getOriginalFilename();
        PARAM_INVALID.whenNull(originalFilename, "文件名不能为空");
        return FileUtil.getExtension(originalFilename);
    }

    /**
     * 获取文件的MD5
     *
     * @param multipartFile 文件
     * @return 文件MD5
     */
    public static @NotNull String getFileHash(MultipartFile multipartFile) {
        if (Objects.isNull(multipartFile)) {
            throw new ServiceException("文件不能为空");
        }
        try (InputStream is = multipartFile.getInputStream()) {
            return DigestUtils.md5DigestAsHex(is);
        } catch (IOException e) {
            throw new ServiceException("计算文件哈希失败，" + e.getMessage());
        }
    }

    /**
     * 验证文件扩展名
     *
     * @param multipartFile 文件
     * @param extensions    文件扩展名
     */
    public static void validateFileExtension(MultipartFile multipartFile, @NotNull String... extensions) {
        if (Objects.isNull(multipartFile)) {
            throw new ServiceException("文件不能为空");
        }
        String originalFilename = multipartFile.getOriginalFilename();
        PARAM_INVALID.whenNull(originalFilename, "文件名不能为空");
        String extension = FileUtil.getExtension(originalFilename);
        PARAM_INVALID.whenEmpty(extension, "文件类型不能为空");
        PARAM_INVALID.when(!Arrays.stream(extensions).toList().contains(extension), "文件类型不允许上传");
    }

    /**
     * 获取上传目录
     *
     * @param category 文件类别
     * @return 上传目录
     */
    public String getUploadDirectory(String category) {
        PARAM_INVALID.whenEmpty(category, "文件类别不能为空");
        return fileConfig.getUploadDirectory() + category + "/";
    }

    /**
     * 获取上传目录
     *
     * @param category 文件类别
     * @param isToday  是否是今天
     * @return 上传目录
     */
    public String getUploadDirectory(String category, boolean isToday) {
        PARAM_INVALID.whenEmpty(category, "文件类别不能为空");
        return fileConfig.getUploadDirectory() + category + (isToday ? FileUtil.getTodayDirectory() : "");
    }

    /**
     * 文件上传(忽略全局文件大小限制)
     *
     * @param multipartFile     文件
     * @param relativeDirectory 文件相对路径
     * @param fileName          文件名
     * @param fileSizeLimit     文件大小限制
     * @return 存储的文件信息
     */
    public String upload(@NotNull MultipartFile multipartFile, @NotNull String relativeDirectory, @NotNull String fileName, Consumer<Long> fileSizeLimit) {
        if (Objects.nonNull(fileSizeLimit)) {
            fileSizeLimit.accept(multipartFile.getSize());
        }
        try {
            getFilePlatform().save(multipartFile, relativeDirectory, fileName);
            return relativeDirectory + fileName;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("上传文件失败，" + e.getMessage());
        }
    }

    /**
     * 文件上传
     *
     * @param multipartFile     文件
     * @param relativeDirectory 文件相对路径
     * @param fileName          文件名
     * @return 存储的文件信息
     */
    public String upload(@NotNull MultipartFile multipartFile, @NotNull String relativeDirectory, @NotNull String fileName) {
        return upload(multipartFile, relativeDirectory, fileName,
                (size) -> PARAM_INVALID.when(size > fileConfig.getUploadMaxSize(), "文件大小超出限制")
        );
    }

    /**
     * 文件上传
     *
     * @param multipartFile 文件
     * @param category      类型
     * @return 存储的文件信息
     */
    public String upload(@NotNull MultipartFile multipartFile, @NotNull String category) {
        String relativeDirectory = getUploadDirectory(category);
        String fileName = getFileHash(multipartFile);
        fileName += "." + getFileExtension(multipartFile);
        return upload(multipartFile, relativeDirectory, fileName,
                (size) -> PARAM_INVALID.when(size > fileConfig.getUploadMaxSize(), "文件大小超出限制")
        );
    }

    /**
     * 获取文件存储平台
     *
     * @return 文件存储平台
     */
    @Contract(pure = true)
    private IFilePlatform getFilePlatform() {
        FilePlatform filePlatform = fileConfig.getFilePlatform();
        return switch (filePlatform) {
            case LOCAL -> localFile;
            case ALIYUN -> aliyunOss;
            case TENCENT_CLOUD -> tencentCloudOss;
        };
    }

    /**
     * 获取文件URL
     *
     * @param url 文件URL
     * @return 文件URL
     */
    public String getUrl(String url) {
        return getFilePlatform().getUrl(url);
    }
}
