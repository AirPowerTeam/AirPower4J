package cn.hamm.airpower.mcp;

import cn.hamm.airpower.config.Constant;
import cn.hamm.airpower.mcp.exception.McpErrorCode;
import cn.hamm.airpower.mcp.exception.McpException;
import cn.hamm.airpower.mcp.method.McpCallMethodResponse;
import cn.hamm.airpower.mcp.method.McpMethod;
import cn.hamm.airpower.mcp.method.McpMethods;
import cn.hamm.airpower.mcp.model.McpInitializeData;
import cn.hamm.airpower.mcp.model.McpRequest;
import cn.hamm.airpower.mcp.model.McpResponse;
import cn.hamm.airpower.mcp.model.McpTool;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.util.DateTimeUtil;
import cn.hamm.airpower.util.ReflectUtil;
import cn.hamm.airpower.util.TaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <h1>McpService</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class McpService {
    /**
     * <h3>SseEmitters</h3>
     */
    public final static ConcurrentHashMap<String, SseEmitter> EMITTERS = new ConcurrentHashMap<>();

    /**
     * <h3>工具列表</h3>
     */
    public static List<McpTool> tools = new ArrayList<>();

    /**
     * <h3>方法列表</h3>
     */
    public final static ConcurrentMap<String, Method> METHOD_MAP = new ConcurrentHashMap<>();

    @Autowired
    private BeanFactory beanFactory;

    /**
     * <h3>扫描Mcp方法</h3>
     *
     * @param packages 包名
     */
    public static void scanMcpMethods(String @NotNull ... packages) {
        Reflections reflections;
        tools = new ArrayList<>();
        for (String pack : packages) {
            reflections = new Reflections(pack, Scanners.MethodsAnnotated);
            Set<Method> methods = reflections.getMethodsAnnotatedWith(McpMethod.class);
            methods.forEach(method -> {
                McpTool mcpTool = getTool(method);
                if (mcpTool != null) {
                    tools.add(mcpTool);
                    METHOD_MAP.put(mcpTool.getName(), method);
                }
            });
        }
        log.info("扫描到 {} 个Mcp方法", tools.size());
    }

    /**
     * <h3>获取McpTool</h3>
     *
     * @param method 方法
     * @return McpTool
     */
    private static @Nullable McpTool getTool(@NotNull Method method) {
        McpMethod annotation = method.getAnnotation(McpMethod.class);
        if (Objects.isNull(annotation)) {
            return null;
        }
        McpTool mcpTool = new McpTool();
        String mcpToolName = annotation.value();
        if (!StringUtils.hasText(mcpToolName)) {
            mcpToolName = method.getDeclaringClass().getSimpleName() + Constant.STRING_UNDERLINE + method.getName();
        }
        McpTool.InputSchema inputSchema = new McpTool.InputSchema();
        // 获取Method的形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        IntStream.range(0, parameterTypes.length).forEach(index -> {
            Class<?> parameterType = parameterTypes[index];

            String paramName = method.getParameters()[index].getName();

            // 添加为必须
            inputSchema.getRequired().add(paramName);

            // 参数的描述
            String paramDesc = ReflectUtil.getDescription(method.getParameters()[index]);
            Map<String, McpTool.InputSchema.Property> properties = inputSchema.getProperties();

            // 初始化一条
            McpTool.InputSchema.Property item = new McpTool.InputSchema.Property().setDescription(paramDesc);

            // 判断方法的类型是否为 String 数字 布尔
            if (parameterType.equals(String.class)) {
                properties.put(paramName, item.setType("string"));
            } else if (parameterType.equals(Boolean.class) || parameterType.equals(boolean.class)) {
                properties.put(paramName, item.setType("boolean"));
            } else if (Number.class.isAssignableFrom(parameterType)) {
                properties.put(paramName, item.setType("number"));
            }
            inputSchema.setProperties(properties);
        });
        mcpTool.setName(mcpToolName)
                .setDescription(ReflectUtil.getDescription(method))
                .setInputSchema(inputSchema);
        return mcpTool;
    }

    /**
     * <h3>获取SseEmitter</h3>
     *
     * @param uuid         uuid
     * @param expireSecond 超时时间 默认 {@code 秒}
     * @return SseEmitter
     */
    public static @NotNull SseEmitter getSseEmitter(String uuid, long expireSecond) {
        SseEmitter emitter = new SseEmitter(expireSecond * DateTimeUtil.MILLISECONDS_PER_SECOND);
        McpService.EMITTERS.put(uuid, emitter);

        // 每3s 发送ping
        TaskUtil.runAsync(() -> {
            while (true) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("ping").build());
                    //noinspection BusyWait
                    Thread.sleep(Duration.ofSeconds(3).toMillis());
                } catch (Exception e) {
                    break;
                }
            }
        });

        emitter.onCompletion(() -> McpService.EMITTERS.remove(uuid));
        emitter.onTimeout(() -> McpService.EMITTERS.remove(uuid));
        return emitter;
    }

    /**
     * <h3>获取SseEmitter</h3>
     *
     * @param uuid uuid
     * @return SseEmitter
     */
    public static @NotNull SseEmitter getSseEmitter(String uuid) {
        return getSseEmitter(uuid, 300);
    }

    /**
     * <h3>发送结果</h3>
     *
     * @param uuid uuid
     * @param id   id
     * @param data 数据
     * @return McpResponse
     * @throws McpException 异常
     */
    public static McpResponse emitResult(String uuid, Long id, Object data) throws McpException {
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setResult(data);
        return emit(uuid, response);
    }

    /**
     * <h3>发送错误</h3>
     *
     * @param uuid uuid
     * @param id   id
     * @param code 错误代码
     * @throws McpException 异常
     */
    public static void emitError(String uuid, Long id, @NotNull McpErrorCode code) throws McpException {
        emitError(uuid, id, code.getKey(), code.getLabel());
    }

    /**
     * <h3>发送错误</h3>
     *
     * @param uuid    uuid
     * @param id      id
     * @param code    错误代码
     * @param message 错误信息
     * @throws McpException 异常
     */
    public static void emitError(String uuid, Long id, Integer code, String message) throws McpException {
        McpResponse response = new McpResponse();
        response.setId(id);
        McpException error = new McpException();
        error.setCode(code).setMessage(message);
        response.setError(error);
        emit(uuid, response);
        throw error;
    }

    /**
     * <h3>发送错误</h3>
     *
     * @param uuid    uuid
     * @param id      id
     * @param message 错误信息
     * @throws McpException 异常
     */
    public static void emitError(String uuid, Long id, String message) throws McpException {
        emitError(uuid, id, McpErrorCode.InternalError.getKey(), message);
    }

    /**
     * <h3>发送</h3>
     *
     * @param uuid     uuid
     * @param response 响应
     * @return McpResponse
     * @throws McpException 异常
     */
    @Contract("_, _ -> param2")
    private static McpResponse emit(String uuid, McpResponse response) throws McpException {
        SseEmitter sseEmitter = EMITTERS.get(uuid);
        String string = Json.toString(response);
        if (Objects.nonNull(sseEmitter)) {
            try {
                sseEmitter.send(SseEmitter.event()
                        .name("message")
                        .data(string)
                );
            } catch (IOException e) {
                throw new McpException().setCode(McpErrorCode.InternalError.getKey()).setMessage(e.getMessage());
            }
        }
        return response;
    }

    /**
     * <h3>获取访问指定工具需要的权限</h3>
     *
     * @param mcpTool 工具
     * @return 权限标识
     */
    public static @NotNull String getPermissionIdentity(@NotNull McpTool mcpTool) {
        return DigestUtils.sha1Hex(mcpTool.getName() + mcpTool.getDescription());
    }

    /**
     * <h3>运行方法</h3>
     *
     * @param uuid            uuid
     * @param mcpMethods      方法
     * @param mcpRequest      请求
     * @param checkPermission 权限验证方法
     * @return 响应
     * @throws McpException 异常
     */
    public McpResponse run(String uuid, @NotNull McpMethods mcpMethods, McpRequest mcpRequest, Consumer<McpTool> checkPermission) throws McpException {
        McpResponse responseData;
        switch (mcpMethods) {
            case INITIALIZE:
                responseData = McpService.emitResult(uuid, mcpRequest.getId(), new McpInitializeData());
                break;
            case TOOLS_CALL:
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) mcpRequest.getParams();
                String methodName = params.get("name").toString();
                Method method = METHOD_MAP.get(methodName);
                if (Objects.isNull(method)) {
                    throw new McpException().setCode(McpErrorCode.MethodNotFound.getKey()).setMessage("Method not found");
                }
                McpTool mcpTool = getTool(method);
                if (Objects.isNull(mcpTool)) {
                    throw new McpException().setCode(McpErrorCode.MethodNotFound.getKey()).setMessage("McpTool not found");
                }
                Object callResult;
                try {
                    checkPermission.accept(mcpTool);
                    Class<?> declaringClass = method.getDeclaringClass();
                    Object bean = beanFactory.getBean(declaringClass);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

                    List<String> keys = new ArrayList<>(arguments.keySet());
                    Collections.sort(keys);

                    Map<String, Object> sortedArguments = keys.stream().collect(
                            Collectors.toMap(
                                    key -> key, arguments::get,
                                    (a, b) -> b, LinkedHashMap::new
                            )
                    );
                    Object[] args = sortedArguments.values().toArray();
                    callResult = method.invoke(bean, args);
                } catch (Exception e) {
                    if (e instanceof InvocationTargetException) {
                        callResult = ((InvocationTargetException) e).getTargetException().getMessage();
                    } else {
                        callResult = e.getMessage();
                    }
                }
                McpCallMethodResponse mcpCallMethodResponse = new McpCallMethodResponse();
                if (Objects.isNull(callResult) || !StringUtils.hasText(callResult.toString())) {
                    callResult = "操作成功";
                }
                mcpCallMethodResponse.getContent().add(
                        new McpCallMethodResponse.Text().setText(callResult.toString())
                );
                responseData = emitResult(uuid, mcpRequest.getId(), mcpCallMethodResponse);
                break;
            case TOOLS_LIST:
                responseData = McpService.emitResult(uuid, mcpRequest.getId(), Map.of(
                        "tools", McpService.tools
                ));
                break;
            default:
                throw new McpException().setCode(McpErrorCode.MethodNotFound.getKey()).setMessage("Method not found");
        }
        return responseData;
    }
}
