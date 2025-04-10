package cn.hamm.airpower.enums;

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
     * <h3>{@code JSON}</h3>
     */
    JSON(APPLICATION_JSON_VALUE),

    /**
     * <h3>{@code HTML}</h3>
     */
    HTML(TEXT_HTML_VALUE),

    /**
     * <h3>{@code PLAIN}</h3>
     */
    PLAIN(TEXT_PLAIN_VALUE),

    /**
     * <h3>{@code XML}</h3>
     */
    XML(TEXT_XML_VALUE),

    /**
     * <h3>{@code FORM_URLENCODED}</h3>
     */
    FORM_URLENCODED(APPLICATION_FORM_URLENCODED_VALUE),

    /**
     * <h3>{@code MULTIPART_FORM_DATA}</h3>
     */
    MULTIPART_FORM_DATA(MULTIPART_FORM_DATA_VALUE);

    private final String value;
}
