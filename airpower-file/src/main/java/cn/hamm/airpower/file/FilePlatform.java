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
     * 本地上传
     */
    LOCAL(0, "本地上传"),

    /**
     * 阿里云 OSS 上传
     */
    ALIYUN(1, "阿里云 OSS 上传"),

    /**
     * 腾讯云 OSS 上传
     */
    TENCENT_CLOUD_OSS(2, "腾讯云 OSS 上传"),
    ;

    private final int key;
    private final String label;
}
