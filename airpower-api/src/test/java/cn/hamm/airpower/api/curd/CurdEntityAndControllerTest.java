package cn.hamm.airpower.api.curd;

import cn.hamm.airpower.api.base.ApiConfig;
import cn.hamm.airpower.api.fixture.DemoController;
import cn.hamm.airpower.api.fixture.DemoEntity;
import cn.hamm.airpower.api.fixture.DemoService;
import cn.hamm.airpower.api.fixture.NoDefaultConstructorEntity;
import cn.hamm.airpower.api.fixture.ReadOnlyController;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.common.ReadOnly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>实体基类与控制器基类单元测试</h2>
 *
 * <p>两者都靠反射工作，失败方式统一是「运行期才炸」，所以要把边界钉在开发期：</p>
 * <ul>
 *     <li>{@code copy} / {@code copyOnlyId} 依赖子类的<b>公有无参构造</b>，
 *     少写一个就整条链路不可用</li>
 *     <li>{@code getEntityClass()} 依赖「实体类型写在第一段泛型里」，
 *     中间插一层就解析失败</li>
 *     <li>控制器先把只读字段剔除再交给服务，
 *     否则客户端可以覆盖创建时间、禁用标记这类服务端字段</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("实体基类与控制器基类单元测试")
class CurdEntityAndControllerTest {

    @Nested
    @DisplayName("1. 实体复制")
    class EntityCopy {

        @Test
        @DisplayName("copyOnlyId 只复制主键")
        void copyOnlyIdKeepsJustId() {
            // 用于 deleteById 之类只需主键的场景。
            // 若把业务字段也带过去，会被 Spring Data 误判成一次「按样例删除」
            DemoEntity source = new DemoEntity().setId(1L).setUsername("u").setNickname("n");
            DemoEntity copy = source.copyOnlyId();
            assertEquals(1L, copy.getId());
            assertNull(copy.getUsername());
            assertNull(copy.getNickname());
        }

        @Test
        @DisplayName("copyOnlyId 保留 null 主键")
        void copyOnlyIdKeepsNullId() {
            assertNull(new DemoEntity().copyOnlyId().getId());
        }

        @Test
        @DisplayName("copy 复制全部字段")
        void copyCopiesEverything() {
            DemoEntity source = new DemoEntity().setId(1L).setUsername("u").setNickname("n");
            DemoEntity copy = source.copy();
            assertNotSame(source, copy);
            assertEquals(1L, copy.getId());
            assertEquals("u", copy.getUsername());
            assertEquals("n", copy.getNickname());
        }

        @Test
        @DisplayName("copy 得到的对象与原对象相互独立")
        void copyIsIndependent() {
            // createSpecification 里用 filter.copy() 构造查询条件，
            // 若两者共享引用，beforeCreatePredicate 的改动会漏回调用方的过滤器
            DemoEntity source = new DemoEntity().setUsername("原");
            DemoEntity copy = source.copy();
            copy.setUsername("改");
            assertEquals("原", source.getUsername());
        }

        @Test
        @DisplayName("缺少无参构造时 copyOnlyId 抛出可读异常")
        void copyOnlyIdWithoutDefaultConstructorThrows() {
            // 反射实例化用的是 getConstructor()，只找公有无参构造。
            // 漏写时必须给出「哪一步失败」的信息，否则线上只会看到一片
            // NoSuchMethodException，看不出是哪个实体
            NoDefaultConstructorEntity source = new NoDefaultConstructorEntity("x");
            ServiceException exception = assertThrows(ServiceException.class, source::copyOnlyId);
            assertTrue(exception.getMessage().contains("复制单 ID 实体失败"),
                    exception.getMessage());
        }

