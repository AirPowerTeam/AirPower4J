package cn.hamm.airpower.file.platform.local;

import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.file.AbstractFilePlatformFactory;
import cn.hamm.airpower.file.FilePlatform;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>文件封装类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@FilePlatform("LOCAL")
public class LocalFileHelper extends AbstractFilePlatformFactory {
    @Autowired
    private LocalFileConfig localFileConfig;

    /**
     * 保存文件
     *
     * @param inputStream 文件输入流
     * @param directory   文件目录
     * @param fileName    文件名
     */
    @Override
    public void save(@NotNull InputStream inputStream, String directory, String fileName) {
        try {
            FileUtil.saveFile(localFileConfig.getLocalAbsoluteDirectory() + directory,
                    fileName,
                    inputStream.readAllBytes()
            );
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败，" + e.getMessage());
        }
    }

    /**
     * 删除文件
     *
     * @param path 文件路径
     */
    @Override
    public void delete(String path) {
        PARAM_INVALID.whenEmpty(path, "文件路径不能为空");
        Path root = Path.of(localFileConfig.getLocalAbsoluteDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException("非法的文件路径");
        }
        if (Files.isSymbolicLink(target)) {
            throw new ServiceException("不允许操作符号链接");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.error("删除文件失败 path={}", path, e);
            throw new ServiceException("删除文件失败");
        }
    }
}
