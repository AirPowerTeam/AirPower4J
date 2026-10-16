package cn.hamm.airpower.api.permission;

import cn.hamm.airpower.api.access.Access;
import cn.hamm.airpower.api.curd.Curd;
import cn.hamm.airpower.api.fixture.DemoController;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.core.reflect.Description;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>权限工具类单元测试</h2>
 *
 * <p>这里有两条容易写反的规则，都直接影响鉴权结果：</p>
 * <ul>
 *     <li>方法上的 {@link Permission} <b>整体覆盖</b>类上的，而不是逐属性合并。
 *     逐属性合并的话，方法上只写 {@code login = false} 会让 {@code authorize}
 *     仍然沿用类上的 {@code true}，于是「无需登录」的方法反而要求授权</li>
 *     <li>{@code login = false} 时 {@code authorize} 必须强制为 {@code false}。
 *     未登录就没有身份，无身份可谈授权</li>
 * </ul>
 *
 * <p>权限标识则是「类 + 方法」的全局唯一真源：它最终进数据库，
 * 一旦生成规则变了，历史权限数据会整体失效。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("权限工具类单元测试")
class PermissionUtilTest {

    /**
     * 权限标识既是静态状态又被全局配置改写，必须复位，否则用例顺序会影响结果
     */
    @BeforeEach
    @AfterEach
    void resetStatics() {
        PermissionUtil.setBasePackageName("");
        PermissionUtil.setPermissionWithPackage(true);
    }

    /**
     * 取出类中指定名称的方法
     *
     * @param clazz  类
     * @param name   方法名
     * @return 方法
     */
    private static Method method(Class<?> clazz, String name) {
        return java.util.Arrays.stream(clazz.getMethods())
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到方法: " + name));
    }

    @Nested
    @DisplayName("1. 访问要求解析")
    class GetWhatNeedAccess {

        @Test
        @DisplayName("类和方法都没标记时默认既登录又授权")
        void defaultIsLoginAndAuthorize() {
            Access access = PermissionUtil.getWhatNeedAccess(PlainController.class,
                    method(PlainController.class, "plain"));
            assertTrue(access.isLogin());
            assertTrue(access.isAuthorize());
        }

        @Test
        @DisplayName("类上标记为默认值，作用于所有方法")
        void classLevelAnnotationApplies() {
            Access access = PermissionUtil.getWhatNeedAccess(PublicController.class,
                    method(PublicController.class, "plainEndpoint"));
            assertFalse(access.isLogin());
            assertFalse(access.isAuthorize());
        }

        @Test
        @DisplayName("方法上只写 login = false 时 authorize 必须一并变成 false")
        void methodLoginFalseForcesAuthorizeFalse() {
            // 这是最容易写错的一条：若按「逐属性覆盖」实现，
            // 这里 authorize 会沿用类上的 true，变成「无需登录但要授权」
            Access access = PermissionUtil.getWhatNeedAccess(PublicController.class,
                    method(PublicController.class, "noLoginOnly"));
            assertFalse(access.isLogin());
            assertFalse(access.isAuthorize());
        }

        @Test
        @DisplayName("方法上只写 authorize = false 时仍然需要登录")
        void methodAuthorizeFalseKeepsLogin() {
            Access access = PermissionUtil.getWhatNeedAccess(PublicController.class,
                    method(PublicController.class, "loginButNoAuthorize"));
            assertTrue(access.isLogin());
            assertFalse(access.isAuthorize());
        }

        @Test
        @DisplayName("方法上的标记整体覆盖类上的")
        void methodOverridesClassWholesale() {
            // 类上是「需要登录 + 需要授权」，方法上写「需要登录 + 不授权」
            Access access = PermissionUtil.getWhatNeedAccess(LockedController.class,
                    method(LockedController.class, "openUp"));
            assertTrue(access.isLogin());
            assertFalse(access.isAuthorize());
        }

        @Test
        @DisplayName("类上是免登录时，方法上的 authorize=true 不会把它拽回授权态")
        void classLoginFalseCannotBeUpgradedByMethod() {
            Access access = PermissionUtil.getWhatNeedAccess(PublicController.class,
                    method(PublicController.class, "noLoginNoAuthorize"));
            assertFalse(access.isLogin());
            assertFalse(access.isAuthorize());
        }

        @Test
        @DisplayName("@Inherited 使基类标记被子类继承")
        void inheritedAnnotationAppliesToSubclass() {
            // CurdController 基类就是靠这一点让所有子接口默认需要登录的
            Access access = PermissionUtil.getWhatNeedAccess(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertTrue(access.isLogin());
        }
    }

