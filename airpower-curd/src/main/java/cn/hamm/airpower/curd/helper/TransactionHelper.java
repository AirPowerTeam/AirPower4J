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
 * @apiNote 所有增删改查都通过本类开事务，隔离级别统一为 {@code REPEATABLE_READ}，
 * 保证同一事务内多次读取看到同一份数据；加锁读取必须排在事务的第一个数据库操作，
 * 否则读视图会提前固定
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
     * @param <T>      返回类型
     * @return 事务的返回值
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
