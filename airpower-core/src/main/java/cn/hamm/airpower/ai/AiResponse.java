package cn.hamm.airpower.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * <h1>AI响应</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class AiResponse {
    /**
     * 响应 ID
     */
    private String id;

    /**
     * 对象
     */
    private String object;

    /**
     * 创建时间
     */
    private Long created;

    /**
     * 模型
     */
    private String model;

    /**
     * 选择
     */
    private List<AiChoice> choices;

    /**
     * 使用情况
     */
    private AiUsage usage;

    /**
     * 系统指纹
     */
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    /**
     * 获取 AI 响应的消息
     *
     * @return 消息内容
     */
    @JsonIgnore
    public @NotNull String getResponseMessage() {
        if (Objects.isNull(choices) || choices.isEmpty()) {
            return "";
        }
        AiMessage message = choices.get(0).getMessage();
        if (Objects.isNull(message)) {
            return "";
        }
        return Objects.requireNonNull(message.getContent(), "");
    }

    /**
     * 获取 AI 响应的消息
     *
     * @return 消息内容
     */
    @JsonIgnore
    public String getStreamMessage() {
        if (Objects.isNull(choices) || choices.isEmpty()) {
            return "";
        }
        AiMessage delta = choices.get(0).getDelta();
        if (Objects.isNull(delta)) {
            return "";
        }
        return Objects.requireNonNull(delta.getContent(), "");
    }

    /**
     * <h2>使用情况</h2>
     *
     * @author Hamm.cn
     */
    @Data
    @Accessors(chain = true)
    static class AiUsage {
        /**
         * 提示词消耗
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * 完成词消耗
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * 总消耗
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;

        /**
         * 完成词消耗详情
         */
        @JsonProperty("completion_tokens_details")
        private CompletionTokensDetails completionTokensDetails;

        /**
         * <h2>完成词消耗详情</h2>
         *
         * @author Hamm.cn
         */
        @Data
        @Accessors(chain = true)
        static class CompletionTokensDetails {
            /**
             * 理解消耗
             */
            @JsonProperty("reasoning_tokens")
            private Integer reasoningTokens;
        }
    }

    /**
     * <h2>选择</h2>
     *
     * @author Hamm.cn
     */
    @Data
    @Accessors(chain = true)
    static class AiChoice {
        /**
         * 索引
         */
        private Integer index;

        /**
         * 消息
         */
        private AiMessage message;

        /**
         * 差分消息
         */
        private AiMessage delta;

        /**
         * 结束原因
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }
}