    @Nested
    @DisplayName("2. 权限标识生成")
    class PermissionIdentity {

        @Test
        @DisplayName("标识形如 类标识:方法名")
        void identityShape() {
            String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertTrue(identity.endsWith(":getDetail"), "实际: " + identity);
            assertFalse(identity.startsWith(":"), "类标识部分不得为空: " + identity);
        }

        @Test
        @DisplayName("Controller 后缀被去掉")
        void controllerSuffixRemoved() {
            // 去掉后缀是为了换实现类（如 XxxServiceController → Xxx）时标识不变
            String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertFalse(identity.contains("Controller"), "后缀未去掉: " + identity);
        }

        @Test
        @DisplayName("未配置基础包名时点号被整体去掉，得到扁平化的全限定名")
        void withoutBasePackageDotsAreStripped() {
            // 实际行为：`basePackageName + "."` 在未配置时就是字符串 `"."`，
            // 于是 `replace(".", "")` 把类名里**所有**点号都删掉，
            // 权限标识退化成 `cnhammairpowerapifixtureDemo:getDetail`。
            // 这不产生歧义（包边界虽丢失，拼接后的全名仍唯一），
            // 但会让「权限标识长得像包名路径」的排查习惯失效——固化行为，别再猜
            PermissionUtil.setBasePackageName("");
            String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertEquals("cnhammairpowerapifixtureDemo:getDetail", identity);
        }

        @Test
        @DisplayName("标识在未配置基础包名时也不会为空")
        void identityNeverEmpty() {
            for (String base : new String[]{"", "cn.hamm", "cn.hamm.airpower", "cn.hamm.airpower.api.fixture"}) {
                PermissionUtil.setBasePackageName(base);
                String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                        method(DemoController.class, "getDetail"));
                assertFalse(identity.isEmpty(), "基础包名 [" + base + "] 下标识为空");
                assertTrue(identity.endsWith(":getDetail"), "基础包名 [" + base + "] 下标识异常: " + identity);
            }
        }

        @Test
        @DisplayName("配置基础包名后该前缀被裁掉，剩余部分保留点号")
        void basePackagePrefixStripped() {
            // 注意首字母小写只作用于整个字符串的第一个字符，
            // 末段类名首字母保持原样（Demo 而不是 demo）
            PermissionUtil.setBasePackageName("cn.hamm.airpower");
            String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertEquals("api.fixture.Demo:getDetail", identity);
        }

        @Test
        @DisplayName("配置的基础包名与类名不匹配时标识退化为全限定名")
        void unmatchedBasePackageKeepsFullName() {
            PermissionUtil.setBasePackageName("com.other.project");
            String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertFalse(identity.isEmpty());
            assertTrue(identity.endsWith(":getDetail"));
        }

        @Test
        @DisplayName("关闭包名前缀后只取简单类名")
        void withoutPackageOnlySimpleName() {
            // 多模块存在同名控制器时会撞标识，因此默认必须带上包名
            PermissionUtil.setPermissionWithPackage(false);
            String identity = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            assertEquals("demo:getDetail", identity);
        }

