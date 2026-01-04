package cn.hamm.airpower.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <h1>Host 工具类</h1>
 *
 * @author Hamm.cn
 */
public class HostUtil {
    /**
     * 获取服务器主机名的完整方法
     */
    public static String getHostName() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            if (isValidHostname(hostname)) {
                return hostname;
            }
        } catch (UnknownHostException e) {
            // 忽略异常，继续尝试其他方法
        }

        // 尝试系统属性
        String hostname = System.getProperty("hostname");
        if (isValidHostname(hostname)) {
            return hostname;
        }
        return getHostnameFromEnvironment();
    }

    /**
     * 从环境变量获取主机名
     */
    private static @Nullable String getHostnameFromEnvironment() {
        // Windows
        String hostname = System.getenv("COMPUTERNAME");
        if (isValidHostname(hostname)) {
            return hostname;
        }
        // Linux/Unix/Mac Docker
        hostname = System.getenv("HOSTNAME");
        if (isValidHostname(hostname)) {
            return hostname;
        }
        return null;
    }

    /**
     * 验证主机名是否有效
     */
    @Contract("null -> false")
    private static boolean isValidHostname(@Nullable String hostname) {
        return hostname != null && !hostname.trim().isEmpty();
    }
}
