package cn.hamm.airpower.util;

import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.helper.TransactionHelper;
import cn.hamm.airpower.redis.RedisHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <h1>任务流程工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class TaskUtil {
    /**
     * 全局锁的 key
     */
    private static final String GLOBAL_LOCK_KEY = "GLOBAL_LOCK";

    /**
     * 线程池
     */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            5,
            20,
            3600L,
            SECONDS,
            new LinkedBlockingQueue<>()
    );

    /**
     * RedisHelper
     */
    private static RedisHelper redisHelper;

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    @Autowired
    private TaskUtil(RedisHelper redisHelper) {
        TaskUtil.redisHelper = redisHelper;
    }

    /**
     * 执行任务 {@code 不会抛出异常}
     *
     * @param runnable     任务
     * @param moreRunnable 更多任务
     * @apiNote 如需事务处理，可使用 {@link TransactionHelper#run(TransactionHelper.Function)}
     */
    public static void run(Runnable runnable, Runnable... moreRunnable) {
        getRunnableList(runnable, moreRunnable).forEach(run -> {
            try {
                run.run();
            } catch (Exception exception) {
                log.error(exception.getMessage(), exception);
            }
        });
    }

    /**
     * 异步执行任务 {@code 不会抛出异常}
     *
     * @param runnable     任务
     * @param moreRunnable 更多任务
     * @apiNote 如需异步事务处理，可在此参数传入的方法中自行调用 {@link TransactionHelper#run(TransactionHelper.Function)}
     */
    public static void runAsync(Runnable runnable, Runnable... moreRunnable) {
        getRunnableList(runnable, moreRunnable).forEach((run) -> EXECUTOR.submit(() -> {
            try {
                run.run();
            } catch (Exception exception) {
                log.error("异步执行任务失败, {}", exception.getMessage(), exception);
            }
        }));
    }

    /**
     * 获取任务列表
     *
     * @param runnable     任务
     * @param moreRunnable 更多任务
     * @return 任务列表
     */
    private static @NotNull List<Runnable> getRunnableList(Runnable runnable, Runnable[] moreRunnable) {
        List<Runnable> runnableList = new ArrayList<>();
        runnableList.add(runnable);
        runnableList.addAll(Arrays.asList(moreRunnable));
        return runnableList;
    }

    /**
     * 加锁运行任务
     *
     * @param task 任务
     * @apiNote 可根据下列方法自行实现获取和释放锁
     * @see RedisHelper#lock(String)
     * @see RedisHelper#lockEntity(CurdEntity)
     * @see RedisHelper#releaseLock(RedisHelper.Lock)
     */
    public static void runWithLock(Runnable task) {
        runWithLock(GLOBAL_LOCK_KEY, task);
    }

    /**
     * 加锁运行任务
     *
     * @param key  锁的 key
     * @param task 任务
     * @apiNote 可根据下列方法自行实现获取和释放锁
     * @see RedisHelper#lock(String)
     * @see RedisHelper#lockEntity(CurdEntity)
     * @see RedisHelper#releaseLock(RedisHelper.Lock)
     */
    public static void runWithLock(String key, Runnable task) {
        RedisHelper.Lock lock = redisHelper.lock(key);
        try {
            task.run();
        } catch (Exception e) {
            log.error("加锁执行任务失败, {}", e.getMessage(), e);
            throw e;
        } finally {
            redisHelper.releaseLock(lock);
        }
    }
}
