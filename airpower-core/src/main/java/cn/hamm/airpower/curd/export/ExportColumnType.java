package cn.hamm.airpower.curd.export;

import cn.hamm.airpower.dictionary.Dictionary;

/**
 * <h1>列数据类型</h1>
 *
 * @author Hamm.cn
 */
public enum ExportColumnType {
    /**
     * 普通文本
     */
    TEXT,

    /**
     * 时间日期
     */
    DATETIME,

    /**
     * 数字
     */
    NUMBER,

    /**
     * 字典
     *
     * @apiNote 请确保同时标记了 {@link Dictionary}
     */
    DICTIONARY,

    /**
     * 布尔值
     */
    BOOLEAN
}
