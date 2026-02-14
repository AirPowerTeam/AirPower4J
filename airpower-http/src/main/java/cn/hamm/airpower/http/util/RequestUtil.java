package cn.hamm.airpower.http.util;

import cn.hamm.airpower.core.StringUtil;
import cn.hamm.airpower.core.constant.HttpConstant;
import cn.hamm.airpower.core.exception.ServiceException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.hamm.airpower.core.constant.HttpConstant.ContentType.MULTIPART_FORM_DATA;
import static cn.hamm.airpower.core.constant.HttpConstant.Proxy.Header;

/**
 * <h1>请求工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class RequestUtil {
    /**
     * 常用 IP 反向代理 Header 头
     */
    private static final List<String> PROXY_IP_HEADERS = List.of(
            Header.X_FORWARDED_FOR,
            Header.PROXY_CLIENT_IP,
            Header.WL_PROXY_CLIENT_IP,
            Header.HTTP_CLIENT_IP,
            Header.HTTP_X_FORWARDED_FOR
    );

    /**
     * 多 IP 地址分隔符
     */
    private static final String IP_PROXY_SPLIT = ",";

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

            if (ipAddress.contains(IP_PROXY_SPLIT)) {
                ipAddress = ipAddress.split(IP_PROXY_SPLIT)[0].trim();
            }
            if (!Objects.equals(HttpConstant.LOCAL_IP_ADDRESS, ipAddress)) {
                return ipAddress;
            }
            // 根据网卡取本机配置的 IP
            InetAddress inet;
            inet = InetAddress.getLocalHost();
            ipAddress = inet.getHostAddress();
            if (isValidAddress(ipAddress)) {
                return ipAddress;
            }
            return ipAddress;
        } catch (Exception e) {
            throw new ServiceException("获取 IP 地址异常，" + e.getMessage());
        }
    }

    /**
     * 判断是否上传文件的请求类型头
     *
     * @param contentType 请求类型头
     * @return 判断结果
     */
    @Contract(value = "null -> false", pure = true)
    private static boolean isUploadFileContentType(String contentType) {
        return contentType != null && contentType.startsWith(MULTIPART_FORM_DATA);
    }

    /**
     * 是否是有效的 IP 地址
     *
     * @param ipAddress IP 地址
     * @return 判定结果
     */
    private static boolean isValidAddress(String ipAddress) {
        return Objects.nonNull(ipAddress)
                && StringUtil.hasText(ipAddress)
                && !HttpConstant.LOCAL_IP_ADDRESS.equalsIgnoreCase(ipAddress);
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
