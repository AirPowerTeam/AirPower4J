package cn.hamm.airpower.curd.permission;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.ReflectUtil;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.model.Access;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.exception.Errors.PARAM_MISSING;
import static org.springframework.core.io.support.ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX;

/**
 * <h1>权限处理工具类</h1>
 *
 * @author Hamm.cn
 * @apiNote 权限标识由类的全限定名派生，是「类」与「方法」的全局唯一真源：
 * 改名或移动类都会让已入库的权限标识失效，需要重新同步权限
 */
@Slf4j
public class PermissionUtil {
    /**
     * {@code Controller}
     */
    private static final String CONTROLLER = "Controller";

    /**
     * 基础包名
     */
    private static String basePackageName = "";

    /**
     * 是否使用包名作为权限标识的前缀
     */
    private static Boolean permissionWithPackage = true;

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private PermissionUtil() {

    }

    /**
     * 设置基础包名
     *
     * @param basePackageName 基础包名
     */
    public static void setBasePackageName(String basePackageName) {
        PermissionUtil.basePackageName = basePackageName;
    }

    /**
     * 设置是否使用包名作为权限标识的前缀
     *
     * @param permissionWithPackage 是否使用包名作为权限标识的前缀
     */
    public static void setPermissionWithPackage(Boolean permissionWithPackage) {
        PermissionUtil.permissionWithPackage = permissionWithPackage;
    }

