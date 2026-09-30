package cn.hamm.airpower.curd.base;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h1>通用数据源</h1>
 *
 * @param <E> 实体
 * @author Hamm.cn
 */
@NoRepositoryBean
public interface ICurdRepository<E extends CurdEntity<E>> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
    /**
     * 加 {@code 写锁} 查询
     *
     * @param id ID
     * @return 实体
     * @apiNote 方法名中的 {@code ForUpdate} 会被 Spring Data 解析成属性路径的一部分，
     * 正因为显式声明了本方法才绕过解析；一旦改名为 {@code findForUpdateById} 之类的
     * 派生查询且没有 {@code @Query}，启动时会抛
     * {@code PropertyReferenceException: No property 'forUpdate'}
     * @apiNote {@code PESSIMISTIC_WRITE} 依赖 {@code SELECT ... FOR UPDATE}，必须包在
     * 事务里。声明 {@code MANDATORY} 是为了把「事务外调用」变成启动即失败的显式约束，
     * 而不是运行期才冒出 {@code InvalidDataAccessApiUsage}
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    E getForUpdateById(Long id);
}
