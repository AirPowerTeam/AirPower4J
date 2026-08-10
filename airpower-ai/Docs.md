# airpower-ai 使用文档

> AirPower AI 模块 - 提供 OpenAI 兼容协议的同步 / 流式对话、MCP 工具注册与调用、Spring Boot 自动装配能力。

## 一、模块定位

`airpower-ai` 是 AirPower4J 框架的 AI
扩展模块，对外屏蔽了大模型厂商差异（默认适配 `https://api.siliconflow.cn/v1/chat/completions`），并提供了完整的 **MCP（Model
Context Protocol）JSON-RPC 2.0** 服务端实现，可被任意支持 MCP 协议的标准客户端调用。

主要能力：

| 能力           | 说明                                                               |
|----------------|--------------------------------------------------------------------|
| 大模型同步调用 | `Ai.request(AiRequest)` 直接获取完整响应                           |
| 大模型流式调用 | `Ai.requestStream(...)` / `Ai.requestStreamRaw(...)` 支持 SSE 输出 |
| 多轮对话       | `AiRequest.prompt(...)` + `addMessage(...)` 链式构造消息列表       |
| MCP 工具扫描   | 基于 `@McpMethod` 注解自动扫描并生成 `tools/list` 元数据           |
| MCP 工具调用   | `McpService.run(...)` 提供 `tools/call` JSON-RPC 调用入口          |
| MCP 权限校验   | 通过 `Consumer<McpTool>` 钩子接入 RBAC 体系                        |

## 二、引入依赖

```xml
<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-ai</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

> 父 POM 已统一管理 `airpower-core`、`airpower-exception`、`spring-webmvc`、`commons-codec`、`reflections` 等依赖。

## 三、应用配置

所有配置以 `airpower.ai` 为前缀：

```yaml
airpower:
  ai:
    # OpenAI 兼容协议的 chat/completions 地址
    url: https://api.siliconflow.cn/v1/chat/completions
    # API Key（必填，建议通过环境变量注入）
    key: ${SILICONFLOW_API_KEY}
    # 默认模型
    model: Qwen/Qwen3-8B
    # 单次请求最大 token
    max-token: 4096
    # 是否启用「思考」模式（如 Qwen3 的 enable_thinking）
    enable-thinking: false
```

源码参见 [AiConfig.java](src/main/java/cn/hamm/airpower/ai/AiConfig.java)。

## 四、同步对话

```java
import cn.hamm.airpower.ai.Ai;
import cn.hamm.airpower.ai.model.AiMessage;
import cn.hamm.airpower.ai.model.AiRequest;
import cn.hamm.airpower.ai.model.AiResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final Ai ai;

    public ChatService(Ai ai) {
        this.ai = ai;
    }

    public String chat(String userInput) {
        AiRequest request = AiRequest.prompt("你是一名严谨的 Java 架构师")
                .addMessage(AiMessage.AiRole.USER, userInput);

        AiResponse response = ai.request(request);
        return response.getResponseMessage();
    }
}
```

要点：

- `AiRequest.prompt(String)` 会写入一条 `system` 消息，作为人设或提示词。
- `addMessage(role, content)` 支持任意角色，包括 `AiRole.USER` / `AiRole.ASSISTANT`。
- `AiResponse.getResponseMessage()` 同步模式下取 `choices[0].message.content`；`getStreamMessage()` 取
  `choices[0].delta.content` 用于流式。

## 五、流式对话（SSE）

### 5.1 仅输出原始 SSE 行

```java
return ai.requestStreamRaw(request);
```

适用于「透传到大模型原生 SSE 协议」的场景，前端可直接消费。

### 5.2 输出结构化 AiStream

```java
return ai.requestStream(request, stream -> {
    if (Boolean.TRUE.equals(stream.getIsDone())) {
        return "data: [DONE]\n\n";
    }
    return "data: " + stream.getStreamMessage() + "\n\n";
});
```

`AiStream` 会自动解析每一行：

| 字段                   | 含义                         |
|------------------------|------------------------------|
| `isDone`               | 是否为 `data: [DONE]` 终止帧 |
| `response`             | 当前帧的 `AiResponse` 对象   |
| `getStreamMessage()`   | 取增量消息内容               |
| `getResponseMessage()` | 取完整消息内容（流式帧为空） |

> 内部基于 `Java 11 HttpClient` + `InputStream` 流式读取，并以 `text/event-stream;charset=UTF-8`
> 输出，参考 [Ai.java](src/main/java/cn/hamm/airpower/ai/Ai.java)。

## 六、MCP 工具协议

### 6.1 启用扫描

在任意 `@Configuration` 类中扫描目标包，框架使用 `reflections` 在启动期一次性扫描并缓存到 `McpService.METHOD_MAP` 与
`tools`：

```java
@Configuration
public class McpConfig {
    public McpConfig() {
        McpService.scanMcpMethods("com.example.app.mcp");
    }
}
```

### 6.2 编写 MCP 方法

```java
package com.example.app.mcp;

