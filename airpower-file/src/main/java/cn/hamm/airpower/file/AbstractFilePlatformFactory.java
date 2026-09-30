package cn.hamm.airpower.file;

import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import io.micrometer.common.lang.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>文件存储平台基类</h1>
 * 各存储平台（本地、阿里云 OSS、腾讯云 COS…）的实现基类，统一了文件命名、哈希、上传入口，
 * 只需实现 {@link #save} 与平台特有的 {@link #getUrl}、{@link #delete}、{@link #download}。
 *
 * @author Hamm
 */
@Service
@Slf4j
public abstract class AbstractFilePlatformFactory {
    /**
     * 文件配置
     */
    @Autowired
    private FileConfig fileConfig;


    /**
     * 获取上传文件的名称
     *
     * @param multipartFile 文件
     * @return 文件名称
     */
    public @NotNull String getFileName(MultipartFile multipartFile) {
        if (Objects.isNull(multipartFile)) {
            throw new ServiceException("文件不能为空");
        }
        String filename = multipartFile.getOriginalFilename();
        PARAM_INVALID.whenNull(filename, "文件名不能为空");
        return filename;
    }

    /**
     * 获取文件的名称
     *
     * @param file 文件
     * @return 文件名称
     */
    public @NotNull String getFileName(File file) {
        if (Objects.isNull(file)) {
            throw new ServiceException("文件不能为空");
        }
        String fileName = file.getName();
        PARAM_INVALID.whenNull(fileName, "文件名不能为空");
        return fileName;
    }

    /**
     * 获取上传文件的 MD5
     *
     * @param multipartFile 文件
     * @return 文件 MD5
     */
    public @NotNull String getFileHash(MultipartFile multipartFile) {
        if (Objects.isNull(multipartFile)) {
            throw new ServiceException("文件不能为空");
        }
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return getInputStreamHash(inputStream);
        } catch (IOException e) {
            throw new ServiceException("计算文件哈希失败，" + e.getMessage());
        }
    }

    /**
     * 获取文件输入流的 MD5
     *
     * @param inputStream 文件输入流
     * @return 文件输入流的 MD5
     */
    public @NotNull String getInputStreamHash(InputStream inputStream) {
        if (Objects.isNull(inputStream)) {
            throw new ServiceException("文件输入流不能为空");
        }
        try {
            return DigestUtils.md5DigestAsHex(inputStream);
        } catch (IOException e) {
            throw new ServiceException("计算文件哈希失败，" + e.getMessage());
        }
    }

    /**
     * 验证文件扩展名
     *
     * @param fileExtension 文件扩展名
     * @param extensions    允许的文件扩展名
     */
    public void validateUploadFileExtension(String fileExtension, @NotNull String... extensions) {
        PARAM_INVALID.whenEmpty(fileExtension, "文件类型不能为空");
        PARAM_INVALID.when(!Arrays.stream(extensions).toList().contains(fileExtension), "文件类型不允许上传");
    }

    /**
     * 获取文件的 MD5
     *
     * @param file 文件
     * @return 文件 MD5
     */
    public @NotNull String getFileHash(File file) {
        if (Objects.isNull(file)) {
            throw new ServiceException("文件不能为空");
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return getInputStreamHash(inputStream);
        } catch (IOException e) {
            throw new ServiceException("计算文件哈希失败，" + e.getMessage());
        }
    }


    /**
     * 文件上传（可自定义文件大小限制）
     *
     * @param inputStream       文件输入流
     * @param relativeDirectory 文件相对路径
     * @param fileName          文件名
     * @param fileSizeLimit     文件大小的回调，不传入则使用默认限制
     * @return 存储的文件路径
     * @apiNote 传入回调后<b>不再做</b>默认的大小校验，而是把字节数交给回调自行处理；
     * 字节数取自 {@code available()}，未标记长度的流该值并不可靠
     */
    public String upload(@NotNull InputStream inputStream,
                         @NotNull String relativeDirectory,
                         @NotNull String fileName,
                         @Nullable Consumer<Integer> fileSizeLimit
    ) {
        relativeDirectory = FileUtil.formatDirectory(relativeDirectory);
        try {
            if (Objects.nonNull(fileSizeLimit)) {
                fileSizeLimit.accept(inputStream.available());
            } else {
                PARAM_INVALID.when(
                        inputStream.available() > fileConfig.getUploadMaxSize(),
                        "文件大小超出限制"
                );
            }
            save(inputStream, relativeDirectory, fileName);
            return relativeDirectory + fileName;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("上传文件失败，" + e.getMessage());
        }
    }

    /**
     * 文件上传
     *
     * @param inputStream       文件输入流
     * @param relativeDirectory 文件相对路径
     * @param fileName          文件名
     * @return 存储的文件路径
     */
    public String upload(@NotNull InputStream inputStream,
                         @NotNull String relativeDirectory,
                         @NotNull String fileName) {
        return upload(inputStream, relativeDirectory, fileName, null);
    }

    /**
     * 文件上传
     *
     * @param multipartFile 文件
     * @param category      类型
     * @return 存储的文件路径
     */
    public String upload(@NotNull MultipartFile multipartFile, @NotNull String category) {
        return upload(multipartFile, category, null);
    }

    /**
     * 获取上传目录
     *
     * @param category 文件类别
     * @return 上传目录
     */
    public String getUploadDirectory(String category) {
        return getUploadDirectory(category, false);
    }

    /**
     * 获取上传目录
     *
     * @param category 文件类别
     * @param isToday  是否追加当天日期目录
     * @return 上传目录
     */
    public String getUploadDirectory(String category, boolean isToday) {
        PARAM_INVALID.whenEmpty(category, "文件类别不能为空");
        return fileConfig.getUploadDirectory() + category + (isToday ? FileUtil.getTodayDirectory() : "");
    }


    /**
     * 文件上传（可自定义文件大小限制）
     *
     * @param multipartFile 文件
     * @param category      类型
     * @param fileSizeLimit 文件大小的回调，不传入则使用默认限制
     * @return 存储的文件路径
     * @apiNote 文件名取文件 MD5，同一文件重复上传会落到同一路径并被覆盖
     */
    public String upload(@NotNull MultipartFile multipartFile,
                         @NotNull String category,
                         @Nullable Consumer<Integer> fileSizeLimit) {
        String relativeDirectory = getUploadDirectory(category);
        String fileName = getFileHash(multipartFile);
        fileName += "." + FileUtil.getExtension(getFileName(multipartFile));
        try {
            return upload(multipartFile.getInputStream(), relativeDirectory, fileName, fileSizeLimit);
        } catch (IOException e) {
            throw new ServiceException("上传文件失败，");
        }
    }

    /**
     * 文件上传
     *
     * @param file     文件
     * @param category 类型
     * @return 存储的文件信息
     */
    public String upload(@NotNull File file, @NotNull String category) {
        return upload(file, category, null);
    }

    /**
     * 文件上传（可自定义文件大小限制）
     *
     * @param file          文件
     * @param category      类型
     * @param fileSizeLimit 文件大小的回调，不传入则使用默认限制
     * @return 存储的文件信息
     * @apiNote 文件名取文件 MD5，同一文件重复上传会落到同一路径并被覆盖
     */
    public String upload(@NotNull File file,
                         @NotNull String category,
                         @Nullable Consumer<Integer> fileSizeLimit) {
        String relativeDirectory = getUploadDirectory(category);
        String fileName = getFileHash(file);
        fileName += "." + FileUtil.getExtension(getFileName(file));
        try {
            return upload(new FileInputStream(file), relativeDirectory, fileName, fileSizeLimit);
        } catch (IOException e) {
            throw new ServiceException("上传文件失败，");
        }
    }

    /**
     * 保存文件
     *
     * @param inputStream 文件输入流
     * @param directory   文件目录
     * @param fileName    文件名
     */
    public abstract void save(@NotNull InputStream inputStream,
                              @NotNull String directory,
                              @NotNull String fileName);

    /**
     * 获取文件 URL
     *
     * @param path   文件路径
     * @param second 过期时间(秒)
     * @return 文件 URL
     * @apiNote 默认实现直接返回路径，私有存储的平台需重写为带签名的临时链接
     */
    public String getUrl(String path, int second) {
        return path;
    }

    /**
     * 从文件平台获取文件流
     *
     * @param path 文件路径
     * @return 文件流
     * @apiNote 平台未实现下载时直接抛异常
     */
    public InputStream download(String path) {
        throw new ServiceException("该平台暂不支持下载文件");
    }

    /**
     * 从文件平台删除文件
     *
     * @param path 文件路径
     * @apiNote 平台未实现删除时直接抛异常
     */
    public void delete(String path) {
        throw new ServiceException("该平台暂不支持删除文件");
    }

    /**
     * 获取文件平台键
     *
     * @return 文件平台键
     * @apiNote 读取类上的 {@link FilePlatform#value()} 注解值
     */
    public final String getKey() {
        FilePlatform annotation = getClass().getAnnotation(FilePlatform.class);
        if (Objects.isNull(annotation)) {
            throw new RuntimeException(getClass().getName() + " 未标记 @FilePlatform 注解");
        }
        return annotation.value();
    }
}
