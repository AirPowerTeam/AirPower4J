package cn.hamm.airpower.interfaces;

import cn.hamm.airpower.util.ReflectUtil;

/**
 * <h1>枚举字典标准接口</h1>
 *
 * @author Hamm.cn
 * @see ReflectUtil
 */
public interface IDictionary {
    /**
     * 获取枚举的 Key
     *
     * @return Key
     */
    int getKey();

    /**
     * 获取枚举的描述
     *
     * @return 描述
     */
    String getLabel();

    /**
     * 判断 Key 是否相等
     *
     * @param key 被判断的 Key
     * @return 对比结果
     */
    default boolean equalsKey(int key) {
        return getKey() == key;
    }

    /**
     * 判断 Key 是否不相等
     *
     * @param key 被判断的 Key
     * @return 对比结果
     */
    default boolean notEqualsKey(int key) {
        return !equalsKey(key);
    }
}
