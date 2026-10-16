package cn.hamm.airpower.api.curd;

import cn.hamm.airpower.api.fixture.DemoController;
import cn.hamm.airpower.api.fixture.DemoEntity;
import cn.hamm.airpower.api.fixture.DemoRepository;
import cn.hamm.airpower.api.fixture.DemoService;
import cn.hamm.airpower.api.fixture.EmptyExtendsController;
import cn.hamm.airpower.api.fixture.NarrowChildController;
import cn.hamm.airpower.api.fixture.ReadOnlyController;
import cn.hamm.airpower.api.fixture.UndeclaredController;
import cn.hamm.airpower.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.hamm.airpower.exception.Errors.API_SERVICE_UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>增删改查接口可见性单元测试</h2>
 *
 * <p>{@code @Extends} 的规则是<b>默认拒绝 + 就近声明</b>：</p>
 * <ul>
 *     <li>只有 {@code value} 一个属性，写了什么开放什么，必须写全</li>
 *     <li>沿继承链自下而上找，<b>最近的那层说了算</b>，不与父类合并</li>
 *     <li>整条链都没标注时一个接口都不开放</li>
 * </ul>
 *
 * <p>三条里最容易写反的是第二条：若改成「合并」，子类写
 * {@code @Extends(Curd.Add)} 反而会继承父类的全部接口，
 * 「收窄」这个意图彻底失效——正是修复前白名单失效的那个形态。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("增删改查接口可见性单元测试")
class CurdTest {

    /**
     * 同一个接口在注解里写了两次：结果必须去重
     */
    @Extends({Curd.Add, Curd.Add, Curd.GetDetail})
    public static class DuplicatedExtendsController extends DemoController {
    }

    @Nested
    @DisplayName("1. 白名单真正生效")
    class WhiteList {

        @Test
        @DisplayName("只开放 value 里列出的接口")
        void onlyDeclaredApisAvailable() {
            // 这条在修复前是<b>失败</b>的：旧实现在走到 Object 时会无条件
            // addAll(全部 Curd)，白名单只是被追加进去，起不到收敛作用
            List<Curd> list = Curd.getCurdList(ReadOnlyController.class);
            assertEquals(2, list.size(), "实际: " + list);
            assertTrue(list.contains(Curd.Add));
            assertTrue(list.contains(Curd.GetDetail));
        }

        @Test
        @DisplayName("未声明的接口不在列表里")
        void undeclaredApisAbsent() {
            List<Curd> list = Curd.getCurdList(ReadOnlyController.class);
            for (Curd curd : List.of(Curd.Delete, Curd.Update, Curd.GetPage, Curd.Export)) {
                assertFalse(list.contains(curd), curd + " 不该被开放");
            }
        }

        @Test
        @DisplayName("未声明的接口调用时报错")
        void undeclaredApiRejected() {
            ReadOnlyController controller = new ReadOnlyController();
            assertDoesNotThrow(() -> Curd.Add.checkApiAvailable(controller));
            assertThrows(ServiceException.class, () -> Curd.Delete.checkApiAvailable(controller));
            assertThrows(ServiceException.class, () -> Curd.Update.checkApiAvailable(controller));
            assertThrows(ServiceException.class, () -> Curd.Export.checkApiAvailable(controller));
        }

        @Test
        @DisplayName("请求的接口暂未实现")
        void rejectionMessageNamesControllerAndApi() {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> Curd.Delete.checkApiAvailable(new ReadOnlyController()));
            assertEquals(API_SERVICE_UNSUPPORTED.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("请求的接口暂未实现"), exception.getMessage());
        }

