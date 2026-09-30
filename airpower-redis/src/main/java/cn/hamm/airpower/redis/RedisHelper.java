package cn.hamm.airpower.redis;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.RootModel;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.interfaces.IEntity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static cn.hamm.airpower.exception.Errors.DATABASE_CONCURRENT_ERROR;
import static cn.hamm.airpower.exception.Errors.REDIS_ERROR;

/**
 * <h1>Redis 封装类</h1>
 *
 * @author Hamm.cn
 */
@Component
@Slf4j
public class RedisHelper {
    /**
     * 全局锁的 key
     */
    private static final String GLOBAL_LOCK_KEY = "GLOBAL_LOCK";

    /**
     * 原子释放锁的脚本
     *
     * @apiNote 「比对 + 删除」在 Redis 服务端一次完成，避免 GET 与 DEL 之间锁已过期而误删他人重新获取到的锁
     */
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0""", Long.class);

    /**
     * 原子续期的脚本
     *
     * @apiNote 仅当锁仍由当前持有者持有时才重置过期时间，参数为「毫秒」
     */
    private static final RedisScript<Long> RENEW_SCRIPT = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0""", Long.class);

    /**
     * 获取锁时的最小重试间隔(毫秒)
     */
    private static final long MIN_RETRY_INTERVAL = 5L;

    /**
     * 获取锁时的最大重试间隔(毫秒)
     */
    private static final long MAX_RETRY_INTERVAL = 50L;

    /**
     * 默认的锁租约时长(毫秒)
     *
     * @apiNote 配置缺失或非法时使用
     */
    private static final long DEFAULT_LEASE_TIMEOUT = 60 * 1000L;

    /**
     * 清空数据时每批删除的 key 数量
     */
    private static final int DELETE_BATCH_SIZE = 500;

    /**
     * 清空数据时每次 SCAN 游标返回的 key 数量
     */
    private static final int SCAN_COUNT = 1000;

    /**
     * JPA 动态代理类的名字特征
     */
    private static final String HIBERNATE_PROXY = "$$HibernateProxy";

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private RedisConfig redisConfig;

    /**
     * 本组件独占的 {@link RedisTemplate}
     *
     * @apiNote 不复用、也不修改 Spring 容器中共享的 redisTemplate，避免污染其他模块的序列化配置
     */
    private volatile RedisTemplate<String, Object> redisTemplate;

    /**
     * 锁的自动续期调度器
     */
    private volatile ScheduledExecutorService lockRenewExecutor;

    /**
     * 加锁运行任务
     *
     * @param key  锁的 key
     * @param task 任务
     * @apiNote 任务执行期间自动续期，任务结束后自动释放锁
     * @see #lock(String)
     * @see #releaseLock(Lock)
     */
    public final void runWithLock(String key, Runnable task) {
        REDIS_ERROR.whenNull(task, "加锁执行任务失败，传入的任务为空");
        Lock lock = lock(key);
        ScheduledFuture<?> renewTask = startRenew(lock);
        try {
            task.run();
        } catch (Exception e) {
            log.error("加锁执行任务失败, key={}, error={}", key, e.getMessage(), e);
            throw e;
        } finally {
            stopRenew(renewTask);
            // 释放锁失败只记录日志，绝不能顶掉业务异常
            releaseLockQuietly(lock);
        }
    }

    /**
     * 加锁运行任务
     *
     * @param task 任务
     * @apiNote 可根据下列方法自行实现获取和释放锁
     * @see #lock(String)
     * @see #releaseLock(Lock)
     */
    public final void runWithLock(Runnable task) {
        runWithLock(GLOBAL_LOCK_KEY, task);
    }

    /**
     * 自增
     *
     * @param key   自增 key
     * @param delta 增量
     * @return 值
     */
    public final long increment(String key, long delta) {
        try {
            ValueOperations<String, Object> stringObjectValueOperations = getRedisTemplate().opsForValue();
            Long increment = stringObjectValueOperations.increment(getKey(key), delta);
            REDIS_ERROR.whenNull(increment, "自增操作失败");
            return increment;
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 自增 {@code 1}
     *
     * @param key 锁的 key
     * @return 值
     */
    public final long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 释放锁
     *
     * @param lock 锁
     */
    public final void releaseLock(@NotNull Lock lock) {
        REDIS_ERROR.whenNull(lock, "释放锁失败，传入的锁为空");
        REDIS_ERROR.whenEmpty(lock.getKey(), "释放锁失败，传入的锁的 key 为空");
        REDIS_ERROR.whenEmpty(lock.getValue(), "释放锁失败，传入的锁的 value 为空");
        try {
            if (executeLong(UNLOCK_SCRIPT, getKey(lock.getKey()), lock.getValue()) < 1) {
                log.warn("释放锁无效，锁已过期或已被其他线程重新获取，key={}", lock.getKey());
                throw new ServiceException(REDIS_ERROR);
            }
        } catch (Exception e) {
            log.error("释放锁失败, key={}, error={}", lock.getKey(), e.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 续期锁
     *
     * @param lock 锁
     * @return {@code true} 续期成功; {@code false} 锁已不属于当前持有者
     * @apiNote 任务执行时间可能超过租约时长时，需要调用此方法避免锁被提前释放
     */
    public final boolean renewLock(@NotNull Lock lock) {
        REDIS_ERROR.whenNull(lock, "锁续期失败，传入的锁为空");
        Long leaseTimeout = lock.getLeaseTimeout();
        return renewLock(lock, Objects.nonNull(leaseTimeout) ? leaseTimeout : getDefaultLeaseTimeout());
    }

    /**
     * 续期锁
     *
     * @param lock         锁
     * @param leaseTimeout 新的租约时长(毫秒)
     * @return {@code true} 续期成功; {@code false} 锁已不属于当前持有者
     */
    public final boolean renewLock(@NotNull Lock lock, long leaseTimeout) {
        REDIS_ERROR.whenNull(lock, "锁续期失败，传入的锁为空");
        REDIS_ERROR.whenEmpty(lock.getKey(), "锁续期失败，传入的锁的 key 为空");
        REDIS_ERROR.whenEmpty(lock.getValue(), "锁续期失败，传入的锁的 value 为空");
        if (leaseTimeout <= 0) {
            REDIS_ERROR.show("锁续期失败，传入的租约时长必须大于 0");
        }
        try {
            return executeLong(RENEW_SCRIPT, getKey(lock.getKey()), lock.getValue(), String.valueOf(leaseTimeout)) > 0;
        } catch (Exception e) {
            log.error("锁续期失败, key={}, error={}", lock.getKey(), e.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 获取锁
     *
     * @param key 锁的 key
     * @return 锁的 key
     */
    public final @NotNull Lock lock(String key) {
        Long waitTimeout = redisConfig.getLockWaitTimeout();
        return lock(key, getDefaultLeaseTimeout(), Objects.nonNull(waitTimeout) ? waitTimeout : 0L);
    }

    /**
     * 获取锁
     *
     * @param entity 实体
     * @return 锁的 key
     */
    public final @NotNull <E extends RootModel<E> & IEntity<E>> Lock lockEntity(@NotNull E entity) {
        REDIS_ERROR.whenNull(entity, "获取锁失败，传入的实体为空");
        REDIS_ERROR.whenNull(entity.getId(), "获取锁失败，传入的实体的ID为空");
        return lock(getEntityCacheKey(entity));
    }

    /**
     * 获取锁
     *
     * @param entity  实体
     * @param timeout 锁超时时间(毫秒)
     * @return 锁的 key
     * @apiNote 租约时长与等待时间均为 timeout
     */
    public final @NotNull <E extends RootModel<E> & IEntity<E>> Lock lockEntity(@NotNull E entity, Integer timeout) {
        REDIS_ERROR.whenNull(entity, "获取锁失败，传入的实体为空");
        REDIS_ERROR.whenNull(entity.getId(), "获取锁失败，传入的实体的ID为空");
        @SuppressWarnings("unchecked")
        Class<E> clazz = (Class<E>) entity.getClass();
        return lock(getCacheKey(clazz, entity.getId()), timeout);
    }

    /**
     * 获取锁
     *
     * @param key     锁的 key
     * @param timeout 锁超时时间(毫秒)
     * @return 锁的 key
     * @apiNote 租约时长与等待时间均为 timeout
     */
    public final @NotNull Lock lock(String key, Integer timeout) {
        long leaseTimeout = Objects.nonNull(timeout) ? timeout.longValue() : getDefaultLeaseTimeout();
        return lock(key, leaseTimeout, leaseTimeout);
    }

    /**
     * 获取锁
     *
     * @param key          锁的 key
     * @param leaseTimeout 锁的租约时长(毫秒)，到期后 Redis 自动释放，防止持有者宕机造成死锁
     * @param waitTimeout  获取锁的最长等待时间(毫秒)，小于等于 {@code 0} 时只尝试一次
     * @return 锁的 key
     * @apiNote 采用「SET NX PX」原子加锁；重试间隔指数退避并叠加随机抖动，
     * 既避免高并发下疯狂重试打爆 Redis，也避免多线程同时唤醒造成的惊群
     */
    public final @NotNull Lock lock(String key, long leaseTimeout, long waitTimeout) {
        REDIS_ERROR.whenEmpty(key, "获取锁失败，传入的锁的 key 为空");
        final String redisKey = getKey(key);
        final long lease = leaseTimeout > 0 ? leaseTimeout : getDefaultLeaseTimeout();
        final String value = UUID.randomUUID().toString();
        // 用单调时钟计算截止时间，避免系统时间被调整后等待时长失真
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(waitTimeout, 0));
        long interval = MIN_RETRY_INTERVAL;
        int count = 0;
        while (true) {
            count++;
            try {
                Boolean success = getRedisTemplate().opsForValue()
                        .setIfAbsent(redisKey, value, lease, TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(success)) {
                    return new Lock().setKey(key).setValue(value).setLeaseTimeout(lease);
                }
            } catch (Exception e) {
                log.error("获取锁失败, key={}, error={}", key, e.getMessage(), e);
                throw new ServiceException(REDIS_ERROR);
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                log.warn("获取锁超时, key={}, 尝试次数={}, 最长等待={}ms", key, count, waitTimeout);
                throw new ServiceException(DATABASE_CONCURRENT_ERROR);
            }
            if (!sleepBeforeRetry(Math.min(interval, TimeUnit.NANOSECONDS.toMillis(remaining)))) {
                throw new ServiceException("获取锁被中断");
            }
            interval = Math.min(interval << 1, MAX_RETRY_INTERVAL);
        }
    }

    /**
     * 获取缓存的 key
     *
     * @param key 缓存的 key
     * @return 缓存的 key
     */
    @Contract(pure = true)
    private @NotNull String getKey(String key) {
        return redisConfig.getPrefix() + key;
    }

    /**
     * 获取默认的锁租约时长(毫秒)
     */
    @Contract(pure = true)
    private long getDefaultLeaseTimeout() {
        Long lockTimeout = redisConfig.getLockTimeout();
        return Objects.nonNull(lockTimeout) && lockTimeout > 0 ? lockTimeout : DEFAULT_LEASE_TIMEOUT;
    }

    /**
     * 从缓存中获取实体
     *
     * @param entityClass 实体类
     * @return 缓存实体
     * @apiNote 默认使用内置的 key 规则
     */
    public final @Nullable <E extends RootModel<E> & IEntity<E>> E getEntity(Class<E> entityClass, Long id) {
        return getEntity(getCacheKey(entityClass, id), entityClass);
    }

    /**
     * 从缓存中获取实体
     *
     * @param key   缓存的 key
     * @param clazz 实体类
     * @return 缓存的实体
     */
    public final @Nullable <E extends IEntity<E>> E getEntity(String key, Class<E> clazz) {
        Object object = get(key);
        if (Objects.isNull(object)) {
            return null;
        }
        String json = object.toString();
        if (Objects.isNull(json)) {
            return null;
        }
        return Json.parse(json, clazz);
    }

    /**
     * 删除指定的实体缓存
     *
     * @param entity 实体
     */
    public final <E extends RootModel<E> & IEntity<E>> void deleteEntity(@NotNull E entity) {
        delete(getEntityCacheKey(entity));
    }

    /**
     * 缓存实体
     *
     * @param entity 实体
     */
    public final <E extends RootModel<E> & IEntity<E>> void saveEntity(E entity) {
        saveEntity(entity, redisConfig.getCacheExpireSecond());
    }

    /**
     * 缓存实体
     *
     * @param entity 实体
     * @param second 缓存时间(秒)
     */
    public final <E extends RootModel<E> & IEntity<E>> void saveEntity(@NotNull E entity, long second) {
        String cacheKey = getEntityCacheKey(entity);
        set(cacheKey, Json.toString(entity), second);
    }

    /**
     * 缓存实体
     *
     * @param key    缓存的 Key
     * @param entity 实体
     */
    public final <E extends IEntity<E>> void saveEntity(String key, E entity) {
        saveEntity(key, entity, redisConfig.getCacheExpireSecond());
    }

    /**
     * 缓存实体
     *
     * @param key    缓存的 Key
     * @param entity 实体
     * @param second 缓存时间(秒)
     */
    public final <E extends IEntity<E>> void saveEntity(String key, E entity, long second) {
        set(key, Json.toString(entity), second);
    }

    /**
     * 指定缓存失效时间
     *
     * @param key    缓存的 Key
     * @param second 缓存时间(秒)
     */
    public final void setExpireSecond(String key, long second) {
        try {
            if (second > 0) {
                getRedisTemplate().expire(getKey(key), second, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 删除所有满足条件的数据
     *
     * @param pattern 通配符模式，如 {@code "*"}、{@code "User_*"}
     * @apiNote 模式会自动补上 {@link RedisConfig#getPrefix()} 前缀，只清理本组件自己的数据。
     * 内部使用 {@code SCAN} 分批删除，不会像 {@code KEYS} 那样阻塞 Redis 主线程。
     */
    public final void clearAll(String pattern) {
        try {
            String redisPattern = getPattern(pattern);
            List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
            try (Cursor<String> cursor = getRedisTemplate().scan(
                    ScanOptions.scanOptions().match(redisPattern).count(SCAN_COUNT).build())) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= DELETE_BATCH_SIZE) {
                        deleteBatch(batch);
                        batch.clear();
                    }
                }
            }
            deleteBatch(batch);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 获取过期时间
     *
     * @param key 缓存的 Key
     * @return 过期时间
     */
    public final long getExpireSecond(String key) {
        try {
            return getRedisTemplate().getExpire(getKey(key), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 缓存的 Key
     * @return {@code true} 存在; {@code false} 不存在
     */
    public final boolean hasKey(String key) {
        try {
            return getRedisTemplate().hasKey(getKey(key));
        } catch (Exception e) {
            // 不能吞掉异常返回 false：Redis 一抖动，「幂等 / 防重放」校验就会全部放行
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 删除缓存
     *
     * @param key 缓存的 Key
     */
    public final void delete(String key) {
        try {
            getRedisTemplate().delete(getKey(key));
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 普通缓存获取
     *
     * @param key 缓存的 Key
     * @return 值
     */
    public final @Nullable Object get(String key) {
        try {
            return getRedisTemplate().opsForValue().get(getKey(key));
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 普通缓存放入
     *
     * @param key   缓存的 Key
     * @param value 值
     */
    public final void set(String key, Object value) {
        set(key, value, redisConfig.getCacheExpireSecond());
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key    缓存的 Key
     * @param value  缓存的值
     * @param second 缓存时间(秒)
     * @apiNote <code>如果time小于等于0 将设置无限期</code>
     */
    public final void set(String key, Object value, long second) {
        try {
            if (second > 0) {
                getRedisTemplate().opsForValue().set(getKey(key), value.toString(), second, TimeUnit.SECONDS);
            } else {
                // 无限期：直接写入，不能再调用自己，否则会无限递归直到栈溢出
                getRedisTemplate().opsForValue().set(getKey(key), value.toString());
            }
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 发布到 {@code channel} 的消息
     *
     * @param channel 频道
     * @param message 消息
     */
    public final void publish(String channel, String message) {
        try {
            getRedisTemplate().convertAndSend(channel, message);
        } catch (Exception e) {
            log.error(REDIS_ERROR.getMessage(), e);
            throw new ServiceException(REDIS_ERROR);
        }
    }

    /**
     * 获取缓存 <b>模型</b> 的 cacheKey
     *
     * @param clazz 模型类
     * @param id    ID
     * @return key
     */
    private @NotNull <T extends RootModel<T>> String getCacheKey(@NotNull Class<T> clazz, Long id) {
        REDIS_ERROR.whenNull(id, "ID 不能为空");
        return getTypeName(clazz) + "_" + id;
    }

    /**
     * 获取缓存 <b>实体</b> 的 cacheKey
     *
     * @param entity 实体
     * @return key
     */
    private <E extends RootModel<E> & IEntity<E>> @NotNull String getEntityCacheKey(@NotNull E entity) {
        //noinspection unchecked
        return getCacheKey(entity.getClass(), entity.getId());
    }

    /**
     * 获取参与缓存 key 计算的类型名
     *
     * @param clazz 类型
     * @return 全限定类名
     * @apiNote 1. 不能用 {@code getSimpleName()}：{@code com.a.user.User} 与 {@code com.b.order.User}
     * 会得到同一个 key，导致缓存串号、锁互相阻塞；
     * 2. 实体是 JPA 代理时 {@code getClass()} 拿到的是 {@code Xxx$HibernateProxy$xxx}，
     * 必须回溯到真实类型，否则每次查出来的 key 都不一样，缓存和锁全部失效
     */
    @Contract(pure = true)
    private @NotNull String getTypeName(@NotNull Class<?> clazz) {
        Class<?> current = clazz;
        while (Objects.nonNull(current) && current.getName().contains(HIBERNATE_PROXY)) {
            current = current.getSuperclass();
        }
        return current.getName();
    }

    /**
     * 拼接出通配符模式
     *
     * @param pattern 通配符模式
     * @return 补上前缀后的模式
     * @apiNote 历史上存在调用方直接传入已带前缀的写法，这里做一次兼容，避免出现「双前缀」
     */
    @Contract(pure = true)
    private @NotNull String getPattern(String pattern) {
        String value = Objects.nonNull(pattern) ? pattern : "*";
        String prefix = redisConfig.getPrefix();
        if (Objects.isNull(prefix) || prefix.isEmpty() || value.startsWith(prefix)) {
            return value;
        }
        return prefix + value;
    }

    /**
     * 批量删除 key
     *
     * @param keys 已经拼好前缀的 key
     * @apiNote 优先使用 {@code UNLINK} 在后台释放内存，旧版本 Redis 不支持时退回 {@code DEL}
     */
    private void deleteBatch(@NotNull List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        RedisTemplate<String, Object> template = getRedisTemplate();
        try {
            template.execute((RedisCallback<Long>) connection -> {
                byte[][] rawKeys = keys.stream()
                        .map(it -> it.getBytes(StandardCharsets.UTF_8))
                        .toArray(byte[][]::new);
                return connection.keyCommands().unlink(rawKeys);
            });
        } catch (Exception e) {
            log.warn("UNLINK 删除失败，退回 DEL：{}", e.getMessage());
            template.delete(new ArrayList<>(keys));
        }
    }

    /**
     * 获取 RedisTemplate
     *
     * @apiNote 本组件独占的实例，key / value 统一使用 UTF-8 字符串序列化，
     * 不会去修改 Spring 容器中共享的 redisTemplate
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        if (Objects.isNull(redisTemplate)) {
            // 兜底：极少数手动 new 的场景下 @PostConstruct 不会执行
            initRedisTemplate();
        }
        return redisTemplate;
    }

    /**
     * 初始化本组件独占的 {@link RedisTemplate}
     */
    @PostConstruct
    public void initRedisTemplate() {
        if (Objects.nonNull(redisTemplate)) {
            return;
        }
        synchronized (this) {
            if (Objects.nonNull(redisTemplate)) {
                return;
            }
            StringRedisSerializer serializer = new StringRedisSerializer(StandardCharsets.UTF_8);
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(redisConnectionFactory);
            template.setKeySerializer(serializer);
            template.setHashKeySerializer(serializer);
            template.setValueSerializer(serializer);
            template.setHashValueSerializer(serializer);
            template.afterPropertiesSet();
            this.redisTemplate = template;
        }
    }

    /**
     * 关闭锁的自动续期调度器
     */
    @PreDestroy
    public void destroy() {
        ScheduledExecutorService executor = lockRenewExecutor;
        if (Objects.nonNull(executor)) {
            executor.shutdownNow();
        }
    }

    /**
     * 释放锁，失败时仅记录日志
     *
     * @apiNote 用于 {@code finally} 场景：释放锁本身失败不能顶掉业务异常
     */
    private void releaseLockQuietly(@NotNull Lock lock) {
        try {
            releaseLock(lock);
        } catch (Exception e) {
            log.error("释放锁失败，锁将在租约到期后自动释放，key={}, error={}", lock.getKey(), e.getMessage(), e);
        }
    }

    /**
     * 开启锁的自动续期(看门狗)
     *
     * @param lock 锁
     * @return 续期任务，未开启时为 {@code null}
     */
    private @Nullable ScheduledFuture<?> startRenew(@NotNull Lock lock) {
        if (Boolean.FALSE.equals(redisConfig.getLockWatchdog())) {
            return null;
        }
        long lease = Objects.isNull(lock.getLeaseTimeout()) ? 0L : lock.getLeaseTimeout();
        if (lease <= 0) {
            return null;
        }
        long configured = Objects.isNull(redisConfig.getLockWatchdogInterval()) ? 0L : redisConfig.getLockWatchdogInterval();
        // 未配置或配置不合理时，按「租约时长的 1/3」续期
        long interval = (configured <= 0 || configured > lease / 2) ? lease / 3 : configured;
        if (interval <= 0) {
            return null;
        }
        AtomicBoolean logged = new AtomicBoolean(false);
        return getLockRenewExecutor().scheduleAtFixedRate(() -> {
            try {
                if (!renewLock(lock) && logged.compareAndSet(false, true)) {
                    log.warn("锁已不属于当前持有者，自动续期失效，key={}", lock.getKey());
                }
            } catch (Exception e) {
                // 续期任务抛出异常会被调度器取消，只能在内部消化
                if (logged.compareAndSet(false, true)) {
                    log.error("锁自动续期失败, key={}, error={}", lock.getKey(), e.getMessage(), e);
                }
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止锁的自动续期
     */
    private void stopRenew(@Nullable ScheduledFuture<?> renewTask) {
        if (Objects.nonNull(renewTask)) {
            renewTask.cancel(false);
        }
    }

    /**
     * 获取锁的自动续期调度器
     */
    private @NotNull ScheduledExecutorService getLockRenewExecutor() {
        if (Objects.isNull(lockRenewExecutor)) {
            synchronized (this) {
                if (Objects.isNull(lockRenewExecutor)) {
                    lockRenewExecutor = Executors.newSingleThreadScheduledExecutor(new LockRenewThreadFactory());
                }
            }
        }
        return lockRenewExecutor;
    }

    /**
     * 执行返回整数的 Lua 脚本
     *
     * @param script   脚本
     * @param redisKey 已经在 Redis 中的 key
     * @param args     脚本参数
     * @return 脚本执行结果，无结果时为 {@code 0}
     */
    private long executeLong(@NotNull RedisScript<Long> script, @NotNull String redisKey, String... args) {
        Number result = getRedisTemplate().execute(script, Collections.singletonList(redisKey), (Object[]) args);
        return result.longValue();
    }

    /**
     * 获取锁失败时的重试等待
     *
     * @param millis 等待毫秒数
     * @return {@code true} 等待完成; {@code false} 等待被中断
     */
    private boolean sleepBeforeRetry(long millis) {
        if (millis <= 0) {
            return !Thread.currentThread().isInterrupted();
        }
        // 叠加随机抖动，避免多线程在同一时刻集中重试
        long sleep = millis / 2 + ThreadLocalRandom.current().nextLong(millis / 2 + 1);
        try {
            TimeUnit.MILLISECONDS.sleep(sleep);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 锁
     */
    @Data
    @Accessors(chain = true)
    public static class Lock {
        /**
         * 锁的 key
         */
        private String key;

        /**
         * 锁的值
         */
        private String value;

        /**
         * 锁的租约时长(毫秒)
         *
         * @apiNote 用于自动续期
         */
        private Long leaseTimeout;
    }

    /**
     * 锁的续期线程工厂
     */
    private static final class LockRenewThreadFactory implements ThreadFactory {
        private static final AtomicLong INDEX = new AtomicLong();

        @Override
        public Thread newThread(@NotNull Runnable runnable) {
            Thread thread = new Thread(runnable, "airpower-lock-renew-" + INDEX.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
