package cn.hamm.airpower.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Mcp 响应结果</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpResponseResult {
    /**
     * 内容
     */
    private List<McpResponseContent> content = new ArrayList<>();

    /**
     * 是否错误
     */
    private Boolean isError = false;

    /**
     * 添加文本内容
     *
     * @param text 文本
     * @return this
     */
    @Contract("_ -> this")
    public final McpResponseResult addTextContent(String text) {
        this.content.add(new McpResponseContent().setText(text));
        return this;
    }

    /**
     * 添加图片内容
     */
    @Contract("_ -> this")
    public final McpResponseResult addImageContent(String base64Image) {
        this.content.add(new McpResponseContent()
                .setType("image")
                .setMimeType("image/png")
                .setData(base64Image)
        );
        return this;
    }

    /**
     * Mcp 响应内容
     */
    @Data
    @Accessors(chain = true)
    static class McpResponseContent {
        /**
         * 类型
         */
        private String type = "text";

        /**
         * 文本内容
         */
        private String text;

        /**
         * 图片内容
         */
        private String data;

        /**
         * 图片类型
         */
        private String mimeType;
    }
}