    /**
     * 获取需要被授权的类型
     *
     * @param clazz  类
     * @param method 方法
     * @return 需要授权的选项
     * @apiNote 方法上的 {@link Permission} 整体覆盖类上的，而不是逐属性合并，
     * 覆写时两个属性都要写全
     */
    public static @NotNull Access getWhatNeedAccess(@NotNull Class<?> clazz, @NotNull Method method) {
        // 未标记时默认既需要登录也需要授权
        Access access = new Access();
        Permission permissionClass = clazz.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionClass)) {
            access.setLogin(permissionClass.login());
            // 需要登录时 RBAC 选项才能启用
            access.setAuthorize(permissionClass.login() && permissionClass.authorize());
        }
        Permission permissionMethod = method.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionMethod)) {
            access.setLogin(permissionMethod.login());
            access.setAuthorize(permissionMethod.login() && permissionMethod.authorize());
        }
        return access;
    }

    /**
     * 获取权限标识
     *
     * @param clazz  类
     * @param method 方法
     * @return 权限标识
     */
    public static @NotNull String getPermissionIdentity(@NotNull Class<?> clazz, @NotNull Method method) {
        return baseIdentity(clazz) + ":" + method.getName();
    }

    /**
     * 获取基础权限标识
     *
     * @param clazz 类
     * @return 权限标识
     * @apiNote 内部类以 {@code $} 分隔，转成 {@code .} 以便与包名路径一致；
     * 开头的 {@code permissionWithPackage} 保证多模块下同名控制器不会撞标识
     */
    private static @NotNull String baseIdentity(@NotNull Class<?> clazz) {
        if (permissionWithPackage) {
            return StringUtils.uncapitalize(clazz.getName().replace('$', '.').replace(basePackageName + ".", "").replace(CONTROLLER, ""));
        } else {
            return StringUtils.uncapitalize(clazz.getSimpleName().replace(CONTROLLER, ""));
        }
    }

    /**
     * 扫描并返回权限列表
     *
     * @param clazz           入口类
     * @param permissionClass 权限类
     * @param <P>             权限类型
     * @return 权限列表
     * @apiNote 以入口类所在包为起点扫描 {@code *Controller.class}，并以异常被吞掉的方式
     * 返回已有结果，因此权限数量对不上时先查「扫描权限出错」日志
     */
    public static <P extends IPermission<P>> @NotNull List<P> scanPermission(
            @NotNull Class<?> clazz, Class<P> permissionClass
    ) {
        return scanPermission(clazz.getPackageName(), permissionClass);
    }

    /**
     * 扫描并返回权限列表
     *
     * @param packageName     包名
     * @param permissionClass 权限类
     * @param <P>             权限类型
     * @return 权限列表
     */
    public static <P extends IPermission<P>> @NotNull List<P> scanPermission(
            String packageName, Class<P> permissionClass
    ) {
        List<P> permissions = new ArrayList<>();
        try {
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            String pattern = CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(packageName) + "/**/*" + CONTROLLER + ".class";
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

            for (Resource resource : resources) {
                // 用于读取类信息
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                // 扫描到的类
                String className = metadataReader.getClassMetadata().getClassName();
                Class<?> clazz = Class.forName(className);

                Api api = clazz.getAnnotation(Api.class);
                if (Objects.isNull(api)) {
                    // 不是 Rest 控制器或者是指定的几个白名单控制器
                    continue;
                }

                String customClassName = ReflectUtil.getDescription(clazz);
                String identity = baseIdentity(clazz);
                P permission = permissionClass.getConstructor().newInstance();

                permission.setName(customClassName).setIdentity(identity).setChildren(new ArrayList<>());

                String apiPath = identity + ":";

                // 取出所有控制器方法
                Method[] methods = clazz.getMethods();

                // 取出控制器类上的Extends注解 如自己没标 则使用父类的
                List<Curd> curdList = Curd.getCurdList(clazz);
                for (Method method : methods) {
                    try {
                        Curd current = DictionaryUtil.getDictionary(Curd.class, Curd::getMethodName, method.getName());
                        if (!curdList.contains(current)) {
                            continue;
                        }
                    } catch (Exception ignored) {
                    }
                    String subIdentity = getMethodPermissionIdentity(method);
                    if (Objects.isNull(subIdentity)) {
                        continue;
                    }
                    subIdentity = apiPath + subIdentity;
                    String customMethodName = ReflectUtil.getDescription(method);
                    Access accessConfig = getWhatNeedAccess(clazz, method);
                    if (!accessConfig.isLogin()) {
                        // 无需登录 不扫描权限
                        continue;
                    }
                    if (!accessConfig.isAuthorize()) {
                        // 无需授权 不扫描权限
                        continue;
                    }
                    P subPermission = permissionClass.getConstructor().newInstance();
                    subPermission.setIdentity(subIdentity).setName(customClassName + "-" + customMethodName);
                    permission.getChildren().add(subPermission);
                }
                permissions.add(permission);
            }
        } catch (Exception exception) {
            log.error("扫描权限出错", exception);
        }
        return permissions;
    }

    /**
     * 获取方法权限标识
     *
     * @param method 方法
     * @return 权限标识
     * @apiNote 非 {@code @RequestMapping} 系列映射的方法视为内部方法，不生成权限
     */
    private static @Nullable String getMethodPermissionIdentity(Method method) {
        RequestMapping requestMapping = ReflectUtil.getAnnotation(RequestMapping.class, method);
        PostMapping postMapping = ReflectUtil.getAnnotation(PostMapping.class, method);
        GetMapping getMapping = ReflectUtil.getAnnotation(GetMapping.class, method);

        if (Objects.isNull(requestMapping) && Objects.isNull(postMapping) && Objects.isNull(getMapping)) {
            return null;
        }
        return method.getName();
    }

    /**
     * 密码和盐获取密码的散列摘要
     *
     * @param password 明文密码
     * @param salt     盐
     * @return {@code sha1} 散列摘要
     * @apiNote 对「密码+盐」和「盐+密码」分别摘要后再拼起来摘要，消除密码与盐交换位置的歧义
     */
    public static @NotNull String encodePassword(@NotNull String password, @NotNull String salt) {
        PARAM_MISSING.whenEmpty(password, "密码不能为空");
        PARAM_MISSING.whenEmpty(salt, "盐不能为空");
        return DigestUtils.sha1Hex(
                DigestUtils.sha1Hex(password + salt) + DigestUtils.sha1Hex(salt + password)
        );
    }
}
