package cn.hamm.airpower.api;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.exception.IException;
import cn.hamm.airpower.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.hamm.airpower.exception.ServiceError.SERVICE_ERROR;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

/**
 * <h1>简单 JSON 对象</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
@Slf4j
public class Json {
    /**
     * {@code ObjectMapper}
     */
    private static ObjectMapper objectMapper = null;

    /**
     * 错误代码
     */
    @Description("错误代码")
    private int code = HttpStatus.OK.value();

    /**
     * 错误信息
     */
    @Description("错误信息")
    private String message = "";

    /**
     * 返回数据
     */
    @Description("返回数据")
    private Object data;

    @Description("TraceID")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String traceId;

    @Contract(pure = true)
    private Json() {
        // 禁止外部实例化
    }

    /**
     * 输出提示信息
     *
     * @param message 提示信息
     * @return JSON
     */
    public static Json success(String message) {
        return create().setMessage(message);
    }

    /**
     * 输出数据
     *
     * @param data 数据
     * @return JSON
     */
    public static Json data(Object data) {
        return data(data, "获取成功");
    }

    /**
     * 输出实体
     *
     * @param id 实体 {@code ID}
     * @return JSON
     */
    public static <E extends CurdEntity<E>> Json entity(@NotNull Long id) {
        return data(new CurdEntity<E>().setId(id));
    }

    /**
     * 输出实体
     *
     * @param id      实体 JSON
     * @param message 提示信息
     * @return JSON
     */
    public static Json entity(@NotNull Long id, @NotNull String message) {
        return entity(id).setMessage(message);
    }

    /**
     * 输出数据
     *
     * @param data    数据
     * @param message 提示信息
     * @return JSON
     */
    public static Json data(Object data, String message) {
        return create().setData(data).setMessage(message);
    }

    /**
     * 输出错误
     *
     * @param error 错误枚举
     * @return JSON
     */
    public static Json error(IException<?> error) {
        return error(error, error.getMessage());
    }

    /**
     * 输出错误
     *
     * @param error   错误枚举
     * @param message 错误信息
     * @return JSON
     */
    public static Json error(@NotNull IException<?> error, String message) {
        return error(error, message, null);
    }

    /**
     * 输出错误
     *
     * @param error   错误枚举
     * @param message 错误信息
     * @param data    错误数据
     * @return JSON
     */
    public static Json error(@NotNull IException<?> error, String message, Object data) {
        return show(error.getCode(), message, data);
    }

    /**
     * 输出错误
     *
     * @param message 错误信息
     * @return JSON
     */
    public static Json error(String message) {
        return error(SERVICE_ERROR, message);
    }

    /**
     * 输出 JSON
     *
     * @param code    错误代码
     * @param message 提示信息
     * @param data    输出数据
     * @return JSON
     */
    public static Json show(int code, String message, Object data) {
        return create().setCode(code).setMessage(message).setData(data);
    }

    /**
     * JSON 反序列化到指定类
     *
     * @param json  字符串
     * @param clazz 目标类
     * @param <T>   目标类
     * @return 目标类的实例
     */
    public static <T> T parse(String json, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(json, clazz);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * JSON 反序列化到指定类
     *
     * @param json          字符串
     * @param typeReference 目标类
     * @param <T>           目标类
     * @return 目标类的实例
     */
    public static <T> T parse(String json, TypeReference<T> typeReference) {
        try {
            return getObjectMapper().readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * JSON 反序列化为数组
     *
     * @param json  字符串
     * @param clazz 目标数组类
     * @param <T>   目标类型
     * @return 目标类的实例数组
     */
    public static <T> T[] parseList(String json, Class<? extends T[]> clazz) {
        try {
            return getObjectMapper().readValue(json, clazz);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * JSON 反序列化为 {@code Map}
     *
     * @param json 字符串
     * @return {@code Map}
     */
    public static Map<String, Object> parse2Map(String json) {
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
            };
            return getObjectMapper().readValue(json, typeRef);
        } catch (Exception exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * JSON 反序列化为 {@code ListMap}
     *
     * @param json 字符串
     * @return {@code List<Map>}
     */
    public static List<Map<String, Object>> parse2MapList(String json) {
        try {
            TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {
            };
            return getObjectMapper().readValue(json, typeRef);
        } catch (Exception exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * JSON 序列化到字符串
     *
     * @param object 对象
     * @return 字符串
     */
    public static String toString(Object object) {
        try {
            return getObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * 获取一个配置后的 {@code ObjectMapper}
     *
     * @return {@code ObjectMapper}
     */
    private static @NotNull ObjectMapper getObjectMapper() {
        if (Objects.isNull(objectMapper)) {
            synchronized (Json.class) {
                if (Objects.isNull(objectMapper)) {
                    ObjectMapper mapper = new ObjectMapper();
                    // 忽略未声明的属性
                    mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
                    // 全局忽略值为 null 的属性。
                    // 用 setSerializationInclusion 而非 configOverride：后者只决定 POJO 属性
                    // 在未标注 @JsonInclude 时的默认行为，对被直接序列化的 Map 不生效
                    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
                    // 忽略没有属性的类
                    mapper.configure(FAIL_ON_EMPTY_BEANS, false);
                    // 必须注册 JSR-310：否则 LocalDate / LocalDateTime / Instant 被当成普通 POJO
                    // 做 bean 序列化，产出 {"year":2026,"monthValue":10,...} 这种结构；
                    // parse 回来时字段类型不匹配直接失败，Redis 缓存整体读不回
                    mapper.registerModule(new JavaTimeModule());
                    // 时间统一输出 ISO-8601 字符串，不要默认的时间戳数字：
                    // 数字形式在跨时区、跨语言时无法自解释，前端也无法直接解析
                    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    objectMapper = mapper;
                }
            }
        }
        return objectMapper;
    }

    /**
     * 初始化一个新的 JSON 对象
     *
     * @return JSON 对象
     */
    @Contract(" -> new")
    public static @NotNull Json create() {
        return new Json();
    }
}
