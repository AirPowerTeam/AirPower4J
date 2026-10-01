package cn.hamm.airpower.redis;

import cn.hamm.airpower.core.DateTimeUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>Redis 配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.redis")
public class RedisConfig {
    /**
     * 缓存过期时间
     */
    private int cacheExpireSecond = DateTimeUtil.SECOND_PER_MINUTE;

    /**
     * Redis 前缀
     */
    private String prefix = "airpower:";

    /**
     * 锁的过期时间(租约时长)
     *
     * @apiNote 单位毫秒，到期后锁会被 Redis 自动释放，避免持有者宕机造成死锁
     */
    private Long lockTimeout = 60 * 1000L;

    /**
     * 获取锁时的最长等待时间
     *
     * @apiNote 单位毫秒，小于等于 {@code 0} 时只尝试获取一次。
     * <p>
     * 注意：这是「等待时间」，与 {@link #lockTimeout}「租约时长」是两个不同的概念。
     */
    private Long lockWaitTimeout = 3 * 1000L;

    /**
     * 是否开启锁的自动续期(看门狗)
     *
     * @apiNote 仅对 {@code runWithLock} 生效：任务执行时间超过租约时长时自动续期，避免任务执行到一半锁被提前释放
     */
    private Boolean lockWatchdog = true;

    /**
     * 锁的自动续期周期
     *
     * @apiNote 单位毫秒，小于等于 {@code 0} 或大于租约时长的一半时，按「租约时长 / 3」自动计算
     */
    private Long lockWatchdogInterval = 0L;
}
