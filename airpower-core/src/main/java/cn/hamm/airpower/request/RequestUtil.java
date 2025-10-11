package cn.hamm.airpower.request;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.hamm.airpower.exception.ServiceError.FORBIDDEN;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

/**
 * <h1>请求工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class RequestUtil {
    /**
     * 本机
     */
    public static final String LOCAL_IP_ADDRESS = "127.0.0.1";

    /**
     * 缓存的 REQUEST_ID
     */
    public static final String REQUEST_ID = "X-Request-ID";

    /**
     * 常用 IP 反向代理 Header 头
     */
    private static final List<String> PROXY_IP_HEADERS = List.of(
            "x-forwarded-for", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    );

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private RequestUtil() {
    }

    /**
     * 判断是否是上传请求
     *
     * @param request 请求
     * @return 是否是上传请求
     */
    public static boolean isUploadRequest(@NotNull HttpServletRequest request) {
        return isUploadFileContentType(request.getContentType());
    }

    /**
     * 判断是否是上传请求
     *
     * @param request 请求
     * @return 是否是上传请求
     */
    public static boolean isUploadRequest(@NotNull ServletRequest request) {
        return isUploadFileContentType(request.getContentType());
    }

    /**
     * 获取请求的真实 IP 地址
     *
     * @param request 请求
     * @return IP 地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress;
        try {
            for (String ipHeader : PROXY_IP_HEADERS) {
                ipAddress = request.getHeader(ipHeader);
                if (Objects.equals(ipAddress, "unknown")) {
                    continue;
                }
                if (isValidAddress(ipAddress)) {
                    return getIpAddressFromMultiIp(ipAddress);
                }
            }

            ipAddress = request.getRemoteAddr();
            if (!Objects.equals(LOCAL_IP_ADDRESS, ipAddress)) {
                return ipAddress;
            }
            // 根据网卡取本机配置的IP
            InetAddress inet;
            inet = InetAddress.getLocalHost();
            ipAddress = inet.getHostAddress();
            if (isValidAddress(ipAddress)) {
                return ipAddress;
            }
            return ipAddress;
        } catch (Exception exception) {
            FORBIDDEN.show("获取IP地址异常");
        }
        return LOCAL_IP_ADDRESS;
    }

    /**
     * 判断是否上传文件的请求类型头
     *
     * @param contentType 请求类型头
     * @return 判断结果
     */
    @Contract(value = "null -> false", pure = true)
    private static boolean isUploadFileContentType(String contentType) {
        return contentType != null && contentType.startsWith(MULTIPART_FORM_DATA_VALUE);
    }

    /**
     * 是否是有效的IP地址
     *
     * @param ipAddress IP地址
     * @return 判定结果
     */
    private static boolean isValidAddress(String ipAddress) {
        return Objects.nonNull(ipAddress)
                && StringUtils.hasText(ipAddress)
                && !LOCAL_IP_ADDRESS.equalsIgnoreCase(ipAddress);
    }

    /**
     * 多 IP 获取真实 IP 地址
     *
     * @param ipAddress 原始 IP 地址
     * @return 处理之后的真实 IP
     */
    private static @NotNull String getIpAddressFromMultiIp(@NotNull String ipAddress) {
        final String split = ",";
        if (ipAddress.indexOf(split) > 0) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(split));
        }
        return ipAddress;
    }

    /**
     * 将 Map 参数转换为 QueryString
     *
     * @param map 参数
     * @return QueryString
     */
    public static String mapToQueryString(@NotNull Map<String, Object> map) {
        return map.entrySet().stream()
                .map(item -> item.getKey() + "=" + item.getValue().toString())
                .collect(Collectors.joining("&"));
    }

    /**
     * 构建 Query 请求的 URL
     *
     * @param url URL
     * @param map 参数
     * @return 完整的 URL
     */
    public static @NotNull String buildQueryUrl(String url, Map<String, Object> map) {
        return url + "?" + mapToQueryString(map);
    }
}
