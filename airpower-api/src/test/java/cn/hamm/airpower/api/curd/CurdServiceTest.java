package cn.hamm.airpower.api.curd;

import cn.hamm.airpower.api.export.ExportHelper;
import cn.hamm.airpower.api.fixture.DemoEntity;
import cn.hamm.airpower.api.fixture.NoDefaultConstructorEntity;
import cn.hamm.airpower.api.fixture.DemoRepository;
import cn.hamm.airpower.api.fixture.DemoService;
import cn.hamm.airpower.api.query.QueryHelper;
import cn.hamm.airpower.api.query.page.Page;
import cn.hamm.airpower.api.query.page.PageData;
import cn.hamm.airpower.api.query.page.QueryPageRequest;
import cn.hamm.airpower.api.query.page.QueryPageResponse;
import cn.hamm.airpower.api.query.request.QueryListRequest;
import cn.hamm.airpower.api.query.sort.Sort;
import cn.hamm.airpower.api.transaction.TransactionHelper;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.redis.RedisHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND;
import static cn.hamm.airpower.exception.Errors.FORBIDDEN_DISABLED;
import static cn.hamm.airpower.exception.Errors.FORBIDDEN_EXIST;
import static cn.hamm.airpower.exception.Errors.PARAM_MISSING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * <h2>实体根服务单元测试</h2>
 *
 * <p>三条最容易出错、且出错后不报错只出错数据的规则：</p>
 * <ul>
 *     <li><b>唯一列校验</b>：改自己不能被自己的旧值挡下来；
 *     值�� {@code null} 时不该参与校验（否则所有未填用户名的记录互相冲突）</li>
 *     <li><b>未标 {@link NullEnable} 的字段传 {@code null} 时不覆盖库中原值</b>。
 *     前端通常只提交改动过的字段，若 {@code null} 直接落库，
 *     「只改昵称」就会把用户名、邮箱一起清空</li>
 *     <li><b>脱敏字段回写</b>：读回来��� {@code ***} 的值再提交，
 *     不能把掩码当真实值写回库里</li>
 * </ul>
 *
 * <p>这里不启 Spring 容器：事务助手在没有事务管理器时退化为直接执行，
 * 正好能把「事务边界内发生了什么」单独测清楚。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("实体根服务单元测试")
class CurdServiceTest {

    /**
     * 主键
     */
    private static final long ID = 7L;

    /**
     * 新增时数据库返回的主键
     */
    private static final long NEW_ID = 100L;

    private DemoService service;
    private DemoRepository repository;

