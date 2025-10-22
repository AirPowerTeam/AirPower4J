package cn.hamm.airpower.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>AI请求</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class AiRequest {
    /**
     * 是否流式返回
     */
    @Setter(AccessLevel.PACKAGE)
    private Boolean stream = false;

    /**
     * 最大Token
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
     * 添加消息
     *
     * @param message 消息
     * @return 当前对象
     */
    public AiRequest addMessage(AiMessage message) {
        messages.add(message);
        return this;
    }

    /**
     * 添加消息
     *
     * @param role    角色
     * @param content 内容
     * @return 当前对象
     */
    public AiRequest addMessage(AiMessage.AiRole role, String content) {
        return addMessage(new AiMessage()
                .setRole(role)
                .setContent(content)
        );
    }

    /**
     * 添加用户消息
     *
     * @param content 内容
     * @return 当前对象
     */
    public AiRequest addUserMessage(String content) {
        return addMessage(new AiMessage()
                .setRole(AiMessage.AiRole.USER)
                .setContent(content)
        );
    }

    /**
     * 添加系统消息
     *
     * @param content 内容
     * @return 当前对象
     */
    public AiRequest addSystemMessage(String content) {
        return addMessage(new AiMessage()
                .setRole(AiMessage.AiRole.SYSTEM)
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
