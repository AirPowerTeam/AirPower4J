package cn.hamm.airpower.curd.model.query;

import cn.hamm.airpower.core.constant.Constant;
import cn.hamm.airpower.curd.base.CurdEntity;
import cn.hamm.airpower.curd.config.CurdConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>排序构造单元测试</h1>
 *
 * <p>{@code org.springframework.data.domain.Sort} 是不可变对象，
 * {@code and()} 返回新实例而不修改接收者。因此排序链必须接收 {@code and()} 的返回值，
 * 否则 javadoc 承诺的「末尾追加唯一列」根本不会出现在 SQL 的 ORDER BY 里，
 * 非唯一列排序时翻页会重复出现或漏掉同一批记录。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("排序构造单元测试")
class QueryHelperSortTest {

    /**
     * 非唯一列，1000 条数据里可以大量重复
     */
    private static final String NON_UNIQUE_FIELD = "code";

    /**
     * 待测的查询帮助类
     */
    private final QueryHelper queryHelper = newQueryHelper();

    /**
     * 构造带默认配置的查询帮助类
     *
     * @return 查询帮助类
     */
    private static QueryHelper newQueryHelper() {
        QueryHelper helper = new QueryHelper();
        try {
            Field field = QueryHelper.class.getDeclaredField("curdConfig");
            field.setAccessible(true);
            field.set(helper, new CurdConfig());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 curdConfig 字段失败", e);
        }
        return helper;
    }

    /**
     * 取出排序列表
     *
     * @param sort {@code Spring} 的排序对象
     * @return 排序列表
     * @apiNote {@code Sort} 实现的是 {@code Streamable<Order>}，没有 getOrderList
     */
    private static java.util.List<org.springframework.data.domain.Sort.Order> orders(
            org.springframework.data.domain.Sort sort) {
        return sort.stream().toList();
    }

    /**
     * 构造排序参数
     *
     * @param field     排序字段
     * @param direction 排序方向
     * @return 排序参数
     */
    private Sort sort(String field, String direction) {
        return new Sort().setField(field).setDirection(direction);
    }

    @Nested
    @DisplayName("非唯一列排序必须追加唯一列")
    class UniqueColumnTest {

        @Test
        @DisplayName("升序时 ORDER BY 应为「用户列, 创建时间, 主键」")
        void ascAppendsTiebreakers() {
            org.springframework.data.domain.Sort result =
                    queryHelper.createSort(sort(NON_UNIQUE_FIELD, Sort.ASC));
            assertEquals(3, orders(result).size(),
                    "只有一列时同值记录的相对顺序由数据库自行决定，翻页必然重复或漏行。实际为：" + result);
            assertEquals(NON_UNIQUE_FIELD, orders(result).get(0).getProperty());
            assertEquals(org.springframework.data.domain.Sort.Direction.ASC, orders(result).get(0).getDirection());
            assertEquals(CurdEntity.STRING_CREATE_TIME, orders(result).get(1).getProperty());
            assertEquals(Constant.ID, orders(result).get(2).getProperty(),
                    "主键必须排在最后兜底，它是唯一的");
        }

        @Test
        @DisplayName("降序时同样追加唯一列")
        void descAppendsTiebreakers() {
            org.springframework.data.domain.Sort result =
                    queryHelper.createSort(sort(NON_UNIQUE_FIELD, Sort.DESC));
            assertEquals(3, orders(result).size(), "实际为：" + result);
            assertEquals(org.springframework.data.domain.Sort.Direction.DESC, orders(result).get(0).getDirection());
            assertEquals(Constant.ID, orders(result).get(2).getProperty());
        }

        @Test
        @DisplayName("末列必须是主键，才能保证 ORDER BY 全序")
        void lastOrderIsPrimaryKey() {
            org.springframework.data.domain.Sort result =
                    queryHelper.createSort(sort(NON_UNIQUE_FIELD, Sort.ASC));
            assertEquals(Constant.ID, orders(result)
                    .get(orders(result).size() - 1).getProperty());
        }
    }

    @Nested
    @DisplayName("无需追加的场景")
    class NoAppendTest {

        @Test
        @DisplayName("按主键排序时只保留一列")
        void primaryKeyOnly() {
            org.springframework.data.domain.Sort result =
                    queryHelper.createSort(sort(Constant.ID, Sort.DESC));
            assertEquals(1, orders(result).size(),
                    "主键本身唯一，再追加就是冗余排序。实际为：" + result);
            assertEquals(Constant.ID, orders(result).get(0).getProperty());
        }

        @Test
        @DisplayName("按创建时间排序时只追加主键，不重复追加创建时间")
        void createTimeAppendsOnlyPrimaryKey() {
            org.springframework.data.domain.Sort result =
                    queryHelper.createSort(sort(CurdEntity.STRING_CREATE_TIME, Sort.ASC));
            assertEquals(2, orders(result).size(), "实际为：" + result);
            assertEquals(CurdEntity.STRING_CREATE_TIME, orders(result).get(0).getProperty());
            assertEquals(Constant.ID, orders(result).get(1).getProperty());
        }

        @Test
        @DisplayName("默认排序字段是主键时只保留一列")
        void defaultSortField() {
            org.springframework.data.domain.Sort result = queryHelper.createSort(null);
            assertEquals(1, orders(result).size(),
                    "默认排序字段就是主键，ORDER BY 只有一列。实际为：" + result);
            assertTrue(orders(result).get(0).getProperty().equals(Constant.ID));
        }
    }

    @Nested
    @DisplayName("不可变语义")
    class ImmutabilityTest {

        @Test
        @DisplayName("and() 返回新实例，原 Sort 不受影响")
        void andReturnsNewInstance() {
            org.springframework.data.domain.Sort base =
                    org.springframework.data.domain.Sort.by(NON_UNIQUE_FIELD);
            org.springframework.data.domain.Sort appended =
                    base.and(org.springframework.data.domain.Sort.by(Constant.ID));
            assertEquals(1, orders(base).size(),
                    "这条是前提：如果 and() 会就地修改，下面的 append 断言就不成立");
            assertEquals(2, orders(appended).size());
            assertFalse(orders(base).equals(orders(appended)));
        }

        @Test
        @DisplayName("连续追加应逐层累积而不是只保留最后一项")
        void chainedAppendAccumulates() {
            org.springframework.data.domain.Sort result = queryHelper.createSort(
                    sort(NON_UNIQUE_FIELD, Sort.ASC));
            String joined = String.join(",", orders(result).stream()
                    .map(order -> order.getProperty() + " " + order.getDirection())
                    .toList());
            assertTrue(joined.contains(NON_UNIQUE_FIELD)
                            && joined.contains(CurdEntity.STRING_CREATE_TIME)
                            && joined.contains(Constant.ID),
                    "三个列名必须都在，只出现最后一个说明 and() 返回值被丢弃了。实际为：" + joined);
        }
    }
}
