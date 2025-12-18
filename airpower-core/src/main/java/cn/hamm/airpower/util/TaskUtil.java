package cn.hamm.airpower.util;

import cn.hamm.airpower.helper.TransactionHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

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
public class TaskUtil {
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
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private TaskUtil() {
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
}
