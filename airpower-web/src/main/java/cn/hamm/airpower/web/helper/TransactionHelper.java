package cn.hamm.airpower.web.helper;

import cn.hamm.airpower.core.util.TaskUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h1>事务助手类</h1>
 *
 * @author Hamm.cn
 */
@Service
public class TransactionHelper {
    /**
     * <h3>开始执行一个包含若干方法的事务</h3>
     *
     * @param function 事务包含的方法集合体
     * @apiNote 如需无视异常执行多项任务，可使用 {@link TaskUtil#run(Runnable, Runnable...)} 或 {@link TaskUtil#runAsync(Runnable, Runnable...)}
     */
    @Transactional(rollbackFor = Exception.class)
    public void run(@NotNull Function function) {
        function.run();
    }

    @FunctionalInterface
    public interface Function {
        /**
         * <h3>开始执行一个包含若干方法的事务</h3>
         */
        void run();
    }

}
