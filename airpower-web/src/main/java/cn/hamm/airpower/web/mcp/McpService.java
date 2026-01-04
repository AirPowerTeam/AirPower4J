package cn.hamm.airpower.web.mcp;

import cn.hamm.airpower.core.ReflectUtil;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.web.mcp.exception.McpErrorCode;
import cn.hamm.airpower.web.mcp.method.McpMethod;
import cn.hamm.airpower.web.mcp.method.McpMethods;
import cn.hamm.airpower.web.mcp.method.McpOptional;
import cn.hamm.airpower.web.mcp.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <h1>MCP 服务</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class McpService {
    /**
     * 方法列表
     */
    private final static ConcurrentMap<String, Method> METHOD_MAP = new ConcurrentHashMap<>();

    /**
     * 工具列表
     */
    public static List<McpTool> tools = new ArrayList<>();

    @Autowired
    private BeanFactory beanFactory;

    /**
     * 扫描 MCP 方法
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
        log.info("扫描到 {} 个 MCP 方法", tools.size());
    }

    /**
     * 获取 MCP 工具
     *
     * @param method 方法
     * @return MCP 工具
     */
    private static @Nullable McpTool getTool(@NotNull Method method) {
        McpMethod annotation = method.getAnnotation(McpMethod.class);
        if (Objects.isNull(annotation)) {
            return null;
        }
        McpTool mcpTool = new McpTool();
        String mcpToolName = annotation.value();
        if (!StringUtils.hasText(mcpToolName)) {
            mcpToolName = method.getDeclaringClass().getSimpleName() + "_" + method.getName();
        }
        McpTool.InputSchema inputSchema = new McpTool.InputSchema();
        // 获取 Method 的形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        IntStream.range(0, parameterTypes.length).forEach(index -> {
            Class<?> parameterType = parameterTypes[index];

            String paramName = method.getParameters()[index].getName();

            McpOptional mcpOptional = method.getParameters()[index].getAnnotation(McpOptional.class);
            if (Objects.isNull(mcpOptional)) {
                // 没有标记可选属性的注解 则为必须属性
                inputSchema.getRequired().add(paramName);
            }

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
     * 获取访问指定工具需要的权限
     *
     * @param mcpTool 工具
     * @return 权限标识
     */
    public static @NotNull String getPermissionIdentity(@NotNull McpTool mcpTool) {
        return DigestUtils.sha1Hex(mcpTool.getName() + mcpTool.getDescription());
    }

    /**
     * 运行 MCP 服务
     *
     * @param mcpRequest      请求
     * @param checkPermission 检查权限
     * @return McpResponse 响应
     * @throws ServiceException ServiceException
     */
    public final @Nullable McpResponse run(@NotNull McpRequest mcpRequest, Consumer<McpTool> checkPermission) throws ServiceException {
        return this.run(mcpRequest, checkPermission, new McpServerInfo());
    }

    /**
     * 运行 MCP 服务
     *
     * @param mcpRequest      请求
     * @param checkPermission 检查权限
     * @param mcpServerInfo   服务信息
     * @return McpResponse 响应
     * @throws ServiceException ServiceException
     */
    public final @Nullable McpResponse run(@NotNull McpRequest mcpRequest, Consumer<McpTool> checkPermission, McpServerInfo mcpServerInfo) throws ServiceException {
        McpResponse responseData = new McpResponse();
        responseData.setId(mcpRequest.getId());
        McpMethods mcpMethods = Arrays.stream(McpMethods.values())
                .filter(value -> value.getLabel().equals(mcpRequest.getMethod()))
                .findFirst()
                .orElse(null);
        log.info("MCP 请求方法: {}，参数: {}", mcpRequest.getMethod(), mcpRequest.getParams());
        McpErrorCode.MethodNotFound.whenNull(mcpMethods);
        switch (mcpMethods) {
            case INITIALIZE:
                return responseData.setResult(new McpInitializeData(mcpServerInfo));
            case TOOLS_CALL:
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) mcpRequest.getParams();
                String methodName = params.get("name").toString();
                Method method = METHOD_MAP.get(methodName);
                McpErrorCode.MethodNotFound.whenNull(method);
                McpTool mcpTool = getTool(method);
                McpErrorCode.MethodNotFound.whenNull(mcpTool, "McpTool not found");
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
                if (Objects.isNull(callResult) || !StringUtils.hasText(callResult.toString())) {
                    callResult = "操作成功";
                }
                return responseData.setResult(new McpResponseResult().addTextContent(callResult.toString()));
            case TOOLS_LIST:
                return responseData.setResult(Map.of(
                        "tools", McpService.tools
                ));
            default:
                McpErrorCode.MethodNotFound.show();
                return null;
        }
    }
}
