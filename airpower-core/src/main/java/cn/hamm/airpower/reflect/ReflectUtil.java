package cn.hamm.airpower.reflect;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.api.ApiController;
import cn.hamm.airpower.curd.CurdEntity;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.root.RootModel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * <h1>反射工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class ReflectUtil {
    /**
     * {@code get}
     */
    private static final String GET = "get";

    /**
     * 反射操作属性失败
     */
    private static final String REFLECT_EXCEPTION = "反射操作属性失败";

    /**
     * 缓存字段列表
     */
    private final static ConcurrentHashMap<Class<?>, List<Field>> FIELD_LIST_MAP = new ConcurrentHashMap<>();

    /**
     * 缓存属性列表
     *
     * @apiNote 声明属性列表
     */
    private final static ConcurrentHashMap<String, Field[]> DECLARED_FIELD_LIST_MAP = new ConcurrentHashMap<>();

    /**
     * 获取字段的 Getter 方法名
     *
     * @param field 字段
     * @return Getter 方法名
     */
    public static @NotNull String getFieldGetter(@NotNull Field field) {
        final String fieldName = field.getName();
        return GET + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    /**
     * 获取对象指定属性的值
     *
     * @param object 对象
     * @param field  属性
     * @return 值
     */
    public static @Nullable Object getFieldValue(Object object, @NotNull Field field) {
        try {
            field.setAccessible(true);
            return field.get(object);
        } catch (IllegalAccessException exception) {
            log.error(REFLECT_EXCEPTION, exception);
            return null;
        } finally {
            field.setAccessible(false);
        }
    }

    /**
     * 设置对象指定属性的值
     *
     * @param object 对象
     * @param field  属性
     * @param value  值
     */
    public static void setFieldValue(Object object, @NotNull Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(object, value);
        } catch (IllegalAccessException exception) {
            log.error(REFLECT_EXCEPTION, exception);
        } finally {
            field.setAccessible(false);
        }
    }

    /**
     * 获取对象实例
     *
     * @param clazz 类
     * @param <T>   对象类型
     * @return 对象实例
     */
    public static <T extends RootModel<T>> @NotNull T newInstance(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (java.lang.Exception exception) {
            throw new ServiceException(exception.getMessage());
        }
    }

    /**
     * 清空对象指定属性的值
     *
     * @param object 对象
     * @param field  属性
     */
    public static void clearFieldValue(Object object, Field field) {
        setFieldValue(object, field, null);
    }

    /**
     * 判断是否是根类
     *
     * @param clazz 类
     * @return 判断结果
     */
    public static boolean isTheRootClass(@NotNull Class<?> clazz) {
        return clazz.equals(ApiController.class) || clazz.equals(CurdEntity.class) || clazz.equals(Object.class);
    }

    /**
     * 递归获取指定方法的注解
     *
     * @param annotationClass 注解类
     * @param method          方法
     * @param <A>             泛型
     * @return 注解
     */
    public static <A extends Annotation> @Nullable A getAnnotation(Class<A> annotationClass, @NotNull Method method) {
        return getAnnotation(annotationClass, method.getDeclaringClass(), method.getName(), method.getParameterTypes());
    }

    /**
     * 递归获取指定类的注解
     *
     * @param annotationClass 注解类
     * @param clazz           类
     * @param <A>             泛型
     * @return 注解
     */
    public static <A extends Annotation> @Nullable A getAnnotation(Class<A> annotationClass, @NotNull Class<?> clazz) {
        A annotation = clazz.getAnnotation(annotationClass);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }
        if (isTheRootClass(clazz)) {
            return null;
        }
        Class<?> superClass = clazz.getSuperclass();
        return getAnnotation(annotationClass, superClass);
    }

    /**
     * 递归获取字段的注解
     *
     * @param annotationClass 注解类
     * @param field           字段
     * @param <A>             泛型
     * @return 注解
     */
    @Contract(pure = true)
    public static <A extends Annotation> @Nullable A getAnnotation(Class<A> annotationClass, @NotNull Field field) {
        return field.getAnnotation(annotationClass);
    }

    /**
     * 递归获取类描述
     *
     * @param clazz 类
     * @return 描述
     * @see Description
     */
    public static String getDescription(Class<?> clazz) {
        Description description = getAnnotation(Description.class, clazz);
        return Objects.isNull(description) ? clazz.getSimpleName() : description.value();
    }

    /**
     * 递归获取方法描述
     *
     * @param method 方法
     * @return 描述
     * @see Description
     */
    public static String getDescription(Method method) {
        Description description = getAnnotation(Description.class, method);
        return Objects.isNull(description) ? method.getName() : description.value();
    }

    /**
     * 递归获取字段描述
     *
     * @param field 字段
     * @return 描述
     * @see Description
     */
    public static String getDescription(Field field) {
        Description description = getAnnotation(Description.class, field);
        return Objects.isNull(description) ? field.getName() : description.value();
    }

    /**
     * 获取参数描述
     *
     * @param parameter 参数
     * @return 描述
     */
    public static String getDescription(@NotNull Parameter parameter) {
        Description description = parameter.getAnnotation(Description.class);
        return Objects.isNull(description) ? parameter.getName() : description.value();
    }

    /**
     * 获取指定类的字段列表
     *
     * @param clazz 类
     * @return 字段数组
     */
    public static @NotNull List<Field> getFieldList(Class<?> clazz) {
        return FIELD_LIST_MAP.computeIfAbsent(clazz, ReflectUtil::getCacheFieldList);
    }

    /**
     * 获取指定类的字段列表
     *
     * @param clazz 类
     * @return 字段数组
     */
    private static @NotNull List<Field> getCacheFieldList(Class<?> clazz) {
        List<Field> fieldList = new LinkedList<>();
        if (Objects.isNull(clazz)) {
            return fieldList;
        }
        Field[] fields = getDeclaredFields(clazz);
        // 过滤静态属性 或 过滤transient 关键字修饰的属性
        fieldList = Arrays.stream(fields)
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers()))
                .collect(Collectors.toCollection(LinkedList::new));
        if (isTheRootClass(clazz)) {
            return fieldList;
        }
        // 处理父类字段
        Class<?> superClass = clazz.getSuperclass();
        fieldList.addAll(getCacheFieldList(superClass));
        return fieldList;
    }

    /**
     * 获取类的所有属性
     *
     * @param clazz 类
     * @return 属性数组
     */
    @Contract(pure = true)
    public static Field @NotNull [] getDeclaredFields(@NotNull Class<?> clazz) {
        return DECLARED_FIELD_LIST_MAP.computeIfAbsent(clazz.getName(), key -> clazz.getDeclaredFields());
    }

    /**
     * 获取 Lambda 的 Function 表达式的函数名
     *
     * @param lambda 表达式
     * @return 函数名
     */
    public static @NotNull String getLambdaFunctionName(@NotNull IFunction<?, ?> lambda) {
        return getSerializedLambda(lambda)
                .getImplMethodName()
                .replace(GET, "");
    }

    /**
     * 获取一个 SerializedLambda
     *
     * @param lambda 表达式
     * @return SerializedLambda
     */
    private static SerializedLambda getSerializedLambda(@NotNull IFunction<?, ?> lambda) {
        try {
            Method replaceMethod = lambda.getClass().getDeclaredMethod("writeReplace");
            replaceMethod.setAccessible(true);
            return (SerializedLambda) replaceMethod.invoke(lambda);
        } catch (Exception exception) {
            throw new ServiceException(exception);
        }
    }

    /**
     * 递归获取方法的注解
     *
     * @param annotationClass 要查找的注解类型
     * @param currentClass    当前类
     * @param methodName      方法名
     * @param paramTypes      方法参数类型数组
     * @return 如果找到注解则返回该注解实例，否则返回 null
     */
    public static <T extends java.lang.annotation.Annotation> @Nullable T getAnnotation(
            Class<T> annotationClass,
            @NotNull Class<?> currentClass,
            String methodName,
            Class<?>[] paramTypes
    ) {
        try {
            // 获取当前类中的方法
            Method method = currentClass.getDeclaredMethod(methodName, paramTypes);
            // 获取注解，避免重复调用 getAnnotation
            T annotation = method.getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        } catch (NoSuchMethodException ignored) {
            // 忽略，继续查找父类或接口
        }

        // 查找父类
        Class<?> superClass = currentClass.getSuperclass();
        if (superClass != null) {
            return getAnnotation(annotationClass, superClass, methodName, paramTypes);
        }
        return null;
    }


    /**
     * 递归获取字段
     *
     * @param fieldName 字段名
     * @param clazz     当前类
     * @return 字段
     */
    public static @Nullable Field getField(String fieldName, Class<?> clazz) {
        if (Objects.isNull(clazz) || Object.class.equals(clazz)) {
            return null;
        }
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return getField(fieldName, clazz.getSuperclass());
        }
    }
}
