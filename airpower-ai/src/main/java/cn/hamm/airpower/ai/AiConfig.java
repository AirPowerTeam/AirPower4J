package cn.hamm.airpower.ai;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>AI 模型</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.ai")
@Slf4j
public class AiConfig {
    /**
     * 请求地址
     */
    private String url = "https://api.siliconflow.cn/v1/chat/completions";

    /**
     * 调用密钥
     */
    private String key;

    /**
     * 模型名称
     */
    private String model = "Qwen/Qwen3-8B";

    /**
     * 最大 Token
     */
    private Integer maxToken = 4096;

    /**
     * 思考功能
     */
    private Boolean enableThinking = false;
}
