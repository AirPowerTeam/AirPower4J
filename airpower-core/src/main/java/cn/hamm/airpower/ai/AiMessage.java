package cn.hamm.airpower.ai;

import cn.hamm.airpower.dictionary.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>AI消息</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class AiMessage {
    /**
     * 角色
     */
    private String role;

    /**
     * 内容
     */
    private String content;

    /**
     * <h1>设置角色</h1>
     *
     * @param role 角色
     * @return 角色
     */
    public AiMessage setRole(String role) {
        this.role = role;
        return this;
    }

    /**
     * 设置角色
     *
     * @param role 角色
     * @return 消息
     */
    public AiMessage setRole(@NotNull AiRole role) {
        return this.setRole(role.name().toLowerCase());
    }

    /**
     * <h1>角色枚举</h1>
     *
     * @author Hamm.cn
     */
    @AllArgsConstructor
    @Getter
    public enum AiRole implements IDictionary {
        /**
         * 系统消息
         */
        SYSTEM(1, "系统消息"),

        /**
         * 用户消息
         */
        USER(2, "用户消息"),

        /**
         * 模型消息
         */
        ASSISTANT(3, "模型消息");


        private final int key;

        private final String label;
    }
}
