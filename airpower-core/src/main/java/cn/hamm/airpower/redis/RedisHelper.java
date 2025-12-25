package cn.hamm.airpower.redis;

import cn.hamm.airpower.api.Json;
import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.root.RootModel;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static cn.hamm.airpower.exception.ServiceError.REDIS_ERROR;

/**
 * <h1>Redis 封装类</h1>
 *
 * @author Hamm.cn
 */
@Component
@Slf4j
public class RedisHelper {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisConfig redisConfig;

    /**
     * 自增
     *
     * @param key   自增 key
     * @param delta 增量
     * @return 值
     */
    public final long increment(String key, long delta) {
        //noinspection DataFlowIssue
        return redisTemplate.opsForValue().increment(key, delta);
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
        final String key = lock.getKey();
        REDIS_ERROR.whenEmpty(key, "释放锁失败，传入的锁的 key 为空");
        REDIS_ERROR.whenEmpty(lock.getValue(), "释放锁失败，传入的锁的 value 为空");
        if (Objects.equals(redisTemplate.opsForValue().get(lock.getKey()), lock.getValue())) {
            redisTemplate.delete(key);
        }
    }

    /**
     * 获取锁
     *
     * @param key 锁的 key
     * @return 锁的 key
     */
    public final @NotNull Lock lock(String key) {
        return lock(key, redisConfig.getLockTimeout());
    }

    /**
     * 获取锁
     *
     * @param entity 实体
     * @return 锁的 key
     */
    public final @NotNull <E extends CurdEntity<E>> Lock lockEntity(@NotNull E entity) {
        return lockEntity(entity, redisConfig.getLockTimeout());
    }

    /**
     * 获取锁
     *
     * @param entity  实体
     * @param timeout 锁超时时间(毫秒)
     * @return 锁的 key
     */
    public final @NotNull <E extends CurdEntity<E>> Lock lockEntity(@NotNull E entity, Integer timeout) {
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
     */
    public final @NotNull Lock lock(String key, Integer timeout) {
        String value = UUID.randomUUID().toString();
        key = redisConfig.getLockPrefix() + ":" + key;
        int currentIndex = 0;
        int step = 50;
        while (true) {
            currentIndex++;
            Boolean lock = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(lock)) {
                return new Lock().setKey(key).setValue(value);
            }
            if (currentIndex * step >= timeout) {
                log.error("获取锁超时，实在点背。{}", key);
                throw new RuntimeException("系统繁忙，请稍后重试");
            }
            try {
                //noinspection BusyWait
                Thread.sleep(step);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * 从缓存中获取实体
     *
     * @param entityClass 实体类
     * @return 缓存实体
     * @apiNote 默认使用内置的 key 规则
     */
    public final @Nullable <E extends CurdEntity<E>> E getEntity(Class<E> entityClass, Long id) {
        return getEntity(getCacheKey(entityClass, id), entityClass);
    }

    /**
     * 从缓存中获取实体
     *
     * @param key   缓存的 key
     * @param clazz 实体类
     * @return 缓存的实体
     */
    public final @Nullable <E extends CurdEntity<E>> E getEntity(String key, Class<E> clazz) {
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
    public final <E extends CurdEntity<E>> void deleteEntity(@NotNull E entity) {
        del(getEntityCacheKey(entity));
    }

    /**
     * 缓存实体
     *
     * @param entity 实体
     */
    public final <E extends CurdEntity<E>> void saveEntity(E entity) {
        saveEntity(entity, redisConfig.getCacheExpireSecond());
    }

    /**
     * 缓存实体
     *
     * @param entity 实体
     * @param second 缓存时间(秒)
     */
    public final <E extends CurdEntity<E>> void saveEntity(@NotNull E entity, long second) {
        String cacheKey = getEntityCacheKey(entity);
        set(cacheKey, Json.toString(entity), second);
    }

    /**
     * 缓存实体
     *
     * @param key    缓存的 Key
     * @param entity 实体
     */
    public final <E extends CurdEntity<E>> void saveEntity(String key, E entity) {
        saveEntity(key, entity, redisConfig.getCacheExpireSecond());
    }

    /**
     * 缓存实体
     *
     * @param key    缓存的 Key
     * @param entity 实体
     * @param second 缓存时间(秒)
     */
    public final <E extends CurdEntity<E>> void saveEntity(String key, E entity, long second) {
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
                redisTemplate.expire(key, second, TimeUnit.SECONDS);
            }
        } catch (Exception exception) {
            log.error(REDIS_ERROR.getMessage(), exception);
            REDIS_ERROR.show();
        }
    }

    /**
     * 删除所有满足条件的数据
     *
     * @param pattern 正则
     */
    public final void clearAll(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            redisTemplate.delete(keys);
        } catch (Exception exception) {
            log.error(REDIS_ERROR.getMessage(), exception);
            REDIS_ERROR.show();
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
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.error(REDIS_ERROR.getMessage(), exception);
            REDIS_ERROR.show();
        }
        return 0;
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 缓存的 Key
     * @return {@code true} 存在; {@code false} 不存在
     */
    public final boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 缓存的 Key
     */
    public final void del(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception exception) {
            log.error(REDIS_ERROR.getMessage(), exception);
            REDIS_ERROR.show();
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
            return redisTemplate.opsForValue().get(key);
        } catch (Exception exception) {
            log.error(REDIS_ERROR.getMessage(), exception);
            REDIS_ERROR.show();
        }
        return null;
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
                redisTemplate.opsForValue().set(key, value.toString(), second, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
        } catch (Exception exception) {
            log.error(REDIS_ERROR.getMessage(), exception);
            REDIS_ERROR.show();
        }
    }

    /**
     * 发布到 {@code channel} 的消息
     *
     * @param channel 频道
     * @param message 消息
     */
    public final void publish(String channel, String message) {
        redisTemplate.setKeySerializer(new StringRedisSerializer(StandardCharsets.UTF_8));
        redisTemplate.setValueSerializer(new StringRedisSerializer(StandardCharsets.UTF_8));
        redisTemplate.convertAndSend(channel, message);
    }

    /**
     * 获取缓存 <b>模型</b> 的 cacheKey
     *
     * @param clazz 模型类
     * @param id    ID
     * @return key
     */
    public final @NotNull <T extends RootModel<T>> String getCacheKey(@NotNull Class<T> clazz, Long id) {
        REDIS_ERROR.whenNull(id, "ID不能为空");
        return clazz.getSimpleName() + "_" + id;
    }

    /**
     * 获取缓存 <b>实体</b> 的 cacheKey
     *
     * @param entity 实体
     * @return key
     */
    public final <T extends CurdEntity<T>> @NotNull String getEntityCacheKey(@NotNull T entity) {
        //noinspection unchecked
        return getCacheKey(entity.getClass(), entity.getId());
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
    }
}
