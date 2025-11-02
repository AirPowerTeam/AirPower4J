package cn.hamm.airpower.access;

import cn.hamm.airpower.api.Api;
import cn.hamm.airpower.curd.Curd;
import cn.hamm.airpower.dictionary.DictionaryUtil;
import cn.hamm.airpower.reflect.ReflectUtil;
import lombok.extern.slf4j.Slf4j;
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

import static org.springframework.core.io.support.ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX;

/**
 * <h1>权限处理工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class PermissionUtil {
    /**
     * {@code Controller}
     */
    private static final String CONTROLLER = "Controller";

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private PermissionUtil() {

    }

    /**
     * 获取需要被授权的类型
     *
     * @param clazz  类
     * @param method 方法
     * @return 需要授权的选项
     */
    public static @NotNull Access getWhatNeedAccess(@NotNull Class<?> clazz, @NotNull Method method) {
        // 默认无标记时，不需要登录和授权
        Access access = new Access();

        // 判断类是否标记访问权限
        Permission permissionClass = clazz.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionClass)) {
            // 类做了标记 先记下来 后续可能被方法覆盖
            access.setLogin(permissionClass.login());
            // 需要登录时 RBAC选项才能启用
            access.setAuthorize(permissionClass.login() && permissionClass.authorize());
        }
        // 如果方法也标注了 方法将覆盖类的注解配置
        Permission permissionMethod = method.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionMethod)) {
            // 方法标记覆盖类的配置
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
        return StringUtils.uncapitalize(clazz.getSimpleName()
                .replaceAll(CONTROLLER, "")) +
                "_" + method.getName();
    }

    /**
     * 扫描并返回权限列表
     *
     * @param clazz           入口类
     * @param permissionClass 权限类
     * @param <P>             权限类型
     * @return 权限列表
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
                // 扫描到的class
                String className = metadataReader.getClassMetadata().getClassName();
                Class<?> clazz = Class.forName(className);

                Api api = clazz.getAnnotation(Api.class);
                if (Objects.isNull(api)) {
                    // 不是rest控制器或者是指定的几个白名单控制器
                    continue;
                }

                String customClassName = ReflectUtil.getDescription(clazz);
                String identity = clazz.getSimpleName().replaceAll(CONTROLLER, "");
                P permission = permissionClass.getConstructor().newInstance();

                permission.setName(customClassName).setIdentity(identity).setChildren(new ArrayList<>());

                String apiPath = identity + "_";

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
}
