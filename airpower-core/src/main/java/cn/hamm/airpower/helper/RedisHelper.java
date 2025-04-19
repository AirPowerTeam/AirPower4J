package cn.hamm.airpower.helper;

import cn.hamm.airpower.config.ServiceConfig;
import cn.hamm.airpower.model.Json;
import cn.hamm.airpower.root.RootEntity;
import cn.hamm.airpower.root.RootModel;
import jakarta.annotation.Resource;
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
import java.util.concurrent.TimeUnit;

import static cn.hamm.airpower.exception.ServiceError.REDIS_ERROR;

/**
 * <h1>{@code Redis} 封装类</h1>
 *
 * @author Hamm.cn
 */
@Component
@Slf4j
public class RedisHelper {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ServiceConfig serviceConfig;

    /**
     * <h3>从缓存中获取实体</h3>
     *
     * @param entityClass 实体类
     * @return 缓存实体
     * @apiNote 默认使用内置的 {@code key} 规则
     */
    public final @Nullable <E extends RootEntity<E>> E getEntity(Class<E> entityClass, Long id) {
        return getEntity(getCacheKey(entityClass, id), entityClass);
    }

    /**
     * <h3>从缓存中获取实体</h3>
     *
     * @param key   缓存的 {@code Key}
     * @param clazz 实体类
     * @return 缓存的实体
     */
    public final @Nullable <E extends RootEntity<E>> E getEntity(String key, Class<E> clazz) {
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
     * <h3>删除指定的实体缓存</h3>
     *
     * @param entity 实体
     */
    public final <E extends RootEntity<E>> void deleteEntity(@NotNull E entity) {
        del(getEntityCacheKey(entity));
    }

    /**
     * <h3>缓存实体</h3>
     *
     * @param entity 实体
     */
    public final <E extends RootEntity<E>> void saveEntity(E entity) {
        saveEntity(entity, serviceConfig.getCacheExpireSecond());
    }

    /**
     * <h3>缓存实体</h3>
     *
     * @param entity 实体
     * @param second 缓存时间(秒)
     */
    public final <E extends RootEntity<E>> void saveEntity(@NotNull E entity, long second) {
        String cacheKey = getEntityCacheKey(entity);
        set(cacheKey, Json.toString(entity), second);
    }

    /**
     * <h3>缓存实体</h3>
     *
     * @param key    缓存的 {@code Key}
     * @param entity 实体
     */
    public final <E extends RootEntity<E>> void saveEntity(String key, E entity) {
        saveEntity(key, entity, serviceConfig.getCacheExpireSecond());
    }

    /**
     * <h3>缓存实体</h3>
     *
     * @param key    缓存的 {@code Key}
     * @param entity 实体
     * @param second 缓存时间(秒)
     */
    public final <E extends RootEntity<E>> void saveEntity(String key, E entity, long second) {
        set(key, Json.toString(entity), second);
    }

    /**
     * <h3>指定缓存失效时间</h3>
     *
     * @param key    缓存的 {@code Key}
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
     * <h3>删除所有满足条件的数据</h3>
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
     * <h3>获取过期时间</h3>
     *
     * @param key 缓存的 {@code Key}
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
     * <h3>判断key是否存在</h3>
     *
     * @param key 缓存的 {@code Key}
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
     * <h3>删除缓存</h3>
     *
     * @param key 缓存的 {@code Key}
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
     * <h3>普通缓存获取</h3>
     *
     * @param key 缓存的 {@code Key}
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
     * <h3>普通缓存放入</h3>
     *
     * @param key   缓存的 {@code Key}
     * @param value 值
     */
    public final void set(String key, Object value) {
        set(key, value, serviceConfig.getCacheExpireSecond());
    }

    /**
     * <h3>普通缓存放入并设置时间</h3>
     *
     * @param key    缓存的 {@code Key}
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
     * <h3>发布到 {@code channel} 的消息</h3>
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
     * <h3>获取缓存 {@code 模型} 的 {@code cacheKey}</h3>
     *
     * @param clazz 模型类
     * @param id    ID
     * @return key
     */
    private @NotNull <T extends RootModel<T>> String getCacheKey(@NotNull Class<T> clazz, Long id) {
        REDIS_ERROR.whenNull(id, "ID不能为空");
        return clazz.getSimpleName() + "_" + id;
    }

    /**
     * <h3>获取缓存 {@code 实体} 的 {@code cacheKey}</h3>
     *
     * @param entity 实体
     * @return key
     */
    private <T extends RootEntity<T>> @NotNull String getEntityCacheKey(@NotNull T entity) {
        //noinspection unchecked
        return getCacheKey(entity.getClass(), entity.getId());
    }
}
