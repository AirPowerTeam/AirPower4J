package cn.hamm.airpower.file.platform.local;

import cn.hamm.airpower.core.file.FileUtil;
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
 * <h1>本地存储平台</h1>
 * 文件落在本机磁盘，目录由 {@link LocalFileConfig#getLocalAbsoluteDirectory()} 指定。
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
     * @apiNote 整流一次性读入内存，大文件慎用
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
     * @apiNote 规范化后必须仍在根目录内、不能等于根目录，且从根到目标的<b>每一级</b>都不能是符号链接
     */
    @Override
    public void delete(String path) {
        PARAM_INVALID.whenEmpty(path, "文件路径不能为空");
        Path root = Path.of(localFileConfig.getLocalAbsoluteDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException("非法的文件路径");
        }
        // startsWith 对「根目录自身」同样成立，不单独拦一次的话 delete(".") 会把整个存储目录删掉
        if (target.equals(root)) {
            throw new ServiceException("不允许删除存储根目录");
        }
        rejectSymbolicLink(root, target);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.error("删除文件失败 path={}", path, e);
            throw new ServiceException("删除文件失败");
        }
    }

    /**
     * 拒绝从根目录到目标路径之间的任一级符号链接
     *
     * @param root   存储根目录
     * @param target 目标路径
     * @apiNote 只检查目标<b>本身</b>是不够的：父目录是链接时，
     * 目标自身并不是链接，照样能顺着链接删到根目录之外
     */
    private void rejectSymbolicLink(Path root, Path target) {
        Path current = root;
        for (Path name : root.relativize(target)) {
            current = current.resolve(name);
            if (Files.isSymbolicLink(current)) {
                throw new ServiceException("不允许操作符号链接");
            }
        }
    }
}
