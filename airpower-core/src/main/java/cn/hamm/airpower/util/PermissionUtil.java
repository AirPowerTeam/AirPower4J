package cn.hamm.airpower.util;

import cn.hamm.airpower.annotation.ApiController;
import cn.hamm.airpower.annotation.Extends;
import cn.hamm.airpower.annotation.Permission;
import cn.hamm.airpower.enums.Api;
import cn.hamm.airpower.interfaces.IPermission;
import cn.hamm.airpower.model.Access;
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
import java.util.Arrays;
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
        //默认无标记时，不需要登录和授权
        Access access = new Access();

        //判断类是否标记访问权限
        Permission permissionClass = clazz.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionClass)) {
            //类有AccessRequire标记
            access.setLogin(permissionClass.login());
            //需要登录时 RBAC选项才能启用
            access.setAuthorize(permissionClass.login() && permissionClass.authorize());
        }
        //如果方法也标注了 方法将覆盖类的注解配置
        Permission permissionMethod = method.getAnnotation(Permission.class);
        if (Objects.nonNull(permissionMethod)) {
            //方法有AccessRequire标记
            access.setLogin(permissionMethod.login());
            //需要登录时 RBAC选项才能启用
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

                ApiController apiController = clazz.getAnnotation(ApiController.class);
                if (Objects.isNull(apiController)) {
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
                Extends extendsApi = ReflectUtil.getAnnotation(Extends.class, clazz);
                for (Method method : methods) {
                    if (Objects.nonNull(extendsApi)) {
                        try {
                            Api current = DictionaryUtil.getDictionary(Api.class, Api::getMethodName, method.getName());
                            if (checkApiExcluded(current, extendsApi)) {
                                continue;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    String subIdentity = getMethodPermissionIdentity(method);
                    if (Objects.isNull(subIdentity)) {
                        continue;
                    }
                    subIdentity = apiPath + subIdentity;
                    String customMethodName = ReflectUtil.getDescription(method);
                    Access accessConfig = getWhatNeedAccess(clazz, method);
                    if (!accessConfig.isLogin() || !accessConfig.isAuthorize()) {
                        // 这里可以选择是否不读取这些接口的权限，但前端可能需要
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
     * 检查Api是否在子控制器中被排除
     *
     * @param api    Api
     * @param extend 注解
     * @return 检查结果
     */
    private static boolean checkApiExcluded(Api api, @NotNull Extends extend) {
        List<Api> excludeList = Arrays.asList(extend.exclude());
        if (excludeList.contains(api)) {
            return true;
        }
        List<Api> includeList = Arrays.asList(extend.value());
        if (includeList.isEmpty()) {
            return false;
        }
        return !includeList.contains(api);
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