import cn.hamm.airpower.ai.mcp.exception.McpErrorCode;
import cn.hamm.airpower.ai.mcp.method.McpMethod;
import cn.hamm.airpower.ai.mcp.method.McpOptional;
import org.springframework.stereotype.Component;

@Component
public class SystemTools {

    @McpMethod("currentTime")
    public String currentTime() {
        return java.time.LocalDateTime.now().toString();
    }

    @McpMethod("searchUser")
    public Object searchUser(
            @cn.hamm.airpower.core.annotation.Description("用户关键字") String keyword,
            @McpOptional Integer pageSize
    ) {
        if (keyword == null || keyword.isBlank()) {
            McpErrorCode.InvalidParams.show("keyword 不能为空");
        }
        // 省略具体查询逻辑
        return java.util.Map.of("keyword", keyword, "pageSize", pageSize);
    }
}
```

约束：

- 形参只支持 `String`、`Boolean`、`Number` 三类，复杂结构请改用 JSON 字符串自行解析。
- 未标记 `@McpOptional` 的参数会被登记为 `required`，模型必须传入。
- 方法需托管在 Spring 容器中（`@Component` / `@Service`），`McpService` 会通过 `BeanFactory` 获取 Bean 实例。

### 6.3 暴露 JSON-RPC 入口

框架不强制绑定传输层，业务侧可在任意控制器里调用：

```java
@PostMapping("/mcp")
public McpResponse handleMcp(@RequestBody McpRequest request,
                              @cn.hamm.airpower.api.annotation.ApiHeader Long userId) {
    AccessTokenUtil.VerifiedToken token = accessTokenService.verify(userId);
    return mcpService.run(token, request, mcpTool -> {
        // 自定义权限校验：抛异常即拒绝
        if (!rbac.hasPermission(userId, mcpTool)) {
            McpErrorCode.MethodNotFound.show("无权限: " + mcpTool.getName());
        }
    });
}
```

支持的方法：

| method       | 行为                                               |
|--------------|----------------------------------------------------|
| `initialize` | 返回 MCP 协议版本 + 服务能力 + `McpServerInfo`     |
| `tools/list` | 返回 `scanMcpMethods` 收集到的工具列表             |
| `tools/call` | 通过反射执行对应方法，并包装为 `McpResponseResult` |

错误码枚举见 [McpErrorCode.java](src/main/java/cn/hamm/airpower/ai/mcp/exception/McpErrorCode.java)，符合 JSON-RPC 2.0
标准（`-32700` ~ `-32603`）。

### 6.4 客户端请求示例

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "currentTime",
    "arguments": {}
  }
}
```

响应：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{ "type": "text", "text": "2026-08-10T15:00:00" }],
    "isError": false
  }
}
```

## 七、关键类参考

| 类                                                                                                     | 路径                                             | 说明                          |
|--------------------------------------------------------------------------------------------------------|--------------------------------------------------|-------------------------------|
| `Ai`                                                                                                   | `cn.hamm.airpower.ai.Ai`                         | 大模型客户端，支持同步 / 流式 |
| `AiConfig`                                                                                             | `cn.hamm.airpower.ai.AiConfig`                   | 配置绑定 `airpower.ai.*`      |
| `AiRequest` / `AiResponse` / `AiStream` / `AiMessage`                                                  | `cn.hamm.airpower.ai.model.*`                    | 请求 / 响应 / 流式帧 / 消息体 |
| `McpService`                                                                                           | `cn.hamm.airpower.ai.mcp.McpService`             | JSON-RPC 工具调用核心         |
| `McpMethod` / `McpOptional`                                                                            | `cn.hamm.airpower.ai.mcp.method.*`               | 方法 / 参数注解               |
| `McpMethods`                                                                                           | `cn.hamm.airpower.ai.mcp.method.McpMethods`      | 内置 method 枚举              |
| `McpRequest` / `McpResponse` / `McpResponseResult` / `McpTool` / `McpServerInfo` / `McpInitializeData` | `cn.hamm.airpower.ai.mcp.model.*`                | JSON-RPC 数据模型             |
| `McpErrorCode`                                                                                         | `cn.hamm.airpower.ai.mcp.exception.McpErrorCode` | JSON-RPC 错误码               |
| `Auto`                                                                                                 | `cn.hamm.airpower.ai.Auto`                       | `@AutoConfiguration` 装配入口 |

## 八、常见问题

1. **大模型返回 401 / 403？** 检查 `airpower.ai.key` 是否正确，以及 `url` 是否匹配对应厂商。
2. **流式响应中断？** 默认 `connection-timeout` 未设置，使用 JDK 默认值；如需控制，可在自定义实现中替换
   `HttpClient.newBuilder()`。
3. **MCP 工具被调用时抛 `McpTool not found`？** 确认 `scanMcpMethods` 的包路径覆盖了工具类，并已注册到 Spring 容器。
4. **如何在 MCP 调用时记录审计日志？** 第三个参数 `Consumer<McpTool> checkPermission` 同时适合挂审计钩子，抛异常即终止调用。