package cn.hamm.airpower.api;

import cn.hamm.airpower.core.StringUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     * 默认可信代理：仅本机回环地址
     *
     * @apiNote 默认信任 Nginx 与应用同机部署的场景，其余代理需显式配置
     */
    public static final List<String> DEFAULT_TRUSTED_PROXIES = List.of("127.0.0.1/32", "::1/128");

    /**
     * 常用 IP 反向代理 Header 头，按可信度从高到低排列
     */
    private static final List<String> PROXY_IP_HEADERS = List.of(
            Header.FORWARD,
            Header.X_FORWARDED_FOR,
            Header.X_REAL_IP,
            Header.PROXY_CLIENT_IP,
            Header.WL_PROXY_CLIENT_IP,
            Header.HTTP_CLIENT_IP,
            Header.HTTP_X_FORWARDED_FOR
    );

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
     * 无法解析出 IP 时返回的占位值
     */
    private static final String UNKNOWN_IP_ADDRESS = "unknown";

    /**
     * 可信代理网段快照
     *
     * @apiNote 以 volatile + 不可变列表保证配置变更对所有请求线程可见
     */
    private static volatile List<IpRange> trustedProxies = parseTrustedProxies(DEFAULT_TRUSTED_PROXIES);

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
     * <p>反向代理头（{@code Forwarded} / {@code X-Forwarded-For} 等）由客户端随意构造，
     * 因此仅在 <b>TCP 连接的对端地址命中可信代理网段</b> 时才会解析，并且自右向左逐个剥离可信代理节点，
     * 取第一个非可信地址作为来源 IP。客户端在 {@code X-Forwarded-For} 左侧伪造的地址会被自动丢弃。
     *
     * <p>未配置可信代理（或对端不是可信代理）时，仅返回 {@code getRemoteAddr()}，
     * 避免伪造请求头绕过 IP 白名单、限流与风控。
     *
     * @param request 请求
     * @return 合法 IP 地址，无法解析时返回 {@code unknown}
     */
    public static @NotNull String getIpAddress(@NotNull HttpServletRequest request) {
        final List<IpRange> proxies = trustedProxies;
        try {
            final String remoteAddress = parseAddress(request.getRemoteAddr());
            if (remoteAddress.isEmpty()) {
                return UNKNOWN_IP_ADDRESS;
            }
            if (proxies.isEmpty() || !isTrustedProxy(remoteAddress, proxies)) {
                return remoteAddress;
            }
            for (String header : PROXY_IP_HEADERS) {
                final String headerValue = request.getHeader(header);
                final String ip = Header.FORWARD.equals(header)
                        ? parseForwardedHeader(headerValue, proxies)
                        : parseIpChainHeader(headerValue, proxies);
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
            log.debug("可信代理 [{}] 转发的请求头中未解析出合法 IP，回退为连接对端地址", remoteAddress);
            return remoteAddress;
        } catch (Exception e) {
            log.warn("获取请求 IP 异常: {}", e.getMessage());
            return UNKNOWN_IP_ADDRESS;
        }
    }

    /**
     * 配置可信代理地址
     *
     * @param proxies 可信代理的 IP 或 CIDR 网段，传入空集合表示不信任任何代理头
     * @apiNote 通常由 {@link cn.hamm.airpower.api.config.IpConfig} 在启动时调用
     */
    public static void setTrustedProxies(@Nullable List<String> proxies) {
        trustedProxies = parseTrustedProxies(proxies);
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
     * 解析多级代理链请求头
     *
     * @param headerValue 原始请求头，格式为 {@code client, proxy1, proxy2}
     * @param proxies     可信代理网段
     * @return 真实来源 IP，解析失败时返回空字符串
     */
    private static @NotNull String parseIpChainHeader(@Nullable String headerValue, @NotNull List<IpRange> proxies) {
        if (!StringUtil.hasText(headerValue)) {
            return "";
        }
        // 自右向左剥离可信代理节点，第一个非可信地址即真实来源
        final String[] items = headerValue.split(IP_SEPARATOR);
        for (int i = items.length - 1; i >= 0; i--) {
            final String ip = parseAddress(items[i]);
            if (ip.isEmpty() || isTrustedProxy(ip, proxies)) {
                continue;
            }
            return ip;
        }
        return "";
    }

    /**
     * 解析 RFC 7239 标准转发请求头
     *
     * @param headerValue 原始请求头，格式为 {@code for=192.0.2.60;proto=http, for="[2001:db8::1]:8080"}
     * @param proxies     可信代理网段
     * @return 真实来源 IP，解析失败时返回空字符串
     */
    private static @NotNull String parseForwardedHeader(@Nullable String headerValue, @NotNull List<IpRange> proxies) {
        if (!StringUtil.hasText(headerValue)) {
            return "";
        }
        // 每个代理追加一个元素，最右侧元素由最靠近服务的代理写入
        final String[] elements = headerValue.split(IP_SEPARATOR);
        for (int i = elements.length - 1; i >= 0; i--) {
            for (String pair : elements[i].split(FORWARDED_PAIR_SEPARATOR)) {
                // 元素之间以逗号分隔，键值对前通常带有空格
                final String item = pair.trim();
                if (!item.regionMatches(true, 0, FORWARDED_FOR_KEY, 0, FORWARDED_FOR_KEY.length())) {
                    continue;
                }
                final String ip = parseAddress(item.substring(FORWARDED_FOR_KEY.length()));
                if (ip.isEmpty() || isTrustedProxy(ip, proxies)) {
                    continue;
                }
                return ip;
            }
        }
        return "";
    }

    /**
     * 解析并规范化单个 IP 字符串
     *
     * @param raw 原始 IP 字符串
     * @return 合法 IP 地址
     * @apiNote 兼容引号包裹、端口后缀、IPv6 zone id 与 IPv4 映射地址，
     * 并严格校验字面量格式，避免非法内容（含换行、{@code unknown} 等占位值）流入日志或数据库。
     */
    private static @NotNull String parseAddress(@Nullable String raw) {
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
     * IP 是否命中可信代理网段
     *
     * @param ip      IP 地址
     * @param proxies 可信代理网段
     * @return 判定结果
     */
    @Contract(pure = true)
    private static boolean isTrustedProxy(@NotNull String ip, @NotNull List<IpRange> proxies) {
        final byte[] address = toInetBytes(ip);
        if (address == null) {
            return false;
        }
        for (IpRange range : proxies) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 IP 字面量转换为字节数组
     *
     * <p>IPv4 采用自研解析，避免 {@code InetAddress} 兼容 {@code 1.2.3} 等简写与八进制歧义写法；
     * IPv6 仅在首字符为十六进制字符或冒号时交给 {@code InetAddress} 解析，确保不会触发 DNS 查询。
     *
     * @param ip IP 字面量
     * @return 字节数组，非法时返回 {@code null}
     */
    @Contract(value = "null -> null", pure = true)
    private static byte @Nullable [] toInetBytes(@Nullable String ip) {
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
     * 解析可信代理配置
     *
     * @param proxies 可信代理的 IP 或 CIDR 网段
     * @return 不可变的可信代理网段列表
     */
    private static @NotNull List<IpRange> parseTrustedProxies(@Nullable List<String> proxies) {
        if (proxies == null || proxies.isEmpty()) {
            return List.of();
        }
        final List<IpRange> ranges = new ArrayList<>(proxies.size());
        for (String proxy : proxies) {
            if (!StringUtil.hasText(proxy)) {
                continue;
            }
            String value = proxy.trim();
            int prefix = -1;
            final int slash = value.indexOf('/');
            if (slash > 0) {
                try {
                    prefix = Integer.parseInt(value.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    log.warn("可信代理 [{}] 的掩码不合法，已忽略", proxy);
                    continue;
                }
                value = value.substring(0, slash).trim();
            }
            final byte[] network = toInetBytes(parseAddress(value));
            if (network == null) {
                log.warn("可信代理 [{}] 不是合法的 IP 或网段，已忽略", proxy);
                continue;
            }
            final int max = network.length * Byte.SIZE;
            if (prefix < 0) {
                prefix = max;
            }
            if (prefix > max) {
                log.warn("可信代理 [{}] 的掩码超出地址长度，已忽略", proxy);
                continue;
            }
            ranges.add(new IpRange(maskNetwork(network, prefix), prefix));
        }
        return List.copyOf(ranges);
    }

    /**
     * 按前缀长度将网络地址的主机位清零
     *
     * @param network 网络地址
     * @param prefix  前缀长度（bit）
     * @return 对齐后的网络地址
     */
    private static byte @NotNull [] maskNetwork(byte @NotNull [] network, int prefix) {
        final byte[] masked = network.clone();
        for (int i = prefix; i < masked.length * Byte.SIZE; i++) {
            masked[i / Byte.SIZE] &= (byte) ~(1 << (7 - i % Byte.SIZE));
        }
        return masked;
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
    public static @NotNull String buildQueryUrl(@NotNull String url, Map<String, Object> map) {
        return url + "?" + mapToQueryString(map);
    }

    /**
     * IP 网段
     *
     * @param network 按掩码对齐后的网络地址
     * @param prefix  前缀长度（bit）
     */
    private record IpRange(byte[] network, int prefix) {
        /**
         * 判断 IP 是否落在本网段内
         *
         * @param address IP 字节数组
         * @return 判定结果
         */
        boolean contains(byte[] address) {
            if (network.length != address.length) {
                return false;
            }
            for (int i = 0; i < prefix; i++) {
                final int mask = 1 << (7 - i % Byte.SIZE);
                if ((network[i / Byte.SIZE] & mask) != (address[i / Byte.SIZE] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
