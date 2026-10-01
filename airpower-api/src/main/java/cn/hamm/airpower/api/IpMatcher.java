package cn.hamm.airpower.api;

import cn.hamm.airpower.core.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * <h1>IP 白名单匹配</h1>
 *
 * <p>白名单按换行、逗号或分号分隔，每条规则是一个 IP 字面量。</p>
 *
 * <p>规则与来源 IP 都先归一化再按字节比较，因此 {@code ::1} 与 {@code 0:0:0:0:0:0:0:1}
 * 命中同一条规则，{@code ::ffff:10.0.0.1} 也会与 {@code 10.0.0.1} 命中同一条规则。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
public class IpMatcher {
    /**
     * 规则分隔符
     */
    private static final String RULE_SEPARATOR = "[,\n;]";

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private IpMatcher() {
    }

    /**
     * 判断来源 IP 是否命中白名单
     *
     * @param whiteList 白名单，支持换行、逗号、分号分隔
     * @param ip        来源 IP
     * @return 是否命中
     * @apiNote 白名单为空或来源 IP 不是合法字面量时一律判为不命中。
     * 单条规则非法只跳过该条并告警，不影响其余规则——一条写错不应让整个 App 全部拒绝
     */
    public static boolean matches(@Nullable String whiteList, @Nullable String ip) {
        if (!StringUtil.hasText(whiteList)) {
            return false;
        }
        final byte[] target = toBytes(ip);
        if (null == target) {
            return false;
        }
        for (String rule : whiteList.split(RULE_SEPARATOR)) {
            final String trimmed = rule.trim();
            if (StringUtil.hasText(trimmed) && matchesRule(trimmed, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单条规则是否命中
     *
     * @param rule   规则
     * @param target 来源 IP 字节
     * @return 是否命中
     */
    private static boolean matchesRule(@NotNull String rule, byte @NotNull [] target) {
        final byte[] candidate = toBytes(rule);
        if (null == candidate) {
            log.warn("IP 白名单规则无法解析，已跳过：{}", rule);
            return false;
        }
        // 地址族不同（IPv4 对 IPv6）不可能是同一个地址
        return candidate.length == target.length && Arrays.equals(candidate, target);
    }

    /**
     * 归一化后转换为字节数组
     *
     * @param address IP 字符串
     * @return 字节数组，非法时返回 {@code null}
     */
    private static byte @Nullable [] toBytes(@Nullable String address) {
        return RequestUtil.toInetBytes(RequestUtil.parseAddress(address));
    }
}
