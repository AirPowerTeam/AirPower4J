package cn.hamm.airpower.curd.helper;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * <h1>事务助手类</h1>
 *
 * @author Hamm.cn
 */
@Service
public class TransactionHelper {
    /**
     * 开始执行一个包含若干方法的事务
     *
     * @param function 事务包含的方法集合体
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public void run(@NotNull Function function) {
        function.run();
    }

    /**
     * 开始执行一个包含若干方法的事务
     *
     * @param supplier 事务包含的方法集合体
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public <T> T run(@NotNull Supplier<T> supplier) {
        return supplier.get();
    }

    @FunctionalInterface
    public interface Function {
        /**
         * 开始执行一个包含若干方法的事务
         */
        void run();
    }
}
