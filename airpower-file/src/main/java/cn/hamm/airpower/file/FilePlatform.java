package cn.hamm.airpower.file;

import cn.hamm.airpower.core.interfaces.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>上传平台</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum FilePlatform implements IDictionary {
    /**
     * 本地文件
     */
    LOCAL(0, "本地文件"),

    /**
     * 阿里云 OSS
     */
    ALIYUN(1, "阿里云 OSS"),

    /**
     * 腾讯云 OSS
     */
    TENCENT_CLOUD(2, "腾讯云 OSS"),
    ;

    private final int key;
    private final String label;
}