        @Test
        @DisplayName("列表里没有重复项")
        void noDuplicates() {
            for (Curd curd : Curd.values()) {
                Curd[] duplicated = new Curd[]{curd, curd};
                List<Curd> list = Curd.getCurdList(DuplicatedExtendsController.class);
                long count = list.stream().filter(curd::equals).count();
                assertTrue(count <= 1, curd + " 重复出现: " + list);
            }
        }

    }

    @Nested
    @DisplayName("2. 默认拒绝")
    class DefaultDeny {

        @Test
        @DisplayName("未标注 @Extends 时一个接口都不开放")
        void undeclaredControllerExposesNothing() {
            // 「默认全开」意味着漏写注解的控制器会把增删改查全部暴露到线上，
            // 且要靠人工审计权限表才能发现。默认拒绝把这类事故挪到开发期
            List<Curd> list = Curd.getCurdList(UndeclaredController.class);
            assertTrue(list.isEmpty(), "未标注时应当什么都不开放，实际: " + list);
        }

        @Test
        @DisplayName("未标注时所有接口调用都报错")
        void allApisRejected() {
            UndeclaredController controller = new UndeclaredController();
            for (Curd curd : Curd.values()) {
                assertThrows(ServiceException.class, () -> curd.checkApiAvailable(controller),
                        curd + " 不该被放行");
            }
        }

        @Test
        @DisplayName("@Extends 留空等价于不开放任何接口")
        void emptyAnnotationExposesNothing() {
            // 显式的「什么都不开放」与「忘了写」运行期表现一致，
            // 但前者是明确决定、后者是疏误，排查时要能区分
            assertTrue(Curd.getCurdList(EmptyExtendsController.class).isEmpty());
        }

        @Test
        @DisplayName("父类标注了也不会让未标注的子类自动开放")
        void parentAnnotationDoesNotLeakToUndeclaredChild() {
            // DemoController 声明了全部接口，但 UndeclaredController 自己没写，
            // 查找是「自下而上找第一个标注」而不是「向上收集所有标注」
            assertTrue(Curd.getCurdList(UndeclaredController.class).isEmpty());
        }
    }

    @Nested
    @DisplayName("3. 就近声明，不做合并")
    class NearestDeclarationWins {

        @Test
        @DisplayName("子类声明只覆盖父类，不与之合并")
        void childReplacesParent() {
            // NarrowChildController 只声明 Add，其父类 DemoController 声明了全部 10 个。
            // 若实现成「合并」，这里会得到 10 个接口，收窄意图彻底失效
            List<Curd> list = Curd.getCurdList(NarrowChildController.class);
            assertEquals(1, list.size(), "子类的声明应完整替换父类，实际: " + list);
            assertTrue(list.contains(Curd.Add));
        }

        @Test
        @DisplayName("父类开放的能力不会漏给子���")
        void parentCapabilitiesDoNotLeak() {
            NarrowChildController controller = new NarrowChildController();
            assertDoesNotThrow(() -> Curd.Add.checkApiAvailable(controller));
            assertThrows(ServiceException.class, () -> Curd.Delete.checkApiAvailable(controller));
        }

        @Test
        @DisplayName("写全即可获得全部能力")
        void declaringAllGrantsAll() {
            assertEquals(Curd.values().length, Curd.getCurdList(DemoController.class).size());
        }

        @Test
        @DisplayName("多层继承同样只看最近的一层")
        void onlyNearestLevelConsidered() {
            // 中间层没有 @Extends，查找会跳过它继续向上找到 DemoController
            assertEquals(Curd.values().length, Curd.getCurdList(SkipMiddleController.class).size());
        }

        /**
         * 中间层不带 {@code @Extends}，查找应跳过它继续向上
         */
        public static class SkipMiddleController extends DemoController {
        }
    }

    @Nested
    @DisplayName("4. 返回值的稳定性")
    class ResultStability {

        @Test
        @DisplayName("重复调用结果一致")
        void repeatedCallsAgree() {
            assertEquals(Curd.getCurdList(DemoController.class), Curd.getCurdList(DemoController.class));
            assertEquals(Curd.getCurdList(ReadOnlyController.class), Curd.getCurdList(ReadOnlyController.class));
        }

        @Test
        @DisplayName("返回的是不可变列表")
        void returnedListIsImmutable() {
            // 注解数组每次读取都会新建，不加 distinct 时重复声明会出现两份；
            // 返回不可变列表则杜绝调用方就地改动的可能
            List<Curd> list = Curd.getCurdList(DemoController.class);
            assertThrows(UnsupportedOperationException.class, () -> list.add(Curd.Delete));
        }

        @Test
        @DisplayName("注解里重复声明同一项会被去重")
        void duplicatesRemoved() {
            List<Curd> list = Curd.getCurdList(DuplicatedExtendsController.class);
            assertEquals(2, list.size(), "实际: " + list);
        }
    }

    @Nested
    @DisplayName("5. checkApiAvailable")
    class CheckApiAvailable {

        @Test
        @DisplayName("抛业务异常而不是返回 false")
        void throwsInsteadOfReturningFalse() {
            // 调用方全是「直接调用、靠异常阻断」的风格，
            // 返回 false 需要每个调用点都记得判断，漏一个就是接口暴露
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> Curd.Delete.checkApiAvailable(new ReadOnlyController()));
            assertEquals(API_SERVICE_UNSUPPORTED.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("按实例的运行期类型判定，不是按声明类型")
        void judgedByRuntimeType() {
            // 同一个方法被父类与子类共用时，判定必须落在实际对象上。
            // 否则子类收敛掉的接口会在父类实例上被误放行
            assertThrows(ServiceException.class,
                    () -> Curd.Delete.checkApiAvailable(new NarrowChildController()));
            assertDoesNotThrow(() -> Curd.Delete.checkApiAvailable(new DemoController()));
        }

        @Test
        @DisplayName("开放全部的控制器上所有接口都可用")
        void allApisAvailableWhenDeclaredAll() {
            DemoController controller = new DemoController();
            for (Curd curd : Curd.values()) {
                assertDoesNotThrow(() -> curd.checkApiAvailable(controller), curd + " 应当可用");
            }
        }
    }

    @Nested
    @DisplayName("7. 不再抛异常（权限扫描的中断隐患已消除）")
    class NoLongerThrows {

        @Test
        @DisplayName("getCurdList 对任何类都不会抛异常")
        void neverThrows() {
            // 旧实现在遇到「value 与 exclude 同时列出同一项」时抛 IllegalStateException，
            // 而 PermissionUtil.scanPermission 的 outer catch 会把这个异常吞掉并
            // 立刻返回整个方法 —— 一个配置错误的控制器会连带把同包其它控制器的权限
            // 一起吞掉，日志里只剩一行「扫描权限出错」。
            // 去掉 exclude 之后这条异常路径不复存在，扫描不会再被单个坏类中断
            for (Class<?> clazz : List.of(DemoController.class, ReadOnlyController.class,
                    UndeclaredController.class, EmptyExtendsController.class,
                    NarrowChildController.class, CurdController.class, Object.class)) {
                assertDoesNotThrow(() -> Curd.getCurdList(clazz), clazz.getName() + " 抛了异常");
            }
        }

        @Test
        @DisplayName("Object 也返回空列表而不是死循环")
        void objectClassTerminates() {
            // 递归的终止条件是「clazz == Object」，Object 自身不该再有父类可查
            assertTrue(Curd.getCurdList(Object.class).isEmpty());
        }
    }

    @Nested
    @DisplayName("6. 泛型约束")
    class GenericContract {

        @Test
        @DisplayName("实体类型必须写在第一段泛型里")
        void entityTypeInFirstTypeArgument() {
            // 中间插一段别的父类会解析失败，getEntityClass() 抛 ClassCastException。
            // 这条断言提醒：新增控制器时不要在 DemoEntity 前面塞别的泛型参数
            assertEquals(DemoEntity.class, new DemoController().getEntityClass());
        }

        @Test
        @DisplayName("服务与控制器共用同一个实体类")
        void serviceAndControllerShareEntityClass() {
            assertEquals(DemoEntity.class, new DemoService().getFirstParameterizedTypeClass());
        }

        @Test
        @DisplayName("数据源与实体匹配")
        void repositoryMatchesEntity() {
            assertTrue(DemoRepository.class.getGenericInterfaces().length > 0);
        }
    }
}