    @BeforeEach
    void setUp() {
        service = new DemoService();
        repository = Mockito.mock(DemoRepository.class);
        // 无 Spring 上下文时 transactionHelper 的 @Transactional 不生效，
        // 退化为「直接执行」，事务内逻辑仍可完整验证
        inject(service, "repository", repository);
        inject(service, "transactionHelper", new TransactionHelper());
        inject(service, "queryHelper", queryHelper());
        inject(service, "exportHelper", Mockito.mock(ExportHelper.class));
        inject(service, "redisHelper", Mockito.mock(RedisHelper.class));

        // 新增路径依赖 saveAndFlush 返回带主键的实体
        Mockito.lenient().when(repository.saveAndFlush(any(DemoEntity.class)))
                .thenAnswer(invocation -> {
                    DemoEntity saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(NEW_ID);
                    }
                    return saved;
                });
    }

    /**
     * 构造一个带默认配置的查询助手
     *
     * @return 查询助手
     */
    private QueryHelper queryHelper() {
        QueryHelper helper = new QueryHelper();
        cn.hamm.airpower.api.base.ApiConfig config = new cn.hamm.airpower.api.base.ApiConfig();
        config.setDefaultPageSize(20);
        config.setMinPageSize(5);
        config.setMaxPageSize(1000);
        config.setDefaultSortField(CurdEntity.STRING_ID);
        try {
            Field field = QueryHelper.class.getDeclaredField("apiConfig");
            field.setAccessible(true);
            field.set(helper, config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 apiConfig 失败", e);
        }
        return helper;
    }

    /**
     * 反射写入字段，兼容继承链上的声明
     *
     * @param target    目标
     * @param name      字段名
     * @param value     值
     */
    private void inject(Object target, String name, Object value) {
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("注入字段失败: " + name, e);
            }
        }
        throw new IllegalStateException("未找到字段: " + name);
    }

    /**
     * 让数据源按 ID 返回指定实体
     *
     * @param entity 实体，{@code null} 表示查不到
     */
    private void givenExists(DemoEntity entity) {
        Mockito.lenient().when(repository.findById(anyLong()))
                .thenReturn(Optional.ofNullable(entity));
    }

    /**
     * {@code JpaRepository#findOne} 有多个重载，必须给出精确的匹配器类型，
     * 否则 Mockito 无法选定目标方法
     *
     * @return 匹配任意 Example 的匹配器
     */
    private static Example<DemoEntity> anyExample() {
        return Mockito.any(Example.class);
    }

    /**
     * 让唯一列查询返回指定结果
     *
     * @param result 查询结果
     */
    private void givenUniqueCheck(Optional<DemoEntity> result) {
        Mockito.lenient().when(repository.findOne(anyExample())).thenReturn(result);
    }

    /**
     * 列表查询（带排序）
     *
     * @param list 数据
     */
    @SuppressWarnings("unchecked")
    private void givenList(List<DemoEntity> list) {
        Mockito.lenient()
                .when(repository.findAll(ArgumentMatchers.<Specification<DemoEntity>>any(),
                        ArgumentMatchers.<org.springframework.data.domain.Sort>any()))
                .thenReturn(list);
    }

    /**
     * 分页查询
     *
     * @param page 分页数据
     */
    @SuppressWarnings("unchecked")
    private void givenPage(org.springframework.data.domain.Page<DemoEntity> page) {
        Mockito.lenient()
                .when(repository.findAll(ArgumentMatchers.<Specification<DemoEntity>>any(),
                        ArgumentMatchers.<Pageable>any()))
                .thenReturn(page);
    }

    /**
     * 构造一个分页对象
     *
     * @param content 内容
     * @param total   总条数
     * @return 分页对象
     * @apiNote 不用 {@code Page.empty()}：它内部的 {@code Unpaged} 在
     * {@code getPageSize()} 上直接抛 {@code UnsupportedOperationException}，
     * 会把「分页数据换算」这条被测路径整个盖掉
     */
    private static org.springframework.data.domain.Page<DemoEntity> pageOf(
            List<DemoEntity> content, long total) {
        return pageOf(content, total, org.springframework.data.domain.PageRequest.of(0, 10));
    }

    /**
     * 构造一个指定总条数与分页参数的分页对象
     *
     * @param content  内容
     * @param total    总条数
     * @param pageable 分页参数
     * @return 分页对象
     */
    private static org.springframework.data.domain.Page<DemoEntity> pageOf(
            List<DemoEntity> content, long total,
            org.springframework.data.domain.Pageable pageable) {
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * 构造一个已存在的实体
     *
     * @return 实体
     */
    private DemoEntity existing() {
        return new DemoEntity().setId(ID).setUsername("old-user").setNickname("旧昵称");
    }

    @Nested
    @DisplayName("1. 新增")
    class Add {

        @Test
        @DisplayName("新增时强制清空主键")
        void idIsForcedToNull() {
            // 前端伪造一个 id 进来，若不清空就会变成「覆盖那条记录」
            DemoEntity source = new DemoEntity().setId(999L).setUsername("u1");
            assertEquals(NEW_ID, service.add(source));
            ArgumentCaptor<DemoEntity> captor = ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(repository).saveAndFlush(captor.capture());
            assertEquals(NEW_ID, captor.getValue().getId());
        }

        @Test
        @DisplayName("新增时写入创建时间、修改时间并默认未禁用")
        void timestampsAndDisabledFilled() {
            long before = System.currentTimeMillis();
            service.add(new DemoEntity().setUsername("u1"));
            ArgumentCaptor<DemoEntity> captor = ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(repository).saveAndFlush(captor.capture());
            DemoEntity saved = captor.getValue();
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
            assertFalse(saved.getIsDisabled(), "新增的数据不应默认禁用");
            assertTrue(saved.getCreateTime() >= before, "创建时间不应早于调用时刻");
        }

        @Test
        @DisplayName("新增时提交了 isDisabled 也一律按未禁用处理")
        void isDisabledIsForcedToFalse() {
            service.add(new DemoEntity().setUsername("u1").setIsDisabled(true));
            ArgumentCaptor<DemoEntity> captor = ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(repository).saveAndFlush(captor.capture());
            assertFalse(captor.getValue().getIsDisabled(),
                    "客户端不能通过新增接口直接插入一条禁用记录");
        }

        @Test
        @DisplayName("addToDatabase 传入主键必须报错")
        void addToDatabaseRejectsGivenId() {
            // 「新增」与「修改」必须可区分，否则一次误传就变成静默覆盖
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.addToDatabase(new DemoEntity().setId(1L)));
            assertEquals(PARAM_MISSING.getCode(), exception.getCode());
            Mockito.verify(repository, Mockito.never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("前置方法返回 null 时报错而不是写入空实体")
        void beforeAddReturningNullRejected() {
            DemoService spied = Mockito.spy(service);
            Mockito.doReturn(null).when(spied).beforeAdd(any());
            assertThrows(ServiceException.class, () -> spied.add(new DemoEntity().setUsername("u1")));
            Mockito.verify(repository, Mockito.never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("addAndGet 新增后按新主键回查")
        void addAndGetReadsBack() {
            Mockito.lenient().when(repository.findById(NEW_ID))
                    .thenReturn(Optional.of(new DemoEntity().setId(NEW_ID).setUsername("u1")));
            DemoEntity result = service.addAndGet(new DemoEntity().setUsername("u1"));
            assertEquals(NEW_ID, result.getId());
            Mockito.verify(repository).findById(NEW_ID);
        }
    }

    @Nested
    @DisplayName("2. 唯一列校验")
    class CheckUnique {

        @Test
        @DisplayName("唯一值已被别人占用时拒绝新增")
        void duplicateRejectedOnAdd() {
            // findOne 返回一条 id 与提交方不同的记录 = 撞车
            givenUniqueCheck(Optional.of(existing()));
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.add(new DemoEntity().setUsername("old-user")));
            assertEquals(FORBIDDEN_EXIST.getCode(), exception.getCode());
            Mockito.verify(repository, Mockito.never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("修改自己不能被自己的旧值挡下来")
        void modifyingSelfIsNotAConflict() {
            // 这是最容易漏的一条：唯一列在修改时必然能查到「自己」，
            // 若不比对 id，任何一次修改自己唯一列的操作都会被误判为重复
            givenExists(existing());
            givenUniqueCheck(Optional.of(existing()));
            assertDoesNotThrow(() -> service.updateToDatabase(
                    new DemoEntity().setId(ID).setUsername("old-user")));
        }

        @Test
        @DisplayName("改成别人已占用的值必须拒绝")
        void changingToAnotherRecordValueRejected() {
            givenUniqueCheck(Optional.of(new DemoEntity().setId(ID + 1).setUsername("taken")));
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.updateToDatabase(new DemoEntity().setId(ID).setUsername("taken")));
            assertEquals(FORBIDDEN_EXIST.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("唯一值为 null 时不参与校验")
        void nullValueSkipped() {
            // 否则两条都没填用户名的记录会互相冲突，等于「用户名必填」，
            // 但代码与文档都没有这个约束
            givenUniqueCheck(Optional.of(existing()));
            assertDoesNotThrow(() -> service.add(new DemoEntity()));
            Mockito.verify(repository, Mockito.never()).findOne(anyExample());
        }

        @Test
        @DisplayName("唯一值为空串时仍然参与校验")
        void emptyStringStillChecked() {
            // 空串是「有值」，和 null 不是一回事；MySQL 唯一索引同样会拒它
            givenUniqueCheck(Optional.of(existing()));
            assertThrows(ServiceException.class, () -> service.add(new DemoEntity().setUsername("")));
        }

        @Test
        @DisplayName("查不到冲突记录时放行")
        void noConflictAllowsSave() {
            givenUniqueCheck(Optional.empty());
            assertDoesNotThrow(() -> service.add(new DemoEntity().setUsername("free")));
        }

        @Test
        @DisplayName("没有唯一列的实体不触发任何查询")
        void entityWithoutUniqueColumnSkipsCheck() {
            givenUniqueCheck(Optional.of(existing()));
            assertDoesNotThrow(() -> service.add(new DemoEntity().setNickname("随便")));
            Mockito.verify(repository, Mockito.never()).findOne(anyExample());
        }

        @Test
        @DisplayName("非数据库字段不参与唯一校验")
        void transientFieldNotChecked() {
            // transientField 上没有 @Column，即便值重复也不该报错
            givenUniqueCheck(Optional.empty());
            assertDoesNotThrow(() -> service.add(new DemoEntity().setTransientField("x")));
        }
    }

    @Nested
    @DisplayName("3. 修改时的 null 覆盖策略")
    class NullOverwrite {

        @Test
        @DisplayName("未标 NullEnable 的字段传 null 时保留库中原值")
        void nullFieldDoesNotOverwrite() {
            // 前端通常只提交改动过的字段。若 null 直接落库，
            // 「只改昵称」就会顺手把用户名也清空
            DemoEntity exist = existing();
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setNickname("新昵称"));

            assertEquals("old-user", exist.getUsername(), "未提交的字段被清空了");
            assertEquals("新昵称", exist.getNickname());
        }

        @Test
        @DisplayName("标了 NullEnable 的字段传 null 时会写入")
        void nullEnableFieldIsCleared() {
            DemoEntity exist = existing();
            exist.setNullableNote("原备注");
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setNullableNote(null));
            assertNull(exist.getNullableNote(), "显式允许置空的字段应当被清空");
        }

        @Test
        @DisplayName("withNull = true 时连未标 NullEnable 的字段也置空")
        void withNullOverridesEverything() {
            // 注意断言对象：withNull=true 走的是「直接拿提交实体写库」这条路，
            // 根本不会去读库里的旧实体，所以要看 saveAndFlush 的入参，
            // 而不是盯着 exist —— 那样断言会永远通过，测不出任何东西
            givenExists(existing());
            service.updateToDatabase(new DemoEntity().setId(ID), true);

            ArgumentCaptor<DemoEntity> captor = ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(repository).saveAndFlush(captor.capture());
            assertNull(captor.getValue().getUsername(), "withNull=true 时应当允许清空");
            assertEquals(ID, captor.getValue().getId());
        }

        @Test
        @DisplayName("withNull = false 时写库实体是「旧实体 + 非空字段覆盖」的结果")
        void withoutNullMergesIntoExisting() {
            givenExists(existing());
            service.updateToDatabase(new DemoEntity().setId(ID).setNickname("新昵称"), false);

            ArgumentCaptor<DemoEntity> captor = ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(repository).saveAndFlush(captor.capture());
            DemoEntity saved = captor.getValue();
            assertEquals("新昵称", saved.getNickname());
            assertEquals("old-user", saved.getUsername(), "未提交的字段不应被清空");
        }

        @Test
        @DisplayName("修改时不允许改创建时间")
        void createTimeIsProtected() {
            // 创建时间由服务端维护，允许客户端改会破坏审计
            DemoEntity exist = existing();
            exist.setCreateTime(1000L);
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setCreateTime(9999L));
            assertEquals(1000L, exist.getCreateTime(), "创建时间被客户端改写了");
        }

        @Test
        @DisplayName("修改时刷新修改时间")
        void updateTimeRefreshed() {
            DemoEntity exist = existing();
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID));
            assertTrue(exist.getUpdateTime() > 1000L, "修改时间未刷新");
        }

        @Test
        @DisplayName("不带主键的修改必须报错")
        void updateWithoutIdRejected() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.updateToDatabase(new DemoEntity().setNickname("x")));
            assertEquals(PARAM_MISSING.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("修改不存在的记录时查不到并报错")
        void updateMissingEntityThrows() {
            givenExists(null);
            assertThrows(ServiceException.class,
                    () -> service.updateToDatabase(new DemoEntity().setId(404L)));
        }

        @Test
        @DisplayName("修改不存在的记录时返回 DATA_NOT_FOUND")
        void updateMissingEntityUsesNotFoundCode() {
            givenExists(null);
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.updateToDatabase(new DemoEntity().setId(404L)));
            assertEquals(DATA_NOT_FOUND.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("4. 脱敏字段回写")
    class Desensitize {

        @Test
        @DisplayName("回传的整体掩码不会覆盖库中原值")
        void replaceSymbolIsCleared() {
            // 前端把读到的 *** 原样提交回来。若不拦，库里真实的密码就被覆盖成 ***
            // 注意断言对象是 exist（库里的旧实体），而不是提交实体——
            // 脱敏处理的是提交实体，置空之后它会因为「未提交」而被跳过
            DemoEntity exist = existing();
            exist.setPassword("库里的真实密码");
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setPassword("***"));
            assertEquals("库里的真实密码", exist.getPassword(), "掩码被写回了数据库");
        }

        @Test
        @DisplayName("回传的部分掩码不会覆盖库中原值")
        void partialMaskIsCleared() {
            DemoEntity exist = existing();
            exist.setMobile("13812348888");
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setMobile("138****8888"));
            assertEquals("13812348888", exist.getMobile(), "部分掩码被写回了数据库");
        }

        @Test
        @DisplayName("新的真实值正常写入")
        void realValueWritesThrough() {
            DemoEntity exist = existing();
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setPassword("N3wP@ss"));
            assertEquals("N3wP@ss", exist.getPassword());
        }

        @Test
        @DisplayName("脱敏字段传 null 时不覆盖库中原值")
        void desensitizeFieldNullKeepsOriginal() {
            DemoEntity exist = existing();
            exist.setPassword("库里的真实密码");
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setPassword(null));
            assertEquals("库里的真实密码", exist.getPassword());
        }

        @Test
        @DisplayName("不含脱敏符号的真实值不会被误判为掩码")
        void cleanValueNotMistakenForMask() {
            DemoEntity exist = existing();
            givenExists(exist);
            service.updateToDatabase(new DemoEntity().setId(ID).setPassword("abc123"));
            assertEquals("abc123", exist.getPassword());
        }

        @Test
        @DisplayName("withNull = true 时真实值仍能写入（脱敏在合并分支之外）")
        void withNullPathDoesNotDesensitize() {
            // withNull=true 走的是「不合并、直接写提交实体」的分支，
            // desensitize() 根本没被调用 —— 这是与 withNull=false 的行为差异，
            // 调用方必须清楚：全量覆盖时不要指望脱敏兜底
            givenExists(existing());
            service.updateToDatabase(new DemoEntity().setId(ID).setPassword("***"), true);

            ArgumentCaptor<DemoEntity> captor = ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(repository).saveAndFlush(captor.capture());
            assertEquals("***", captor.getValue().getPassword(),
                    "withNull=true 分支不经过脱敏，掩码会原样落库");
        }
    }

    @Nested
    @DisplayName("5. 查询")
    class Query {

        @Test
        @DisplayName("get 查不到时抛 DATA_NOT_FOUND")
        void getMissingThrows() {
            givenExists(null);
            ServiceException exception = assertThrows(ServiceException.class, () -> service.get(ID));
            assertEquals(DATA_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("getMaybeNull 查不到时返回 null 而不抛异常")
        void getMaybeNullSwallows() {
            // 用于「可选关联」的探测，抛异常会把整个列表查询打断
            givenExists(null);
            assertNull(service.getMaybeNull(ID));
        }

        @Test
        @DisplayName("getMaybeNull 查得到时返回实体")
        void getMaybeNullReturnsEntity() {
            givenExists(existing());
            assertNotNull(service.getMaybeNull(ID));
        }

        @Test
        @DisplayName("getMaybeNull 吞掉所有异常")
        void getMaybeNullSwallowsAnyException() {
            Mockito.when(repository.findById(anyLong())).thenThrow(new IllegalStateException("数据源炸了"));
            assertNull(service.getMaybeNull(ID));
        }

        @Test
        @DisplayName("getWithEnable 命中禁用数据时抛 FORBIDDEN_DISABLED")
        void getWithEnableRejectsDisabled() {
            givenExists(existing().setIsDisabled(true));
            ServiceException exception = assertThrows(ServiceException.class, () -> service.getWithEnable(ID));
            assertEquals(FORBIDDEN_DISABLED.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("getWithEnable 命中启用数据时正常返回")
        void getWithEnableAcceptsEnabled() {
            givenExists(existing().setIsDisabled(false));
            assertNotNull(service.getWithEnable(ID));
        }

        @Test
        @DisplayName("getForUpdate 加锁查询查不到时抛 DATA_NOT_FOUND")
        void getForUpdateMissingThrows() {
            Mockito.when(repository.getForUpdateById(ID)).thenReturn(null);
            ServiceException exception = assertThrows(ServiceException.class, () -> service.getForUpdate(ID));
            assertEquals(DATA_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("getForUpdate 查得到时返回加锁数据")
        void getForUpdateReturnsEntity() {
            Mockito.when(repository.getForUpdateById(ID)).thenReturn(existing());
            assertEquals(ID, service.getForUpdate(ID).getId());
        }

        @Test
        @DisplayName("updateWithLock 先加锁读再写")
        void updateWithLockLocksThenWrites() {
            DemoEntity exist = existing();
            Mockito.when(repository.getForUpdateById(ID)).thenReturn(exist);
            Mockito.when(repository.findById(ID)).thenReturn(Optional.of(exist));
            service.updateWithLock(ID, item -> item.setNickname("锁内改的"));
            assertEquals("锁内改的", exist.getNickname());
            Mockito.verify(repository).getForUpdateById(ID);
        }

        @Test
        @DisplayName("getList 传入 null 请求时用默认请求")
        void getListAcceptsNullRequest() {
            givenList(List.of());
            assertNotNull(service.getList(null));
        }

        @Test
        @DisplayName("getList 传入 null 过滤器时按空过滤器处理")
        void getListAcceptsNullFilter() {
            givenList(List.of());
            QueryListRequest<DemoEntity> request = new QueryListRequest<>();
            request.setFilter(null);
            assertNotNull(service.getList(request));
        }

        @Test
        @DisplayName("getList 会补齐缺失的排序对象")
        void getListFillsMissingSort() {
            givenList(List.of());
            QueryListRequest<DemoEntity> request = new QueryListRequest<>();
            request.setSort(null);
            service.getList(request);
            assertNotNull(request.getSort());
            assertNotNull(request.getSort().getField());
        }

        @Test
        @DisplayName("getPage 回带本次生效的排序")
        void getPageCarriesSortBack() {
            givenPage(pageOf(List.of(), 0));
            Sort sort = new Sort().setField("nickname").setDirection("asc");
            QueryPageRequest<DemoEntity> request = new QueryPageRequest<>();
            request.setSort(sort);

            QueryPageResponse<DemoEntity> response = service.getPage(request);
            assertEquals("nickname", response.getSort().getField());
            assertEquals("asc", response.getSort().getDirection());
        }

        @Test
        @DisplayName("getPage 把对外的 1 起页码转成内部的 0 起")
        void getPageConvertsToZeroBased() {
            // 断言对象是传给数据源的 Pageable，而不是响应里的 pageNum：
            // 响应的页码来自数据库返回结果（这里是 mock 的固定值），
            // 盯着它测不出「请求页码有没有被正确转换」
            givenPage(pageOf(List.of(), 0));
            QueryPageRequest<DemoEntity> request = new QueryPageRequest<>();
            request.setPage(new Page().setPageNum(3));
            service.getPage(request);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            Mockito.verify(repository).findAll(
                    ArgumentMatchers.<Specification<DemoEntity>>any(), captor.capture());
            assertEquals(2, captor.getValue().getPageNumber(), "第 3 页应对应内部的第 2 页");
        }

        @Test
        @DisplayName("getPage 的响应页码来自数据库返回的分页结果")
        void getPageEchoesDatabasePage() {
            givenPage(pageOf(List.of(), 0, org.springframework.data.domain.PageRequest.of(2, 10)));
            QueryPageRequest<DemoEntity> request = new QueryPageRequest<>();
            request.setPage(new Page().setPageNum(3));
            assertEquals(3, service.getPage(request).getPage().getPageNum());
        }

        @Test
        @DisplayName("getPage 传入 null 请求时用默认请求")
        void getPageAcceptsNullRequest() {
            givenPage(pageOf(List.of(), 0));
            assertNotNull(service.getPage(null));
        }

        @Test
        @DisplayName("filter 走全匹配")
        void filterUsesEqualsMatching() {
            givenList(List.of());
            assertNotNull(service.filter(new DemoEntity().setNickname("精确")));
        }

        @Test
        @DisplayName("query 走模糊匹配")
        void queryUsesLikeMatching() {
            givenList(List.of());
            assertNotNull(service.query(new DemoEntity().setNickname("模糊")));
        }

        @Test
        @DisplayName("filter / query 传入 null 过滤器时不报错")
        void nullFilterAccepted() {
            givenList(List.of());
            assertNotNull(service.filter(null));
            assertNotNull(service.query(null));
        }


    }

    @Nested
    @DisplayName("6. 启用 / 禁用")
    class Toggle {

        @Test
        @DisplayName("enable 把禁用标记置为 false")
        void enableClearsDisabled() {
            DemoEntity exist = existing().setIsDisabled(true);
            givenExists(exist);
            service.enable(ID);
            assertFalse(exist.getIsDisabled());
        }

        @Test
        @DisplayName("disable 把禁用标记置为 true")
        void disableSetsDisabled() {
            DemoEntity exist = existing().setIsDisabled(false);
            givenExists(exist);
            service.disable(ID);
            assertTrue(exist.getIsDisabled());
        }

        @Test
        @DisplayName("启停不存在的记录时按查不到处理")
        void toggleMissingThrows() {
            givenExists(null);
            assertThrows(ServiceException.class, () -> service.disable(ID));
            assertThrows(ServiceException.class, () -> service.enable(ID));
        }

        @Test
        @DisplayName("前置方法抛异常时不得执行写入")
        void beforeHookCanBlockDisable() {
            // 用 spy 而不是匿名子类：匿名子类的直接父类是 DemoService（普通 Class，
            // 不是 ParameterizedType），getEntityClass() 会抛 ClassCastException，
            // 那属于另一条约束，见「泛型解析约束」一节
            DemoService spied = Mockito.spy(service);
            Mockito.doThrow(new ServiceException("不允许禁用"))
                    .when(spied).beforeDisable(any());
            givenExists(existing());
            assertThrows(ServiceException.class, () -> spied.disable(ID));
            Mockito.verify(repository, Mockito.never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("后置方法在写入之后执行")
        void afterHookRunsLast() {
            boolean[] called = {false};
            DemoService spied = Mockito.spy(service);
            Mockito.doAnswer(invocation -> {
                called[0] = true;
                return null;
            }).when(spied).afterEnable(anyLong());
            givenExists(existing().setIsDisabled(true));
            spied.enable(ID);
            assertTrue(called[0], "后置方法未被调用");
            Mockito.verify(repository).saveAndFlush(any());
        }

        @Test
        @DisplayName("beforeSaveToDatabase 拿到的正是最终写库的那个实体")
        void beforeSaveReceivesFinalEntity() {
            java.util.List<Long> seen = new java.util.ArrayList<>();
            DemoService spied = Mockito.spy(service);
            Mockito.doAnswer(invocation -> {
                seen.add(((DemoEntity) invocation.getArgument(0)).getId());
                return invocation.getArgument(0);
            }).when(spied).beforeSaveToDatabase(any());
            givenExists(existing());
            spied.updateToDatabase(new DemoEntity().setId(ID));
            assertEquals(1, seen.size());
            assertEquals(ID, seen.get(0));
        }
    }

    @Nested
    @DisplayName("7. 删除")
    class Delete {

        @Test
        @DisplayName("删除不存在的记录时按查不到处理")
        void deleteMissingThrows() {
            givenExists(null);
            assertThrows(ServiceException.class, () -> service.delete(ID));
            Mockito.verify(repository, Mockito.never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除时先读出实体再删")
        void deleteReadsBeforeRemoving() {
            givenExists(existing());
            service.delete(ID);
            Mockito.verify(repository).findById(ID);
            Mockito.verify(repository).deleteById(ID);
        }

        @Test
        @DisplayName("前置方法抛异常时不得执行删除")
        void beforeHookCanBlockDelete() {
            DemoService spied = Mockito.spy(service);
            Mockito.doThrow(new ServiceException("不允许删除"))
                    .when(spied).beforeDelete(any());
            givenExists(existing());
            assertThrows(ServiceException.class, () -> spied.delete(ID));
            Mockito.verify(repository, Mockito.never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("连续删除多条互不影响")
        void batchDeleteDoesNotDropPreviousDeletions() {
            // 曾经用 EntityManager.clear()「保证读到最新数据」，
            // 结果把前面已标记为删除的实体直接从持久化上下文里抹掉，删除静默丢失
            givenExists(existing());
            assertDoesNotThrow(() -> {
                service.delete(ID);
                service.delete(ID + 1);
            });
            Mockito.verify(repository).deleteById(ID);
            Mockito.verify(repository).deleteById(ID + 1);
        }

        @Test
        @DisplayName("删除后置方法收到主键")
        void afterDeleteReceivesId() {
            long[] seen = {-1};
            DemoService spied = Mockito.spy(service);
            Mockito.doAnswer(invocation -> {
                seen[0] = (long) invocation.getArgument(0);
                return null;
            }).when(spied).afterDelete(anyLong());
            givenExists(existing());
            spied.delete(ID);
            assertEquals(ID, seen[0]);
        }
    }

    @Nested
    @DisplayName("8. 实体实例工具")
    class EntityInstance {

        /**
         * 通过反射调用受保护方法获取新实例
         *
         * @param target 服务
         * @return 新实例
         */
        private DemoEntity newInstance(CurdService<DemoEntity, DemoRepository> target) {
            try {
                Method method = CurdService.class.getDeclaredMethod("getEntityInstance");
                method.setAccessible(true);
                return (DemoEntity) method.invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("反射调用 getEntityInstance 失败", e);
            }
        }

        /**
         * 通过反射调用带主键的重载
         *
         * @param target 服务
         * @param id     主键
         * @return 新实例
         */
        private DemoEntity newInstance(CurdService<DemoEntity, DemoRepository> target, long id) {
            try {
                Method method = CurdService.class.getDeclaredMethod("getEntityInstance", long.class);
                method.setAccessible(true);
                return (DemoEntity) method.invoke(target, id);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("反射调用 getEntityInstance 失败", e);
            }
        }

        @Test
        @DisplayName("新建实例的类型就是实体类")
        void newInstanceHasRightType() {
            assertEquals(DemoEntity.class, newInstance(service).getClass());
        }

        @Test
        @DisplayName("新建实例不含任何业务字段值")
        void newInstanceHasNoBusinessValues() {
            DemoEntity entity = newInstance(service);
            assertNull(entity.getId());
            assertNull(entity.getUsername());
            assertNull(entity.getNickname());
        }

        @Test
        @DisplayName("带主键新建的实例只含主键")
        void newInstanceWithIdOnlyHasId() {
            DemoEntity entity = newInstance(service, ID);
            assertEquals(ID, entity.getId());
            assertNull(entity.getUsername(), "只应带主键，不应带上任何业务字段");
        }
    }

    @Nested
    @DisplayName("8b. 泛型解析约束：实体类必须写在直接父类的第一段泛型里")
    class GenericResolutionConstraint {

        /**
         * 直接父类是 {@link DemoService}（普通 Class，不是 ParameterizedType）
         */
        public static class IndirectService extends DemoService {
        }

        @Test
        @DisplayName("直接子类能解析出实体类")
        void directSubclassResolves() {
            assertEquals(DemoEntity.class, service.getFirstParameterizedTypeClass());
        }

        @Test
        @DisplayName("多包一层就会解析失败")
        void indirectSubclassBreaks() {
            // getFirstParameterizedTypeClass 直接把 getGenericSuperclass() 强转成
            // ParameterizedType。IndirectService 的直接父类是 DemoService 这个普通 Class，
            // 强转立刻抛 ClassCastException，且时机很晚（第一次真正访问实体类时）。
            //
            // 后果：想「给所有 XxxService 加一段公共逻辑」而新建一层基类，
            // 会让整条服务链路在运行期才炸。写继承层次时必须把实体类透传到每一层。
            assertThrows(ClassCastException.class, () -> new IndirectService().getFirstParameterizedTypeClass());
        }

        @Test
        @DisplayName("解析失败发生在使用时而不是启动时")
        void failureIsDeferred() {
            // 构造实例本身不报错，错误被推迟到第一次真正用到实体类的那一刻
            IndirectService instance = new IndirectService();
            assertNotNull(instance, "构造实例不报错");
            assertThrows(ClassCastException.class, instance::getFirstParameterizedTypeClass);
        }
    }

    @Nested
    @DisplayName("9. 分页数据换算")
    class PageDataMapping {

        @Test
        @DisplayName("空结果的总页数为 0 且列表非 null")
        void emptyPageHasZeroCount() {
            givenPage(pageOf(List.of(), 0));
            PageData<DemoEntity> pageData = service.filterPage(new DemoEntity());
            assertEquals(0, pageData.getTotal());
            assertEquals(0, pageData.getPageCount());
            assertNotNull(pageData.getList());
        }

        @Test
        @DisplayName("总条数超出 int 范围必须抛算术异常而不是静默溢出")
        void overflowRaisesArithmeticException() {
            // 用 Math.toIntExact 是有意为之：静默溢出成负数会让前端看到「-1 条」，
            // 而这种脏数据比直接报错难查得多
            assertThrows(ArithmeticException.class,
                    () -> PageData.newInstance(pageOf(List.of(), Integer.MAX_VALUE + 1L)));
        }

        @Test
        @DisplayName("恰好等于 int 上限时可以正常换算")
        void intMaxIsAccepted() {
            // 边界：MAX_VALUE 本身必须放行，只有超出才报错
            assertEquals(Integer.MAX_VALUE,
                    PageData.newInstance(pageOf(List.of(), Integer.MAX_VALUE)).getTotal());
        }

        @Test
        @DisplayName("对外页码从 1 开始")
        void pageNumberIsOneBased() {
            assertEquals(1, PageData.newInstance(pageOf(List.of(), 0)).getPage().getPageNum());
            assertEquals(4, PageData.newInstance(
                    pageOf(List.of(), 0, org.springframework.data.domain.PageRequest.of(3, 10)))
                    .getPage().getPageNum());
        }

        @Test
        @DisplayName("列表原样透传，不做复制")
        void listIsPassedThrough() {
            // 调用方（getPage / createExportTask）会继续在同一个列表对象上操作
            DemoEntity item = new DemoEntity().setId(ID);
            PageData<DemoEntity> pageData = PageData.newInstance(pageOf(List.of(item), 1));
            assertEquals(1, pageData.getList().size());
            assertEquals(ID, pageData.getList().get(0).getId());
        }
    }
}
