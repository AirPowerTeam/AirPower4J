package cn.hamm.airpower.api;

import cn.hamm.airpower.core.StringUtil;
import cn.hamm.airpower.core.constant.HttpConstant;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.hamm.airpower.core.constant.HttpConstant.ContentType.MULTIPART_FORM_DATA;

/**
 * <h1>请求工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class RequestUtil {
    /**
     * 多 IP 地址分隔符
     */
    private static final String IP_SEPARATOR = ",";
    /**
     * Forwarded 头中键值对分隔符
     */
    private static final String FORWARDED_PAIR_SEPARATOR = ";";
    /**
     * Forwarded 头中来源地址的键
     */
    private static final String FORWARDED_FOR_KEY = "for=";
    /**
     * IPv4 映射地址前缀，形如 {@code ::ffff:192.168.1.1}
     */
    private static final String IPV4_MAPPED_PREFIX = "::ffff:";
    /**
     * 可信代理头
     */
    private static volatile String trustProxyHeader = "";

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
     * 获取请求的来源 IP 地址
     *
     * @param request 请求
     * @return 合法 IP 地址，无法解析时返回空字符串
     * @apiNote 优先读可信代理头并取链上<b>最右侧</b>的合法 IP，读不到才回退
     * {@link HttpServletRequest#getRemoteAddr()}，仍拿不到则返回空字符串，不抛异常。
     */
    public static @NotNull String getIpAddress(@NotNull HttpServletRequest request) {
        try {
            final String ip = parseHeader(trustProxyHeader, request.getHeader(trustProxyHeader));
            if (!ip.isEmpty()) {
                return ip;
            }
            // 读不到则回退 TCP 连接对端地址
            final String remoteAddress = parseAddress(request.getRemoteAddr());
            if (!remoteAddress.isEmpty()) {
                return remoteAddress;
            }
            return "";
        } catch (Exception e) {
            log.warn("获取请求 IP 异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 配置可信代理头
     *
     * @param header 传入空表示不信任任何代理头，仅使用 TCP 对端地址
     * @apiNote 只由 {@link cn.hamm.airpower.api.config.ApiConfig} 在启动时调用
     */
    public static void setTrustProxyHeader(@Nullable String header) {
        trustProxyHeader = header;
    }

    /**
     * 按代理头名称选择对应的解析方式
     *
     * @param header      代理头名
     * @param headerValue 原始请求头
     * @return 来源 IP，解析失败时返回空字符串
     */
    private static @NotNull String parseHeader(@NotNull String header, @Nullable String headerValue) {
        if (!StringUtil.hasText(headerValue)) {
            return "";
        }
        // Forwarded 是 RFC 7239 的键值对格式，其余均为逗号分隔的 IP 链
        return header.equalsIgnoreCase(HttpConstant.Proxy.Header.FORWARD)
                ? parseForwardedHeader(headerValue)
                : parseIpChainHeader(headerValue);
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
     * 解析逗号分隔的代理链请求头
     *
     * @param headerValue 原始请求头，形如 {@code 198.51.100.7, 203.0.113.9}，最右侧由离服务端最近的代理追加
     * @return 来源 IP，解析失败时返回空字符串
     */
    private static @NotNull String parseIpChainHeader(@Nullable String headerValue) {
        if (!StringUtil.hasText(headerValue)) {
            return "";
        }
        final String[] items = headerValue.split(IP_SEPARATOR);
        // 从右往左取：链上只有最右侧那一项由最近的代理写入，客户端填的前缀一律不可信
        for (int i = items.length - 1; i >= 0; i--) {
            final String ip = parseAddress(items[i]);
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        return "";
    }

    /**
     * 解析 RFC 7239 标准转发请求头
     *
     * @param headerValue 原始请求头，格式为 {@code for=192.0.2.60;proto=http, for="[2001:db8::1]:8080"}
     * @return 来源 IP，解析失败时返回空字符串
     */
    private static @NotNull String parseForwardedHeader(@Nullable String headerValue) {
        if (!StringUtil.hasText(headerValue)) {
            return "";
        }
        // 与逗号分隔的代理链同一取值方向，从右往左取
        final String[] elements = headerValue.split(IP_SEPARATOR);
        for (int i = elements.length - 1; i >= 0; i--) {
            for (String pair : elements[i].split(FORWARDED_PAIR_SEPARATOR)) {
                // 键值对前通常带有空格
                final String item = pair.trim();
                if (!item.regionMatches(true, 0, FORWARDED_FOR_KEY, 0, FORWARDED_FOR_KEY.length())) {
                    continue;
                }
                final String ip = parseAddress(item.substring(FORWARDED_FOR_KEY.length()));
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return "";
    }

    /**
     * 解析并规范化单个 IP 字符串
     *
     * @param raw 原始 IP 字符串
     * @return 合法 IP 地址
     */
    static @NotNull String parseAddress(@Nullable String raw) {
        if (!StringUtil.hasText(raw)) {
            return "";
        }
        String ip = raw.trim();
        // Forwarded 元素可能被双引号包裹：for="[2001:db8::1]:8080"
        if (ip.length() > 1 && ip.charAt(0) == '"' && ip.charAt(ip.length() - 1) == '"') {
            ip = ip.substring(1, ip.length() - 1).trim();
        }
        // 剥离端口：多个冒号视为 IPv6 地址本身
        if (ip.charAt(0) == '[') {
            final int end = ip.indexOf(']');
            if (end < 0) {
                return "";
            }
            ip = ip.substring(1, end);
        } else {
            final int colon = ip.indexOf(':');
            if (colon > 0 && colon == ip.lastIndexOf(':')) {
                ip = ip.substring(0, colon);
            }
        }
        // 剥离 IPv6 zone id：fe80::1%eth0
        final int zone = ip.indexOf('%');
        if (zone > 0) {
            ip = ip.substring(0, zone);
        }
        if (toInetBytes(ip) == null) {
            return "";
        }
        // 归一化 IPv4 映射地址：::ffff:192.168.1.1 -> 192.168.1.1
        if (ip.regionMatches(true, 0, IPV4_MAPPED_PREFIX, 0, IPV4_MAPPED_PREFIX.length())) {
            ip = ip.substring(IPV4_MAPPED_PREFIX.length());
        }
        return ip;
    }

    /**
     * 将 IP 字面量转换为字节数组
     *
     * @param ip IP 字面量
     * @return 字节数组，非法时返回 {@code null}
     */
    @Contract(value = "null -> null", pure = true)
    static byte @Nullable [] toInetBytes(@Nullable String ip) {
        if (!StringUtil.hasText(ip)) {
            return null;
        }
        final String address = ip.trim();
        if (address.indexOf(':') < 0) {
            return toIpv4Bytes(address);
        }
        final char first = address.charAt(0);
        final boolean literal = (first >= '0' && first <= '9') || (first >= 'a' && first <= 'f')
                || (first >= 'A' && first <= 'F') || first == ':';
        if (!literal) {
            return null;
        }
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 IPv4 字面量为 4 字节数组
     *
     * @param ip IPv4 字面量
     * @return 字节数组，非法时返回 {@code null}
     */
    @Contract(value = "null -> null", pure = true)
    private static byte @Nullable [] toIpv4Bytes(@Nullable String ip) {
        if (!isIpv4(ip)) {
            return null;
        }
        final byte[] bytes = new byte[4];
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            int end = ip.indexOf('.', start);
            if (end < 0) {
                end = ip.length();
            }
            bytes[i] = (byte) Integer.parseInt(ip.substring(start, end));
            start = end + 1;
        }
        return bytes;
    }

    /**
     * 校验 IPv4 字面量
     *
     * @param ip 待校验内容
     * @return 是否为 4 段、每段 0-255 且无前导零的 IPv4 字面量
     */
    @Contract(value = "null -> false", pure = true)
    private static boolean isIpv4(@Nullable String ip) {
        if (StringUtil.isEmpty(ip)) {
            return false;
        }
        int parts = 0;
        int start = 0;
        final int length = ip.length();
        for (int i = 0; i <= length; i++) {
            if (i != length && ip.charAt(i) != '.') {
                final char c = ip.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
                continue;
            }
            final int partLength = i - start;
            if (partLength < 1 || partLength > 3) {
                return false;
            }
            // 拒绝 010 这类可能被解析为八进制的歧义写法
            if (partLength > 1 && ip.charAt(start) == '0') {
                return false;
            }
            int value = 0;
            for (int j = start; j < i; j++) {
                value = value * 10 + (ip.charAt(j) - '0');
            }
            if (value > 255) {
                return false;
            }
            parts++;
            start = i + 1;
        }
        return parts == 4;
    }

    /**
     * 将 Map 参数转换为 QueryString
     *
     * @param map 参数
     * @return QueryString
     * @apiNote 不做 URL 编码，值需由调用方自行编码
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
    public static @NotNull String buildQueryUrl(@NotNull String url, Map<String, Object> map) {
        return url + "?" + mapToQueryString(map);
    }
}
