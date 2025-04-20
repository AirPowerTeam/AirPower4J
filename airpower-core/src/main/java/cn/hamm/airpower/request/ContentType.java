package cn.hamm.airpower.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.springframework.http.MediaType.*;

/**
 * <h1>请求的数据类型</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum ContentType {
    /**
     * JSON
     */
    JSON(APPLICATION_JSON_VALUE),

    /**
     * 普通网页
     */
    HTML(TEXT_HTML_VALUE),

    /**
     * 纯文本
     */
    PLAIN(TEXT_PLAIN_VALUE),

    /**
     * XML
     */
    XML(TEXT_XML_VALUE),

    /**
     * 普通表单提交
     */
    FORM_URLENCODED(APPLICATION_FORM_URLENCODED_VALUE),

    /**
     * 文件上传
     */
    MULTIPART_FORM_DATA(MULTIPART_FORM_DATA_VALUE);

    private final String value;
}
