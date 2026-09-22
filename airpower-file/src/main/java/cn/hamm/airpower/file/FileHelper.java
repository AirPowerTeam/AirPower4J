package cn.hamm.airpower.file;

import cn.hamm.airpower.core.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <h1>文件助手类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class FileHelper {
    /**
     * 文件存储平台列表
     * key 为 {@link FilePlatform#value()} 指定的平台标识。
     */
    private final Map<String, AbstractFilePlatformFactory> platforms = new HashMap<>();

    /**
     * 文件配置
     */
    @Autowired
    private FileConfig fileConfig;

    /**
     * 注入所有 {@link AbstractFilePlatformFactory} 实例
     */
    @Autowired
    private List<AbstractFilePlatformFactory> allPlatforms;

    /**
     * 获取默认的文件存储平台
     *
     * @return 文件存储平台
     */
    @Contract(pure = true)
    public AbstractFilePlatformFactory getPlatform() {
        return getPlatform(fileConfig.getDefaultPlatform());
    }

    /**
     * 通过平台标识获取文件存储平台
     *
     * @param key 平台标识
     * @return 文件存储平台
     */
    public AbstractFilePlatformFactory getPlatform(String key) {
        AbstractFilePlatformFactory platform = platforms.get(key);
        if (Objects.isNull(platform)) {
            throw new ServiceException("暂未支持的文件存储平台 " + key);
        }
        return platform;
    }

    /**
     * 注册所有标记了 {@link FilePlatform} 的文件平台
     */
    @PostConstruct
    private void registerPlatforms() {
        platforms.clear();
        for (AbstractFilePlatformFactory platform : allPlatforms) {
            FilePlatform annotation = platform.getClass().getAnnotation(FilePlatform.class);
            if (Objects.isNull(annotation)) {
                continue;
            }
            String key = annotation.value();
            AbstractFilePlatformFactory exists = platforms.put(key, platform);
            if (Objects.nonNull(exists)) {
                throw new ServiceException(
                        "文件存储平台 key 重复: " + key +
                                " (" + exists.getClass().getName() +
                                " 和 " + platform.getClass().getName() + ")"
                );
            }
        }
        log.info("已注册 {} 个文件存储平台: {}", platforms.size(), platforms.keySet());
    }
}
