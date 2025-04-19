package cn.hamm.airpower.root;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

/**
 * <h1>数据源接口</h1>
 *
 * @param <E> 实体
 * @author Hamm.cn
 */
@NoRepositoryBean
public interface RootRepository<E extends RootEntity<E>> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
    /**
     * 加 {@code 写锁} 查询
     *
     * @param id ID
     * @return 实体
     */
    @Transactional(rollbackFor = Exception.class)
    @Lock(PESSIMISTIC_WRITE)
    E getWidthLockById(Long id);
}