        @Test
        @DisplayName("缺少无参构造时 copy 抛出可读异常")
        void copyWithoutDefaultConstructorThrows() {
            NoDefaultConstructorEntity source = new NoDefaultConstructorEntity("x");
            ServiceException exception = assertThrows(ServiceException.class, source::copy);
            assertTrue(exception.getMessage().contains("复制新实体失败"), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("2. 公共字段")
    class CommonFields {

        @Test
        @DisplayName("setter 返回自身以支持链式赋值")
        void settersAreChainable() {
            DemoEntity entity = new DemoEntity();
            assertEquals(entity, entity.setId(1L));
            assertEquals(entity, entity.setIsDisabled(true));
            assertEquals(entity, entity.setCreateTime(1L));
            assertEquals(entity, entity.setUpdateTime(1L));
        }

        @Test
        @DisplayName("主键字段名为 id")
        void idFieldName() {
            // 大量地方按字符串 "id" 取字段（排序、唯一校验、权限），
            // 改名会让这些地方静默失效
            assertEquals("id", CurdEntity.STRING_ID);
        }

        @Test
        @DisplayName("创建时间字段名为 createTime")
        void createTimeFieldName() {
            assertEquals("createTime", CurdEntity.STRING_CREATE_TIME);
        }

        @Test
        @DisplayName("创建/修改时间与禁用标记都标了只读")
        void serverOwnedFieldsAreReadOnly() throws Exception {
            for (String name : List.of("isDisabled", "createTime", "updateTime")) {
                Field field = CurdEntity.class.getDeclaredField(name);
                assertNotNull(field.getAnnotation(ReadOnly.class),
                        name + " 未标 @ReadOnly，客户端可覆盖它");
            }
        }

        @Test
        @DisplayName("排除只读字段后它们被置空")
        void excludeReadOnlyClearsServerFields() {
            // 只读字段在出参里已由 @ReadOnly 裁掉，入参这一侧靠 excludeReadOnly 清空，
            // 两侧都做才不会出现「请求体里塞一个 createTime 就把审计时间改了」
            DemoEntity entity = new DemoEntity().setCreateTime(1L).setUpdateTime(2L).setIsDisabled(true);
            entity.excludeReadOnly();
            assertNull(entity.getCreateTime());
            assertNull(entity.getUpdateTime());
            assertNull(entity.getIsDisabled());
        }

        @Test
        @DisplayName("排除只读不会动到业务字段")
        void excludeReadOnlyKeepsBusinessFields() {
            DemoEntity entity = new DemoEntity().setUsername("u").setCreateTime(1L);
            entity.excludeReadOnly();
            assertEquals("u", entity.getUsername());
        }

        @Test
        @DisplayName("排除只读对自引用实体不会栈溢出")
        void excludeReadOnlyHandlesSelfReference() {
            // 自引用（树形结构）在实体里很常见，
            // 递归裁剪若没有已访问集合会直接 StackOverflowError
            DemoEntity root = new DemoEntity().setUsername("root").setCreateTime(1L);
            root.setParent(root);
            assertDoesNotThrow(root::excludeReadOnly);
        }
    }

    @Nested
    @DisplayName("3. 控制器基类")
    class ControllerBase {

        private DemoController controller;
        private DemoService service;

        @BeforeEach
        void setUp() {
            controller = new DemoController();
            service = Mockito.mock(DemoService.class);
            inject(controller, "service", service);
            inject(controller, "apiConfig", new ApiConfig());
        }

        /**
         * 反射注入字段
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

        @Test
        @DisplayName("实体类从直接父类的第一段泛型解析")
        void entityClassResolved() {
            assertEquals(DemoEntity.class, controller.getEntityClass());
        }

        @Test
        @DisplayName("两个控制器的实体类各自独立")
        void differentControllersDifferentEntity() {
            assertEquals(DemoEntity.class, new ReadOnlyController().getEntityClass());
        }
    }

    @Nested
    @DisplayName("4. 接口返回值")
    class EndpointResponses {

        private DemoController controller;
        private DemoService service;

        @BeforeEach
        void setUp() {
            controller = new DemoController();
            service = Mockito.mock(DemoService.class);
            // 控制器的每个接口都以「只含主键的实体」作为出参，
            // 这里补上真实实现的行为，否则出参恒为 null
            Mockito.when(service.getEntityInstance(org.mockito.ArgumentMatchers.anyLong()))
                    .thenAnswer(invocation -> new DemoEntity()
                            .setId((Long) invocation.getArgument(0)));
            inject(controller, "service", service);
            inject(controller, "apiConfig", new ApiConfig());
        }

        /**
         * 反射注入字段
         *
         * @param target 目标
         * @param name   字段名
         * @param value  值
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

        @Test
        @DisplayName("新增成功时只回传主键")
        void addReturnsOnlyId() {
            Mockito.when(service.add(Mockito.any())).thenReturn(42L);
            Json json = controller.add(new DemoEntity().setUsername("u"));
            assertEquals(Json.SUCCESS_CODE, json.getCode());
            assertEquals("添加成功", json.getMessage());
            assertEquals(42L, ((DemoEntity) json.getData()).getId());
        }

        @Test
        @DisplayName("新增前先剔除只读字段")
        void addExcludesReadOnlyBeforeSave() {
            Mockito.when(service.add(Mockito.any())).thenReturn(42L);
            controller.add(new DemoEntity().setUsername("u").setCreateTime(1L));
            org.mockito.ArgumentCaptor<DemoEntity> captor =
                    org.mockito.ArgumentCaptor.forClass(DemoEntity.class);
            Mockito.verify(service).add(captor.capture());
            assertNull(captor.getValue().getCreateTime(), "创建时间被原样透传到服务层");
        }

        @Test
        @DisplayName("修改成功时回传提交上来的主键")
        void updateReturnsId() {
            Json json = controller.update(new DemoEntity().setId(7L));
            assertEquals(Json.SUCCESS_CODE, json.getCode());
            assertEquals("修改成功", json.getMessage());
            assertEquals(7L, ((DemoEntity) json.getData()).getId());
        }

        @Test
        @DisplayName("详情查询透传服务层结果")
        void getDetailPassesThrough() {
            DemoEntity stored = new DemoEntity().setId(7L).setUsername("u");
            Mockito.when(service.get(7L)).thenReturn(stored);
            Json json = controller.getDetail(new DemoEntity().setId(7L));
            assertEquals(Json.SUCCESS_CODE, json.getCode());
            assertEquals(stored, json.getData());
        }

        @Test
        @DisplayName("禁用与启用都回传主键")
        void toggleReturnsId() {
            Mockito.when(service.get(7L)).thenReturn(new DemoEntity().setId(7L));
            assertEquals(7L, ((DemoEntity) controller.disable(new DemoEntity().setId(7L)).getData()).getId());
            assertEquals(7L, ((DemoEntity) controller.enable(new DemoEntity().setId(7L)).getData()).getId());
        }

        @Test
        @DisplayName("删除时先查出实体再交给服务")
        void deleteReadsFirst() {
            DemoEntity stored = new DemoEntity().setId(7L);
            Mockito.when(service.get(7L)).thenReturn(stored);
            Json json = controller.delete(new DemoEntity().setId(7L));
            assertEquals("删除成功", json.getMessage());
            Mockito.verify(service).delete(7L);
        }

        @Test
        @DisplayName("服务层抛出的异常原样冒泡")
        void serviceExceptionPropagates() {
            // 控制器不做 try-catch：异常统一交给 @ControllerAdvice，
            // 在这里吞掉只会让前端拿到一个没有信息量的成功响应
            Mockito.when(service.get(7L)).thenThrow(new ServiceException("查不到"));
            assertThrows(ServiceException.class,
                    () -> controller.getDetail(new DemoEntity().setId(7L)));
        }
    }

    @Nested
    @DisplayName("5. 查询请求补齐")
    class QueryRequestNormalization {

        private DemoController controller;
        private DemoService service;

        @BeforeEach
        void setUp() {
            controller = new DemoController();
            service = Mockito.mock(DemoService.class);
            inject(controller, "service", service);
            inject(controller, "apiConfig", new ApiConfig());
        }

        /**
         * 反射注入字段
         *
         * @param target 目标
         * @param name   字段名
         * @param value  值
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

        @Test
        @DisplayName("列表查询传 null 请求时补一个默认请求")
        void listRequestNullTolerated() {
            Mockito.when(service.getList(Mockito.any())).thenReturn(List.of());
            assertNotNull(controller.getList(null).getData());
        }

        @Test
        @DisplayName("列表查询传 null 过滤器时补一个空实体")
        void listFilterNullTolerated() {
            // 过滤器为 null 时必须 new 一个，否则服务层拼查询条件时无处下手
            Mockito.when(service.getList(Mockito.any())).thenReturn(List.of());
            cn.hamm.airpower.api.query.request.QueryListRequest<DemoEntity> request =
                    new cn.hamm.airpower.api.query.request.QueryListRequest<>();
            request.setFilter(null);
            assertNotNull(controller.getList(request).getData());
            org.mockito.ArgumentCaptor<cn.hamm.airpower.api.query.request.QueryListRequest<DemoEntity>> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            cn.hamm.airpower.api.query.request.QueryListRequest.class);
            Mockito.verify(service).getList(captor.capture());
            assertNotNull(captor.getValue().getFilter(), "过滤器未补齐");
        }

        @Test
        @DisplayName("分页查询传 null 请求时补一个默认请求")
        void pageRequestNullTolerated() {
            Mockito.when(service.getPage(Mockito.any())).thenReturn(null);
            assertDoesNotThrow(() -> controller.getPage(null));
        }
    }
}
