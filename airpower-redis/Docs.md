# airpower-redis 使用文档

> AirPower Redis 模块 - 基于 Spring Data Redis 的统一缓存 / 分布式锁 / Pub/Sub 助手，并提供 `RedisCacheManager` 的默认配置。

## 一、模块定位

`airpower-redis` 提供：

| 组件              | 路径                                     | 作用                                                            |
|-------------------|------------------------------------------|-----------------------------------------------------------------|
| `RedisHelper`     | `cn.hamm.airpower.redis.RedisHelper`     | 普通 KV / 实体缓存 / 分布式锁 / Pub/Sub 一站式工具              |
| `RedisConfig`     | `cn.hamm.airpower.redis.RedisConfig`     | `airpower.redis.*` 配置                                         |
| `RedisConfigurer` | `cn.hamm.airpower.redis.RedisConfigurer` | `RedisCacheManager` + `KeyGenerator` + `CacheResolver` 默认配置 |

依赖中使用 Jedis 客户端（已排除 Lettuce）。

## 二、引入依赖

```xml

<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-redis</artifactId>
    <version>${airpower.version}</version>
</dependency>
```

## 三、应用配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASS:}
      database: 0
      timeout: 3s
      jedis:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 1
          max-wait: 3s

airpower:
  redis:
    cache-expire-second: 60            # 默认缓存有效期（秒）
    save-file-path:                     # 可选：导出文件保存目录
    prefix:
      airpower:                   # 全局 key 前缀
    lock-timeout: 60000                 # 分布式锁超时（毫秒）
```

源码：[RedisConfig.java](src/main/java/cn/hamm/airpower/redis/RedisConfig.java)。

## 四、普通缓存

```java

@Autowired
private RedisHelper redisHelper;

// SET / GET
redisHelper.

set("user:1","hamm");

Object value = redisHelper.get("user:1");

// 带过期时间
redisHelper.

set("user:1","hamm",3600);

// 自增
long v = redisHelper.increment("counter", 1L);

// 批量删除
redisHelper.

clearAll("airpower:user:*");

// 判断是否存在
boolean exists = redisHelper.hasKey("user:1");
```

## 五、实体缓存

`RedisHelper` 内置了按类名 + ID 自动拼 key 的实体缓存工具：

```java

@Data
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends CurdEntity<UserEntity> { ...
}

UserEntity user = new UserEntity().setId(1L).setNickname("hamm");

// 写入默认 TTL 缓存
redisHelper.

saveEntity(user);

// 指定 TTL
redisHelper.

saveEntity(user, 3600);

// 读取（不存在返回 null）
UserEntity cached = redisHelper.getEntity(UserEntity.class, 1L);

// 删除
redisHelper.

deleteEntity(user);
```

> Cache key 形如 `airpower:UserEntity_1`（前缀可在 `RedisConfig.prefix` 配置）。

## 六、分布式锁

```java
// 基于 key
RedisHelper.Lock lock = redisHelper.lock("order:create");
// ... 业务逻辑 ...
redisHelper.

releaseLock(lock);

// 基于实体（key 自动 = 类名 + ID）
RedisHelper.Lock lock = redisHelper.lockEntity(userEntity);
// ... 业务逻辑 ...
redisHelper.

releaseLock(lock);

// runWithLock 简写
redisHelper.

runWithLock("global",() ->

doSomething());
```

锁特性：

- 使用 `SETNX` + 唯一 UUID value，避免误删别人的锁。
- 默认 `lock-timeout = 60s`，到期自动失效。
- 等待过程按 50ms 自旋，超时抛 `SERVICE_ERROR: 系统繁忙，请稍后重试`。

## 七、Pub/Sub 发布

```java
redisHelper.publish("airpower:WEBSOCKET_ALL","{\"type\":\"ping\"}");
```

> 推荐仅在跨进程广播场景使用（如 `airpower-websocket` 走 Redis Pub/Sub）。

## 八、`@Cacheable` 集成

模块已配置 `RedisCacheManager` + `KeyGenerator`，可直接启用 Spring Cache：

```java

@Cacheable("user")
public UserEntity getUser(Long id) {
    return repository.findById(id).orElse(null);
}
```

缓存 key 规则：`{类名}.{方法名}{参数 toString 拼接}`，值使用 `GenericJackson2JsonRedisSerializer` 序列化。

## 九、关键类速查

| 类                 | 路径                                      | 说明                          |
|--------------------|-------------------------------------------|-------------------------------|
| `RedisHelper`      | `cn.hamm.airpower.redis.RedisHelper`      | 业务入口                      |
| `RedisConfig`      | `cn.hamm.airpower.redis.RedisConfig`      | 配置                          |
| `RedisConfigurer`  | `cn.hamm.airpower.redis.RedisConfigurer`  | Spring Cache 集成             |
| `RedisHelper.Lock` | `cn.hamm.airpower.redis.RedisHelper.Lock` | 分布式锁句柄                  |
| `Auto`             | `cn.hamm.airpower.redis.Auto`             | `@AutoConfiguration` 装配入口 |

## 十、常见问题

1. **键值中混入 `RedisTemplate` 的 JDK 序列化前缀？** 已统一使用 `StringRedisSerializer`，但如果使用
   `redisTemplate.opsForValue()` 自定义操作，请调用 `getRedisTemplate()` 重新设置序列化器。
2. **想临时关闭全局 key 前缀？** 把 `airpower.redis.prefix` 设为空字符串。
3. **分布式锁超时时间如何调整？** 修改 `airpower.redis.lock-timeout`，单位毫秒。
4. **实体缓存与数据库不一致？** CURD 默认不会自动清除缓存，可在 `CurdService.afterUpdate / afterDelete` 中手动
   `redisHelper.deleteEntity(...)`。