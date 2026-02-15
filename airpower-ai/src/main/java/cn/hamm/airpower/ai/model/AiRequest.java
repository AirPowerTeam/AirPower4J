package cn.hamm.airpower.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>AI 请求</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class AiRequest {
    /**
     * 是否流式返回
     */
    private Boolean stream = false;
    /**
     * 最大 Token
     */
    @JsonProperty("max_tokens")
    private Integer maxToken;
    /**
     * 模型
     */
    private String model;
    /**
     * 是否启用思考
     */
    @JsonProperty("enable_thinking")
    private Boolean enableThinking;
    /**
     * 消息列表
     */
    private List<AiMessage> messages = new ArrayList<>();

    /**
     * 私有构造函数
     */
    private AiRequest() {

    }

    /**
     * 从提示词开始
     *
     * @param prompt 提示词
     * @return 当前对象
     */
    @Contract("_ -> new")
    public static AiRequest prompt(String prompt) {
        return new AiRequest().addMessage(new AiMessage()
                .setRole("system")
                .setContent(prompt)
        );
    }

    /**
     * 添加消息
     *
     * @param message 消息
     * @return 当前对象
     */
    @Contract("_ -> this")
    private AiRequest addMessage(AiMessage message) {
        messages.add(message);
        return this;
    }

    /**
     * 添加用户消息
     *
     * @param content 内容
     * @return 当前对象
     */
    public AiRequest addMessage(String content) {
        return addMessage(new AiMessage()
                .setRole(AiMessage.AiRole.USER)
                .setContent(content)
        );
    }

    /**
     * 添加助手消息
     *
     * @param content 内容
     * @return 当前对象
     */
    public AiRequest addAssistantMessage(String content) {
        return addMessage(new AiMessage()
                .setRole(AiMessage.AiRole.ASSISTANT)
                .setContent(content)
        );
    }
}
