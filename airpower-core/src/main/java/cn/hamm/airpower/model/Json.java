package cn.hamm.airpower.model;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.exception.IException;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.root.RootEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.hamm.airpower.config.Constant.STRING_EMPTY;
import static cn.hamm.airpower.exception.ServiceError.SERVICE_ERROR;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

/**
 * <h1>简单 {@code JSON} 对象</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
@Slf4j
public class Json {
    /**
     * <h3>{@code ObjectMapper}</h3>
     */
    private static ObjectMapper objectMapper = null;

    /**
     * <h3>错误代码</h3>
     */
    @Description("错误代码")
    private int code = HttpStatus.OK.value();

    /**
     * <h3>错误信息</h3>
     */
    @Description("错误信息")
    private String message = "";

    /**
     * <h3>返回数据</h3>
     */
    @Description("返回数据")
    private Object data;

    /**
     * <h3>请求ID</h3>
     */
    @Description("请求ID")
    private String requestId;

    @Contract(pure = true)
    private Json() {
        // 禁止外部实例化
    }

    /**
     * <h3>输出提示信息</h3>
     *
     * @param message 提示信息
     * @return {@code Json}
     */
    public static Json success(String message) {
        return create().setMessage(message);
    }

    /**
     * <h3>输出数据</h3>
     *
     * @param data 数据
     * @return {@code Json}
     */
    public static Json data(Object data) {
        return data(data, "获取成功");
    }

    /**
     * <h3>输出实体</h3>
     *
     * @param id 实体 {@code ID}
     * @return {@code Json}
     */
    public static <E extends RootEntity<E>> Json entity(@NotNull Long id) {
        return data(new RootEntity<E>().setId(id));
    }

    /**
     * <h3>输出实体</h3>
     *
     * @param id      实体 {@code Json}
     * @param message 提示信息
     * @return {@code Json}
     */
    public static Json entity(@NotNull Long id, @NotNull String message) {
        return entity(id).setMessage(message);
    }

    /**
     * <h3>输出数据</h3>
     *
     * @param data    数据
     * @param message 提示信息
     * @return {@code Json}
     */
    public static Json data(Object data, String message) {
        return create().setData(data).setMessage(message);
    }

    /**
     * <h3>输出错误</h3>
     *
     * @param error 错误枚举
     * @return {@code Json}
     */
    public static Json error(IException<?> error) {
        return error(error, error.getMessage());
    }

    /**
     * <h3>输出错误</h3>
     *
     * @param error   错误枚举
     * @param message 错误信息
     * @return {@code Json}
     */
    public static Json error(@NotNull IException<?> error, String message) {
        return error(error, message, null);
    }

    /**
     * <h3>输出错误</h3>
     *
     * @param error   错误枚举
     * @param message 错误信息
     * @param data    错误数据
     * @return {@code Json}
     */
    public static Json error(@NotNull IException<?> error, String message, Object data) {
        return show(error.getCode(), message, data);
    }

    /**
     * <h3>输出错误</h3>
     *
     * @param message 错误信息
     * @return {@code Json}
     */
    public static Json error(String message) {
        return error(SERVICE_ERROR, message);
    }

    /**
     * <h3>输出 {@code Json}</h3>
     *
     * @param code    错误代码
     * @param message 提示信息
     * @param data    输出数据
     * @return {@code Json}
     */
    public static Json show(int code, String message, Object data) {
        return create().setCode(code).setMessage(message).setData(data);
    }

    /**
     * <h3>{@code Json} 反序列化到指定类</h3>
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
     * <h3>{@code Json} 反序列化为数组</h3>
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
     * <h3>{@code Json} 反序列化为 {@code Map}</h3>
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
     * <h3>{@code Json} 反序列化为 {@code ListMap}</h3>
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
     * <h3>{@code Json} 序列化到字符串</h3>
     *
     * @param object 对象
     * @return 字符串
     */
    public static String toString(Object object) {
        try {
            return getObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            return STRING_EMPTY;
        }
    }

    /**
     * <h3>获取一个配置后的 {@code ObjectMapper}</h3>
     *
     * @return {@code ObjectMapper}
     */
    private static @NotNull ObjectMapper getObjectMapper() {
        if (Objects.isNull(objectMapper)) {
            objectMapper = new ObjectMapper();
            // 忽略未声明的属性
            objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            // 忽略值为null的属性
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            // 忽略没有属性的类
            objectMapper.configure(FAIL_ON_EMPTY_BEANS, false);
        }
        return objectMapper;
    }

    /**
     * <h3>初始化一个新的 {@code JSON} 对象</h3>
     *
     * @return {@code JSON} 对象
     */
    @Contract(" -> new")
    public static @NotNull Json create() {
        return new Json();
    }
}
