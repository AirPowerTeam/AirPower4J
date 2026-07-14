package cn.hamm.airpower.file;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * <h1>文件平台接口</h1>
 *
 * @author Hamm
 */
public interface IFilePlatform {
    /**
     * <h1>保存文件</h1>
     *
     * @param multipartFile 文件
     * @param directory     文件目录
     * @param fileName      文件名
     */
    void save(@NotNull MultipartFile multipartFile, @NotNull String directory, @NotNull String fileName);

    /**
     * <h1>获取文件 URL</h1>
     *
     * @param path 文件路径
     * @return 文件 URL
     */
    default String getUrl(String path) {
        return path;
    }

    /**
     * <h1>从文件平台获取文件流</h1>
     *
     * @param path 文件路径
     * @return 文件流
     */
    default InputStream download(String path) {
        throw new RuntimeException("暂不支持该平台");
    }
}
