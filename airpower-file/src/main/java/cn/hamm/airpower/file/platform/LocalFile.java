package cn.hamm.airpower.file.platform;

import cn.hamm.airpower.core.FileUtil;
import cn.hamm.airpower.file.FileConfig;
import cn.hamm.airpower.file.IFilePlatform;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


/**
 * <h1>文件封装类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class LocalFile implements IFilePlatform {
    @Autowired
    private FileConfig fileConfig;

    /**
     * <h1>保存文件</h1>
     *
     * @param multipartFile 文件
     * @param directory     文件目录
     * @param fileName      文件名
     */
    @Override
    public void save(@NotNull MultipartFile multipartFile, String directory, String fileName) {
        try {
            FileUtil.saveFile(fileConfig.getLocalAbsoluteDirectory() + directory,
                    fileName,
                    multipartFile.getBytes()
            );
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败，" + e.getMessage());
        }
    }
}
