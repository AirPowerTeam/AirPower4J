package cn.hamm.airpower.util;

import cn.hamm.airpower.annotation.Description;
import cn.hamm.airpower.exception.ServiceException;
import cn.hamm.airpower.interfaces.IDictionary;
import cn.hamm.airpower.interfaces.IFunction;
import cn.hamm.airpower.root.RootController;
import cn.hamm.airpower.root.RootEntity;
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

import static cn.hamm.airpower.config.Constant.*;

/**
 * <h1>反射工具类</h1>
 *
 * @author Hamm.cn
 * @see IDictionary
 */
@Slf4j
public class ReflectUtil {
    /**
     * <h3>反射操作属性失败</h3>
     */
    private static final String REFLECT_EXCEPTION = "反射操作属性失败";

    /**
     * <h3>缓存字段列表</h3>
     */
    private final static ConcurrentHashMap<Class<?>, List<Field>> FIELD_LIST_MAP = new ConcurrentHashMap<>();

    /**
     * <h3>缓存属性列表</h3>
     *
     * @apiNote 声明属性列表
     */
    private final static ConcurrentHashMap<String, Field[]> DECLARED_FIELD_LIST_MAP = new ConcurrentHashMap<>();

    /**
     * <h3>获取对象指定属性的值</h3>
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
     * <h3>设置对象指定属性的值</h3>
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
     * <h3>获取对象实例</h3>
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
     * <h3>清空对象指定属性的值</h3>
     *
     * @param object 对象
     * @param field  属性
     */
    public static void clearFieldValue(Object object, Field field) {
        setFieldValue(object, field, null);
    }

    /**
     * <h3>判断是否是根类</h3>
     *
     * @param clazz 类
     * @return 判断结果
     */
    public static boolean isTheRootClass(@NotNull Class<?> clazz) {
        return Objects.equals(clazz.getName(), RootController.class.getName()) ||
                Objects.equals(clazz.getName(), RootEntity.class.getName()) ||
                Objects.equals(clazz.getName(), Object.class.getName());
    }

    /**
     * <h3>递归获取指定方法的注解</h3>
     *
     * @param annotationClass 注解类
     * @param method          方法
     * @param <A>             泛型
     * @return 注解
     */
    public static <A extends Annotation> @Nullable A getAnnotation(Class<A> annotationClass, Method method) {
        return getAnnotation(annotationClass, method, method.getDeclaringClass());
    }

    /**
     * <h3>递归获取指定类的注解</h3>
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
     * <h3>递归获取字段的注解</h3>
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
     * <h3>递归获取类描述</h3>
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
     * <h3>递归获取方法描述</h3>
     *
     * @param method 方法
     * @return 描述
     * @see Description
     */
    public static String getDescription(Method method) {
        Description description = getAnnotation(Description.class, method, method.getDeclaringClass());
        return Objects.isNull(description) ? method.getName() : description.value();
    }

    /**
     * <h3>递归获取字段描述</h3>
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
     * <h3>获取参数描述</h3>
     *
     * @param parameter 参数
     * @return 描述
     */
    public static String getDescription(@NotNull Parameter parameter) {
        Description description = parameter.getAnnotation(Description.class);
        return Objects.isNull(description) ? parameter.getName() : description.value();
    }

    /**
     * <h3>是否是继承自 {@code RootEntity}</h3>
     *
     * @param clazz 类
     * @return 布尔
     */
    public static boolean isEntity(Class<?> clazz) {
        if (Objects.isNull(clazz)) {
            return false;
        }
        if (clazz.getName().equalsIgnoreCase(RootEntity.class.getName())) {
            return true;
        }
        return isEntity(clazz.getSuperclass());
    }

    /**
     * <h3>是否是继承自 {@code RootModel}</h3>
     *
     * @param clazz 类
     * @return 布尔
     */
    public static boolean isModel(Class<?> clazz) {
        if (Objects.isNull(clazz)) {
            return false;
        }
        if (clazz.getName().equalsIgnoreCase(RootModel.class.getName())) {
            return true;
        }
        return isModel(clazz.getSuperclass());
    }

    /**
     * <h3>获取指定类的字段列表</h3>
     *
     * @param clazz 类
     * @return 字段数组
     */
    public static @NotNull List<Field> getFieldList(Class<?> clazz) {
        return FIELD_LIST_MAP.computeIfAbsent(clazz, ReflectUtil::getCacheFieldList);
    }

    /**
     * <h3>获取指定类的字段列表</h3>
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
     * <h3>获取类的所有属性</h3>
     *
     * @param clazz 类
     * @return 属性数组
     */
    @Contract(pure = true)
    public static Field @NotNull [] getDeclaredFields(@NotNull Class<?> clazz) {
        return DECLARED_FIELD_LIST_MAP.computeIfAbsent(clazz.getName(), key -> clazz.getDeclaredFields());
    }

    /**
     * <h3>获取类的所有公开属性名称列表</h3>
     *
     * @param clazz 类
     * @return 属性名数组
     */
    public static @NotNull List<String> getFieldNameList(@NotNull Class<?> clazz) {
        Field[] fields = getDeclaredFields(clazz);
        return Arrays.stream(fields)
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    /**
     * <h3>获取 {@code Lambda} 的 {@code Function} 表达式的函数名</h3>
     *
     * @param lambda 表达式
     * @return 函数名
     */
    public static @NotNull String getLambdaFunctionName(@NotNull IFunction<?, ?> lambda) {
        return getSerializedLambda(lambda)
                .getImplMethodName()
                .replace(STRING_GET, STRING_EMPTY);
    }

    /**
     * <h3>获取 {@code Lambda} 的 {@code Function} 类的函数名</h3>
     *
     * @param lambda 表达式
     * @return 类名
     */
    public static @NotNull String getLambdaClassName(@NotNull IFunction<?, ?> lambda) {
        return getSerializedLambda(lambda)
                .getImplClass()
                .replaceAll(STRING_SLASH, STRING_DOT);
    }

    /**
     * <h3>获取一个 {@code SerializedLambda}</h3>
     *
     * @param lambda 表达式
     * @return {@code SerializedLambda}
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
     * <h3>递归获取方法的注解</h3>
     *
     * @param <A>             注解泛型
     * @param annotationClass 注解类
     * @param method          方法
     * @param currentClass    所在类
     * @return 装配的注解
     */
    private static <A extends Annotation> @Nullable A getAnnotation(
            Class<A> annotationClass, @NotNull Method method, Class<?> currentClass
    ) {
        A annotation = method.getAnnotation(annotationClass);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }
        if (isTheRootClass(currentClass)) {
            return null;
        }
        Class<?> superClass = currentClass.getSuperclass();
        if (Objects.isNull(superClass)) {
            return null;
        }
        List<Method> superMethods = Arrays.stream(superClass.getMethods()).toList();
        Method superMethod = superMethods.stream()
                .filter(m -> Objects.equals(m.getName(), method.getName()))
                .findFirst()
                .orElse(null);
        if (Objects.isNull(superMethod)) {
            return null;
        }
        return getAnnotation(annotationClass, superMethod, superClass);
    }

    /**
     * <h3>递归获取字段</h3>
     *
     * @param fieldName 字段名
     * @param clazz     当前类
     * @return 字段
     */
    public static @Nullable Field getField(String fieldName, Class<?> clazz) {
        if (Objects.isNull(clazz) || Objects.equals(Object.class, clazz)) {
            return null;
        }
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return getField(fieldName, clazz.getSuperclass());
        }
    }
}
