package cn.hamm.airpower.mcp.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Mcp响应结果</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class McpResponseResult {
    /**
     * <h3>内容</h3>
     */
    private List<McpResponseContent> content = new ArrayList<>();

    /**
     * <h3>是否错误</h3>
     */
    private Boolean isError = false;

    /**
     * <h3>添加文本内容</h3>
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
     * <h3>添加图片内容</h3>
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
     * <h3>Mcp响应内容</h3>
     */
    @Data
    @Accessors(chain = true)
    static class McpResponseContent {
        /**
         * <h3>类型</h3>
         */
        private String type = "text";

        /**
         * <h3>文本内容</h3>
         */
        private String text;

        /**
         * <h3>图片内容</h3>
         */
        private String data;

        /**
         * <h3>图片类型</h3>
         */
        private String mimeType;
    }
}