        @Test
        @DisplayName("不同控制器的标识互不相同")
        void differentControllersDifferentIdentity() {
            PermissionUtil.setPermissionWithPackage(false);
            String a = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "getDetail"));
            String b = PermissionUtil.getPermissionIdentity(PublicController.class,
                    method(PublicController.class, "plainEndpoint"));
            assertFalse(a.equals(b), "两个控制器撞了标识");
        }

        @Test
        @DisplayName("内部类的 $ 被转成 .")
        void innerClassDollarReplaced() {
            // 扫描权限时控制器可能是内部类，$ 转成 . 才能和包名路径对得上
            String identity = PermissionUtil.getPermissionIdentity(NestedController.class,
                    method(NestedController.class, "nested"));
            assertFalse(identity.contains("$"), "内部类标识仍含 $: " + identity);
        }

        @Test
        @DisplayName("标识只由类和方法决定，与是否重复调用无关")
        void identityIsStable() {
            String first = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "add"));
            String second = PermissionUtil.getPermissionIdentity(DemoController.class,
                    method(DemoController.class, "add"));
            assertEquals(first, second);
        }
    }

    @Nested
    @DisplayName("3. 方法是否算「接口」")
    class MethodMapping {

        @Test
        @DisplayName("@PostMapping / @GetMapping / @RequestMapping 都算接口")
        void mappingAnnotationsCount() {
            assertNotNull(identityOf(PublicController.class, "mappedPost"));
            assertNotNull(identityOf(PublicController.class, "mappedGet"));
            assertNotNull(identityOf(PublicController.class, "mappedAny"));
        }

        @Test
        @DisplayName("非映射注解的方法不生成权限")
        void plainMethodHasNoIdentity() {
            // 内部方法不该出现在权限列表里，否则前端会拿到一串点不动的按钮
            assertNull(identityOf(PublicController.class, "plainEndpoint"));
        }

        /**
         * 取出控制器方法对应的权限标识
         *
         * @param clazz 类
         * @param name  方法名
         * @return 标识，未识别为接口时返回 {@code null}
         * @apiNote {@code getMethodPermissionIdentity} 是私有的「是否为对外接口」判定，
         * 反射调它比绕一大圈去断言扫描结果更直接
         */
        private String identityOf(Class<?> clazz, String name) {
            try {
                Method target = PermissionUtil.class
                        .getDeclaredMethod("getMethodPermissionIdentity", Method.class);
                target.setAccessible(true);
                return (String) target.invoke(null, method(clazz, name));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("反射调用失败", e);
            }
        }
    }

    @Nested
    @DisplayName("4. 密码散列")
    class EncodePassword {

        private static final String PASSWORD = "Passw0rd!";
        private static final String SALT = "s3cr3t-salt";

        @Test
        @DisplayName("散列稳定且可重复")
        void deterministic() {
            assertEquals(PermissionUtil.encodePassword(PASSWORD, SALT),
                    PermissionUtil.encodePassword(PASSWORD, SALT));
        }

        @Test
        @DisplayName("不同密码得到不同散列")
        void differentPasswordDifferentHash() {
            assertFalse(PermissionUtil.encodePassword(PASSWORD, SALT)
                    .equals(PermissionUtil.encodePassword("other", SALT)));
        }

        @Test
        @DisplayName("不同盐得到不同散列")
        void differentSaltDifferentHash() {
            assertFalse(PermissionUtil.encodePassword(PASSWORD, SALT)
                    .equals(PermissionUtil.encodePassword(PASSWORD, "another-salt")));
        }

        @Test
        @DisplayName("密码与盐交换位置得到【不同】散列")
        void passwordAndSaltSwapProduceDifferentHash() {
            // 实现是 sha1( sha1(p+s) + sha1(s+p) )，两段是按固定先后顺序拼接的，
            // 而字符串拼接不可交换，所以交换密码与盐会得到不同结果。
            //
            // 方法上的 javadoc 写着「消除密码与盐交换位置的歧义」，容易被读成
            // 「两者应当同散列」——那需要先把两段排序再拼。这里把真实行为钉死，
            // 免得日后有人按 javadoc 的字面意思去改实现、把老用户的密码全部作废。
            //
            // 顺带说明：安全性并不依赖这个性质。真正起作用的是
            // 「同样的 (密码, 盐) 恒得同一结果」+「盐不存明文且每用户不同」。
            assertNotEquals(PermissionUtil.encodePassword(PASSWORD, SALT),
                    PermissionUtil.encodePassword(SALT, PASSWORD),
                    "交换密码与盐会改变散列——与 javadoc 的字面表述不符，以本用例为准");
        }

        @Test
        @DisplayName("散列对密码与盐的每一种字节都敏感")
        void hashIsSensitiveToEveryByte() {
            String base = PermissionUtil.encodePassword("abcdef", "ghijkl");
            assertNotEquals(base, PermissionUtil.encodePassword("abcdefX", "ghijkl"));
            assertNotEquals(base, PermissionUtil.encodePassword("abcdeX", "ghijkl"));
            assertNotEquals(base, PermissionUtil.encodePassword("abcdef", "ghijklX"));
            assertNotEquals(base, PermissionUtil.encodePassword("abcdef", "ghijkX"));
        }

        @Test
        @DisplayName("拼接歧义（密码与盐边界相同）不产生相同散列")
        void concatenationAmbiguityBroken() {
            // 朴素的 sha1(p+s) 无法区分 ("ab","c") 与 ("a","bc")。
            // 双段拼接正是为了拆掉这个歧义，因此这两组**必须**不同
            assertNotEquals(PermissionUtil.encodePassword("ab", "c"),
                    PermissionUtil.encodePassword("a", "bc"));
        }

        @Test
        @DisplayName("散列是 40 位十六进制的 sha1")
        void shapeIsSha1Hex() {
            assertTrue(PermissionUtil.encodePassword(PASSWORD, SALT).matches("[0-9a-f]{40}"));
        }

        @Test
        @DisplayName("原文不出现在散列里")
        void plaintextNotPresent() {
            assertFalse(PermissionUtil.encodePassword(PASSWORD, SALT).contains(PASSWORD));
        }

        @ParameterizedTest(name = "password=[{index}] {0}")
        @ValueSource(strings = {"", " "})
        @DisplayName("密码为空必须拒绝")
        void emptyPasswordRejected(String password) {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> PermissionUtil.encodePassword(password, SALT));
            assertEquals(cn.hamm.airpower.exception.Errors.PARAM_MISSING.getCode(), exception.getCode());
        }

        @ParameterizedTest(name = "salt=[{index}] {0}")
        @ValueSource(strings = {"", " "})
        @DisplayName("盐为空必须拒绝")
        void emptySaltRejected(String salt) {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> PermissionUtil.encodePassword(PASSWORD, salt));
            assertEquals(cn.hamm.airpower.exception.Errors.PARAM_MISSING.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("密码或盐为 null 必须拒绝而不是抛 NPE")
        void nullRejected() {
            assertThrows(ServiceException.class, () -> PermissionUtil.encodePassword(null, SALT));
            assertThrows(ServiceException.class, () -> PermissionUtil.encodePassword(PASSWORD, null));
        }
    }

    @Nested
    @DisplayName("5. 权限扫描")
    class ScanPermission {

        @Test
        @DisplayName("只扫描标注了 @Api 的控制器")
        void onlyApiControllersScanned() {
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            assertFalse(permissions.isEmpty());
            assertTrue(permissions.stream().allMatch(item -> Objects.nonNull(item.getIdentity())));
        }

        @Test
        @DisplayName("无需登录的控制器不生成权限")
        void openControllerNotScanned() {
            // PublicDemoController 标了 @Permission(login = false)，
            // 运行期不需要登录，权限表里也不该有它
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            assertTrue(permissions.stream()
                    .noneMatch(item -> "PublicDemoController".equals(item.getName())),
                    "免登录控制器不应进入权限表");
        }

        @Test
        @DisplayName("未声明的方法不会因为查表失败而被放进来")
        void unknownMethodNamesAreSkipped() {
            // 字典查不到方法名时旧实现把异常吞掉后<b>没有 continue</b>，
            // 于是自定义方法会混进权限列表；现在统一跳过
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            permissions.forEach(item -> item.getChildren().forEach(child ->
                    assertTrue(child.getIdentity().endsWith(":add")
                                    || child.getIdentity().endsWith(":delete")
                                    || child.getIdentity().endsWith(":disable")
                                    || child.getIdentity().endsWith(":enable")
                                    || child.getIdentity().endsWith(":getDetail")
                                    || child.getIdentity().endsWith(":getList")
                                    || child.getIdentity().endsWith(":getPage")
                                    || child.getIdentity().endsWith(":update")
                                    || child.getIdentity().endsWith(":export")
                                    || child.getIdentity().endsWith(":queryExport"),
                            "出现了不在 Curd 枚举里的方法权限: " + child.getIdentity())));
        }

        @Test
        @DisplayName("子权限标识以父标识打头")
        void childIdentityPrefixedByParent() {
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            permissions.forEach(item -> item.getChildren().forEach(child ->
                    assertTrue(child.getIdentity().startsWith(item.getIdentity() + ":"),
                            "子标识未挂在父标识下: " + child.getIdentity())));
        }

        @Test
        @DisplayName("扫描不存在的包返回空列表而不是抛异常")
        void unknownPackageReturnsEmpty() {
            assertTrue(PermissionUtil.scanPermission("com.not.exists.package", TestPermission.class).isEmpty());
        }

        @Test
        @DisplayName("未标注 @Extends 的控制器不生成任何子权限")
        void undeclaredControllerHasNoChildren() {
            // 「默认拒绝」在权限侧同样成立：没声明过的接口不出现在权限表里，
            // 前端也就不会渲染出点不动的按钮
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            assertTrue(permissions.stream()
                    .noneMatch(item -> "UndeclaredController".equals(item.getName())),
                    "未标注 @Extends 的控制器不应被扫出: " + names(permissions));
        }

        /**
         * 取出权限名列表，便于失败时定位
         *
         * @param permissions 权限列表
         * @return 名称列表
         */
        private List<String> names(List<TestPermission> permissions) {
            return permissions.stream().map(TestPermission::getName).toList();
        }

        @Test
        @DisplayName("收窄后的控制器只生成被声明的那部分权限")
        void narrowedControllerOnlyDeclaresItsOwn() {
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            List<TestPermission> narrowed = permissions.stream()
                    .filter(item -> "NarrowChildController".equals(item.getName()))
                    .toList();
            assertEquals(1, narrowed.size(), "应只扫出收窄控制器: " + names(permissions));
            // NarrowChildController 只声明了 Add，因此只应有 Add 的权限，
            // 且类标识里保留 "Narrow" 前缀
            assertTrue(narrowed.get(0).getChildren().stream()
                    .allMatch(child -> child.getIdentity().endsWith(":add")),
                    "收窄控制器出现了未声明的权限: " + narrowed.get(0).getChildren());
        }

        @Test
        @DisplayName("声明全部接口的控制器生成全部可授权权限")
        void fullyDeclaredControllerGetsAllPermissions() {
            List<TestPermission> permissions = PermissionUtil.scanPermission(
                    "cn.hamm.airpower.api.fixture", TestPermission.class);
            List<TestPermission> demo = permissions.stream()
                    .filter(item -> "DemoController".equals(item.getName()))
                    .toList();
            assertEquals(1, demo.size(), names(permissions).toString());
            // 比 Curd.values().length 少 1：queryExport 上标了 @Permission(authorize = false)，
            // 「无需授权」的方法按设计不生成权限（导出任务的文件编码本身就是访问凭证）。
            // 少这一条是正确的，不是漏扫
            assertEquals(Curd.values().length - 1, demo.get(0).getChildren().size(),
                    "声明了全部接口就应生成全部「可授权」权限");
            assertTrue(demo.get(0).getChildren().stream()
                    .noneMatch(child -> child.getIdentity().endsWith(":queryExport")),
                    "无需授权的方法不应生成权限");
        }
    }

    // ==================== 测试用类型 ====================

    /**
     * 未标记 {@link Permission} 的控制器
     */
    public static class PlainController {
        /**
         * 内部方法，无映射注解
         *
         * @return 结果
         */
        public String plain() {
            return "plain";
        }
    }

    /**
     * 类级免登录的控制器
     */
    @Permission(login = false, authorize = true)
    @Description("公开控制器")
    public static class PublicController {

        /**
         * 类级免登录 + 方法级不授权，两处都没写 login=false
         *
         * @return 结果
         */
        public String plainEndpoint() {
            return "";
        }

        /**
         * 只在方法上写 login=false
         *
         * @return 结果
         */
        @Permission(login = false)
        public String noLoginOnly() {
            return "";
        }

        /**
         * 只在方法上写 authorize=false
         *
         * @return 结果
         */
        @Permission(authorize = false)
        public String loginButNoAuthorize() {
            return "";
        }

        /**
         * 方法级不授权且不登录
         *
         * @return 结果
         */
        @Permission(login = false, authorize = false)
        public String noLoginNoAuthorize() {
            return "";
        }

        /**
         * POST 映射
         *
         * @return 结果
         */
        @PostMapping("p")
        public String mappedPost() {
            return "";
        }

        /**
         * GET 映射
         *
         * @return 结果
         */
        @GetMapping("g")
        public String mappedGet() {
            return "";
        }

        /**
         * 任意方法映射
         *
         * @return 结果
         */
        @RequestMapping("r")
        public String mappedAny() {
            return "";
        }
    }

    /**
     * 类级需登录需授权，方法上放开为不授权
     */
    @Permission
    public static class LockedController {

        /**
         * 放开的接口
         *
         * @return 结果
         */
        @Permission(authorize = false)
        public String openUp() {
            return "";
        }
    }

    /**
     * 内部类控制器
     */
    @Permission(login = false, authorize = false)
    public static class NestedController {

        /**
         * 接口
         *
         * @return 结果
         */
        @PostMapping("n")
        public String nested() {
            return "";
        }
    }

    /**
     * 扫描权限的测试实体
     */
    @Getter
    @Setter
    public static class TestPermission implements IPermission<TestPermission> {
        /**
         * 主键
         */
        private Long id;
        /**
         * 父级 ID
         */
        private Long parentId;
        /**
         * 名称
         */
        private String name;
        /**
         * 标识
         */
        private String identity;
        /**
         * 子权限
         */
        private List<TestPermission> children;

        @Override
        public TestPermission setId(Long id) {
            this.id = id;
            return this;
        }

        @Override
        public TestPermission setParentId(Long parentId) {
            this.parentId = parentId;
            return this;
        }

        @Override
        public TestPermission setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public TestPermission setIdentity(String identity) {
            this.identity = identity;
            return this;
        }

        @Override
        public TestPermission setChildren(List<TestPermission> children) {
            this.children = children;
            return this;
        }
    }
}
