package cn.hamm.airpower.model;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.config.Constant;
import cn.hamm.airpower.config.MessageConstant;
import cn.hamm.airpower.enums.SystemError;
import cn.hamm.airpower.exception.SystemException;
import cn.hamm.airpower.interfaces.IException;
import cn.hamm.airpower.root.RootEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>简单JSON对象</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
@Slf4j
public class Json {
    /**
     * <h2>错误代码</h2>
     */
    @Description("错误代码")
    private int code = Constant.JSON_SUCCESS_CODE;

    /**
     * <h2>错误信息</h2>
     */
    @Description("错误信息")
    private String message = Constant.JSON_SUCCESS_MESSAGE;

    /**
     * <h2>返回数据</h2>
     */
    @Description("返回数据")
    private Object data;

    /**
     * <h2>输出提示信息</h2>
     *
     * @param message 提示信息
     * @return Json
     */
    public static Json success(String message) {
        return newJson().setMessage(message);
    }

    /**
     * <h2>输出数据</h2>
     *
     * @param data 数据
     * @return Json
     */
    public static Json data(Object data) {
        return data(data, "获取成功");
    }

    /**
     * <h2>输出实体</h2>
     *
     * @param id  实体ID
     * @param <E> 实体类型
     * @return Json
     */
    public static <E extends RootEntity<E>> Json entity(@NotNull Long id) {
        return data(new RootEntity<E>().setId(id));
    }


    /**
     * <h2>输出实体</h2>
     *
     * @param id      实体ID
     * @param message 提示信息
     * @return Json
     */
    public static Json entity(@NotNull Long id, @NotNull String message) {
        return entity(id).setMessage(message);
    }

    /**
     * <h2>输出数据</h2>
     *
     * @param data    数据
     * @param message 提示信息
     * @return Json
     */
    public static Json data(Object data, String message) {
        return newJson().setData(data).setMessage(message);
    }

    /**
     * <h2>输出错误</h2>
     *
     * @param error 错误枚举
     * @return Json
     */
    public static Json error(IException error) {
        return error(error, error.getMessage());
    }

    /**
     * <h2>输出错误</h2>
     *
     * @param error   错误枚举
     * @param message 错误信息
     * @return Json
     */
    public static Json error(IException error, String message) {
        return show(error.getCode(), message, null);
    }

    /**
     * <h2>输出错误</h2>
     *
     * @param message 错误信息
     * @return Json
     */
    public static Json error(String message) {
        return error(SystemError.SERVICE_ERROR, message);
    }

    /**
     * <h2>输出Json</h2>
     *
     * @param code    错误代码
     * @param message 提示信息
     * @param data    输出数据
     * @return Json
     */
    public static Json show(int code, String message, Object data) {
        return newJson().setCode(code).setMessage(message).setData(data);
    }

    /**
     * <h2>Json反序列化到指定类</h2>
     *
     * @param json  字符串
     * @param clazz 目标类
     * @param <E>   目标类
     * @return 目标类的实例
     */
    public static <E> E parse(String json, Class<E> clazz) {
        try {
            return getObjectMapper().readValue(json, clazz);
        } catch (JsonProcessingException exception) {
            log.error(MessageConstant.EXCEPTION_WHEN_JSON_PARSE, exception);
            throw new SystemException(exception);
        }
    }


    /**
     * <h2>Json序列化到字符串</h2>
     *
     * @param object 对象
     * @return 字符串
     */
    public static String toString(Object object) {
        try {
            return getObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            log.error(MessageConstant.EXCEPTION_WHEN_JSON_TO_STRING, exception);
            return Constant.EMPTY_STRING;
        }
    }

    /**
     * <h2>获取一个配置后的ObjectMapper</h2>
     *
     * @return ObjectMapper
     */
    private static @NotNull ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 忽略未声明的属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    /**
     * <h2>初始化一个新的JSON对象</h2>
     *
     * @return JSON对象
     */
    private static Json newJson() {
        return new Json();
    }
}