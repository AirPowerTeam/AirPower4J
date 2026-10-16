package cn.hamm.airpower.api.query;

import cn.hamm.airpower.api.base.ApiConfig;
import cn.hamm.airpower.api.curd.CurdEntity;
import cn.hamm.airpower.api.fixture.DemoEntity;
import cn.hamm.airpower.api.query.page.Page;
import cn.hamm.airpower.api.query.sort.Sort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Order;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static cn.hamm.airpower.api.curd.CurdEntity.STRING_CREATE_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>查询帮助类单元测试</h1>
 *
 * <p>三个关注点：</p>
 * <ul>
 *     <li><b>分页边界</b>：对外页码 1 起、内部 0 起；每页条数必须被夹在配置上下限内。
 *     上限是防「一次拉爆内存」的最后一道闸，一旦失效就是一个 OOM 入口</li>
 *     <li><b>排序稳定性</b>：任何排序都必须以唯一列收尾，否则同值记录翻页时会重复出现或漏掉</li>
 *     <li><b>查询条件构建</b>：哪些字段参与筛选、哪些不参与，以及
 *     {@code LIKE} 通配符是否被当成字面量处理</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("查询帮助类单元测试")
class QueryHelperTest {

    /**
     * 最小分页条数
     */
    private static final int MIN_PAGE_SIZE = 5;

    /**
     * 最大分页条数
     */
    private static final int MAX_PAGE_SIZE = 1000;

    /**
     * 默认分页条数
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private QueryHelper queryHelper;

    /**
     * 每个用例一份干净的配置：{@link ApiConfig} 是全局静态的入口，
     * 用共享实例会让用例之间互相影响
     */
    @BeforeEach
    void setUp() {
        queryHelper = new QueryHelper();
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setMinPageSize(MIN_PAGE_SIZE);
        apiConfig.setMaxPageSize(MAX_PAGE_SIZE);
        apiConfig.setDefaultPageSize(DEFAULT_PAGE_SIZE);
        apiConfig.setDefaultSortField(CurdEntity.STRING_ID);
        inject(apiConfig, "apiConfig");
    }

    /**
     * 反射注入私有字段
     *
     * @param value     目标对象
     * @param fieldName 字段名
     */
    private void inject(Object value, String fieldName) {
        try {
            Field field = QueryHelper.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(queryHelper, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入字段失败: " + fieldName, e);
        }
    }

    @Nested
    @DisplayName("1. 分页参数补齐")
    class RequirePageNonNull {

        @Test
        @DisplayName("null 页对象应新建并补上默认值")
        void nullPageGetsDefaults() {
            Page page = queryHelper.requirePageNonNull(null);
            assertEquals(DEFAULT_PAGE_SIZE, page.getPageSize());
            assertEquals(1, page.getPageNum());
        }

        @ParameterizedTest(name = "pageSize={0} -> 默认 {1}")
        @CsvSource({"0", "-1", "-1000"})
        @DisplayName("非正的 pageSize 一律回落到默认值")
        void nonPositivePageSizeFallsBack(int pageSize) {
            // 0 或负数会让 PageRequest.of 抛 IllegalArgumentException，
            // 客户端一个非法参数就打成 500，是很不好的体验
            assertEquals(DEFAULT_PAGE_SIZE,
                    queryHelper.requirePageNonNull(new Page().setPageSize(pageSize)).getPageSize());
        }

        @Test
        @DisplayName("pageSize 为 null 时回落到默认值")
        void nullPageSizeFallsBack() {
            assertEquals(DEFAULT_PAGE_SIZE,
                    queryHelper.requirePageNonNull(new Page().setPageSize(null)).getPageSize());
        }

        @ParameterizedTest(name = "pageNum={0} -> 1")
        @ValueSource(ints = {0, -1, -1000})
        @DisplayName("非正的 pageNum 一律回落到第 1 页")
        void nonPositivePageNumFallsBack(int pageNum) {
            assertEquals(1, queryHelper.requirePageNonNull(new Page().setPageNum(pageNum)).getPageNum());
        }

        @Test
        @DisplayName("pageNum 为 null 时回落到第 1 页")
        void nullPageNumFallsBack() {
            assertEquals(1, queryHelper.requirePageNonNull(new Page().setPageNum(null)).getPageNum());
        }

        @Test
        @DisplayName("合法值原样保留")
        void legalValuePreserved() {
            Page page = queryHelper.requirePageNonNull(new Page().setPageNum(7).setPageSize(30));
            assertEquals(7, page.getPageNum());
            assertEquals(30, page.getPageSize());
        }

        @Test
        @DisplayName("超大 pageSize 在补齐阶段不得被裁剪")
        void hugePageSizeNotClampedHere() {
            // 补齐只负责「非法值」，裁剪在 createPageable 里做。
            // 两处都裁会让「已裁剪」与「刚补齐」两种来源的 Page 无法区分
            assertEquals(999999, queryHelper.requirePageNonNull(new Page().setPageSize(999999)).getPageSize());
        }

        @Test
        @DisplayName("返回的就是传入的那个对象")
        void sameInstanceReturned() {
            // 控制器/服务层会继续在返回对象上设置其他字段，
            // 复制一份会让这些设置丢失
            Page page = new Page();
            assertSame(page, queryHelper.requirePageNonNull(page));
        }
    }

    @Nested
    @DisplayName("2. 排序参数补齐")
    class RequireSortNonNull {

        @Test
        @DisplayName("null 排序对象应新建并补上默认字段")
        void nullSortGetsDefaults() {
            Sort sort = queryHelper.requireSortNonNull(null);
            assertEquals(CurdEntity.STRING_ID, sort.getField());
            assertEquals(Sort.DESC, sort.getDirection());
        }

        @ParameterizedTest(name = "field=[{index}] {0}")
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("空白字段名应回落到默认排序字段")
        void blankFieldFallsBack(String field) {
            assertEquals(CurdEntity.STRING_ID,
                    queryHelper.requireSortNonNull(new Sort().setField(field)).getField());
        }

        @ParameterizedTest(name = "[{index}] {0} 视为升序")
        @ValueSource(strings = {"asc", "ASC", "Asc", "aSc"})
        @DisplayName("只有 asc（忽略大小写）算升序")
        void onlyAscIsAscending(String direction) {
            assertEquals(Sort.ASC,
                    queryHelper.requireSortNonNull(new Sort().setDirection(direction)).getDirection());
        }

        @ParameterizedTest(name = "[{index}] {0} 视为降序")
        @ValueSource(strings = {"desc", "DESC", "ascending", "1", "asc ", "asc;drop", "null", "-"})
        @DisplayName("其余取值一律降序，绝不原样拼进 SQL")
        void everythingElseIsDescending(String direction) {
            // 方向最终会出现在 ORDER BY 里，非法值原样透传就是 SQL 注入面
            assertEquals(Sort.DESC,
                    queryHelper.requireSortNonNull(new Sort().setDirection(direction)).getDirection());
        }

        @Test
        @DisplayName("方向为 null 时按降序处理")
        void nullDirectionBecomesDesc() {
            assertEquals(Sort.DESC,
                    queryHelper.requireSortNonNull(new Sort().setDirection(null)).getDirection());
        }
    }

    @Nested
    @DisplayName("3. Pageable 构建")
    class CreatePageable {

        @Test
        @DisplayName("对外 1 起的页码转成内部 0 起")
        void pageNumIsOneBasedOutside() {
            assertEquals(0, queryHelper.createPageable(new Page().setPageNum(1), null).getPageNumber());
            assertEquals(4, queryHelper.createPageable(new Page().setPageNum(5), null).getPageNumber());
        }

        @Test
        @DisplayName("pageSize 超过上限时被压到上限")
        void pageSizeClampedToMax() {
            assertEquals(MAX_PAGE_SIZE, queryHelper.createPageable(new Page().setPageSize(999999), null).getPageSize());
        }

        @Test
        @DisplayName("pageSize 低于下限时被抬到下限")
        void pageSizeRaisedToMin() {
            assertEquals(MIN_PAGE_SIZE, queryHelper.createPageable(new Page().setPageSize(1), null).getPageSize());
        }

        @Test
        @DisplayName("落在区间内的 pageSize 原样使用")
        void pageSizeInRangePreserved() {
            assertEquals(50, queryHelper.createPageable(new Page().setPageSize(50), null).getPageSize());
        }

        @Test
        @DisplayName("恰好等于上下限的值原样使用")
        void boundaryPageSizePreserved() {
            assertEquals(MIN_PAGE_SIZE, queryHelper.createPageable(new Page().setPageSize(MIN_PAGE_SIZE), null).getPageSize());
            assertEquals(MAX_PAGE_SIZE, queryHelper.createPageable(new Page().setPageSize(MAX_PAGE_SIZE), null).getPageSize());
        }

        @Test
        @DisplayName("page 为 null 时也能构建出合法 Pageable")
        void nullPageStillBuildable() {
            Pageable pageable = queryHelper.createPageable(null, null);
            assertEquals(0, pageable.getPageNumber());
            assertEquals(DEFAULT_PAGE_SIZE, pageable.getPageSize());
        }

        @Test
        @DisplayName("排序被带入 Pageable")
        void sortCarriedIntoPageable() {
            Pageable pageable = queryHelper.createPageable(new Page(), new Sort().setField("nickname").setDirection("asc"));
            List<Order> orders = pageable.getSort().toList();
            assertEquals(3, orders.size());
            assertTrue(orders.get(0).isAscending());
            assertEquals("nickname", orders.get(0).getProperty());
        }
    }

    @Nested
    @DisplayName("4. 排序必须以唯一列收尾（分页稳定性）")
    class SortStability {

        @Test
        @DisplayName("按 ID 排序时只追加 ID 一列")
        void sortingByIdAddsNothing() {
            // ID 已经唯一，再追加列只会让排序键变长、白白拖慢查询
            List<Order> orders = queryHelper.createSort(new Sort().setField("id")).toList();
            assertEquals(1, orders.size());
            assertEquals("id", orders.get(0).getProperty());
        }

        @Test
        @DisplayName("按创建时间排序时补一列 ID")
        void sortingByCreateTimeAppendsId() {
            List<Order> orders = queryHelper.createSort(new Sort().setField(STRING_CREATE_TIME)).toList();
            assertEquals(2, orders.size());
            assertEquals(STRING_CREATE_TIME, orders.get(0).getProperty());
            assertEquals(CurdEntity.STRING_ID, orders.get(1).getProperty());
        }

        @Test
        @DisplayName("按其他字段排序时补创建时间与 ID 两列")
        void sortingByOtherAppendsCreateTimeAndId() {
            List<Order> orders = queryHelper.createSort(new Sort().setField("nickname")).toList();
            assertEquals(3, orders.size());
            assertEquals("nickname", orders.get(0).getProperty());
            assertEquals(STRING_CREATE_TIME, orders.get(1).getProperty());
            assertEquals(CurdEntity.STRING_ID, orders.get(2).getProperty());
        }

        @Test
        @DisplayName("ID 永远是最后一级排序")
        void idIsAlwaysLast() {
            // 缺了这一步：同 createTime 的记录在第 1 页和第 2 页可能各出现一次，
            // 也可能一次都不出现。分页结果直接不可信
            List<Order> orders = queryHelper.createSort(new Sort().setField("nickname")).toList();
            assertEquals(CurdEntity.STRING_ID, orders.get(orders.size() - 1).getProperty());
        }

        @Test
        @DisplayName("没有唯一列收尾的排序一律视为不稳定")
        void everySortEndsWithUniqueColumn() {
            for (String field : List.of("id", "nickname", STRING_CREATE_TIME, "unknownField")) {
                List<Order> orders = queryHelper.createSort(new Sort().setField(field)).toList();
                assertEquals(CurdEntity.STRING_ID, orders.get(orders.size() - 1).getProperty(),
                        "按 " + field + " 排序时未以 ID 收尾");
            }
        }

        @Test
        @DisplayName("排序方向按 asc/desc 生效")
        void directionApplied() {
            assertTrue(queryHelper.createSort(new Sort().setField("id").setDirection("asc")).toList()
                    .get(0).isAscending());
            assertFalse(queryHelper.createSort(new Sort().setField("id").setDirection("desc")).toList()
                    .get(0).isAscending());
        }
    }

    @Nested
    @DisplayName("5. 查询条件构建")
    class PredicateList {

        private CriteriaBuilder builder;
        private From<Object, Object> root;

        @BeforeEach
        void setUpMocks() {
            builder = Mockito.mock(CriteriaBuilder.class);
            root = Mockito.mock(From.class);
            Mockito.lenient().when(builder.equal(Mockito.any(), Mockito.any()))
                    .thenReturn(Mockito.mock(Predicate.class));
            Mockito.lenient().when(builder.like(Mockito.any(), Mockito.anyString()))
                    .thenReturn(Mockito.mock(Predicate.class));
            Mockito.lenient().when(root.get(Mockito.anyString())).thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
        }

        /**
         * 取出 {@code like} 实际使用的模式串
         *
         * @return 模式串列表
         */
        private List<String> captureLikePatterns() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(builder, Mockito.atLeastOnce()).like(Mockito.any(), captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("搜索对象为 null 时不产生任何条件")
        void nullSearchGivesNoPredicate() {
            assertTrue(queryHelper.getPredicateList(root, builder, null, false).isEmpty());
        }

        @Test
        @DisplayName("全新实体（所有字段为 null）不产生任何条件")
        void blankEntityGivesNoPredicate() {
            assertTrue(queryHelper.getPredicateList(root, builder, new DemoEntity(), false).isEmpty());
        }

        @Test
        @DisplayName("值为 null 的字段不参与筛选")
        void nullFieldSkipped() {
            DemoEntity entity = new DemoEntity();
            entity.setNickname("abc");
            assertEquals(1, queryHelper.getPredicateList(root, builder, entity, false).size());
        }

        @Test
        @DisplayName("未标 @Search 的字段按强匹配处理")
        void plainFieldUsesEqual() {
            DemoEntity entity = new DemoEntity();
            entity.setNickname("abc");
            queryHelper.getPredicateList(root, builder, entity, false);
            Mockito.verify(builder).equal(Mockito.any(), Mockito.eq("abc"));
            Mockito.verify(builder, Mockito.never()).like(Mockito.any(), Mockito.anyString());
        }

        @Test
        @DisplayName("全匹配查询一律用 = ，即使标了 @Search")
        void equalsQueryIgnoresSearchAnnotation() {
            // Search 只在模糊查询中生效，全匹配必须仍是 =
            DemoEntity entity = new DemoEntity();
            entity.setKeyword("abc");
            queryHelper.getPredicateList(root, builder, entity, true);
            Mockito.verify(builder).equal(Mockito.any(), Mockito.eq("abc"));
            Mockito.verify(builder, Mockito.never()).like(Mockito.any(), Mockito.anyString());
        }

        @Test
        @DisplayName("@Search 生成左模糊 LIKE '值%'")
        void searchGeneratesLeftLike() {
            DemoEntity entity = new DemoEntity();
            entity.setKeyword("abc");
            queryHelper.getPredicateList(root, builder, entity, false);
            assertTrue(captureLikePatterns().contains("abc%"));
        }

        @Test
        @DisplayName("@Search(fullLike = true) 生成 LIKE '%值%'")
        void fullLikeGeneratesBothSideLike() {
            DemoEntity entity = new DemoEntity();
            entity.setFullKeyword("abc");
            queryHelper.getPredicateList(root, builder, entity, false);
            assertTrue(captureLikePatterns().contains("%abc%"));
        }

        @Test
        @DisplayName("LIKE 查询值中的 % 被转义")
        void percentSignEscaped() {
            // 不转义的话，`%` 会让条件退化成全表匹配：
            // 本意「查名字里带 100% 的记录」，实际返回整张表
            DemoEntity entity = new DemoEntity();
            entity.setKeyword("100%");
            queryHelper.getPredicateList(root, builder, entity, false);
            assertTrue(captureLikePatterns().contains("100\\%%"),
                    "通配符必须按字面量处理，实际: " + captureLikePatterns());
        }

        @Test
        @DisplayName("LIKE 查询值中的 _ 被转义")
        void underscoreEscaped() {
            DemoEntity entity = new DemoEntity();
            entity.setKeyword("a_b");
            queryHelper.getPredicateList(root, builder, entity, false);
            assertTrue(captureLikePatterns().contains("a\\_b%"));
        }

        @Test
        @DisplayName("LIKE 查询值中的反斜杠先被转义，避免二次转义")
        void backslashEscapedFirst() {
            // 顺序必须是「先转义 \ 再转义 % 和 _」：
            // 反过来会把新产生的转义符再转义一次，`a%` 变成 `a\\\%`
            DemoEntity entity = new DemoEntity();
            entity.setKeyword("a\\%b");
            queryHelper.getPredicateList(root, builder, entity, false);
            assertTrue(captureLikePatterns().contains("a\\\\\\%b%"),
                    "反斜杠转义顺序有误，实际: " + captureLikePatterns());
        }

        @Test
        @DisplayName("全模糊查询同样转义通配符")
        void fullLikeAlsoEscapes() {
            DemoEntity entity = new DemoEntity();
            entity.setFullKeyword("%_");
            queryHelper.getPredicateList(root, builder, entity, false);
            assertTrue(captureLikePatterns().contains("%\\%\\_%"));
        }

        @Test
        @DisplayName("@Transient 字段不参与搜索")
        void transientFieldSkipped() {
            DemoEntity entity = new DemoEntity();
            entity.setTransientField("x");
            assertTrue(queryHelper.getPredicateList(root, builder, entity, false).isEmpty());
        }

        @Test
        @DisplayName("@OneToMany 字段不参与搜索")
        void oneToManySkipped() {
            DemoEntity entity = new DemoEntity();
            entity.setChildren(new ArrayList<>(List.of(new DemoEntity())));
            assertTrue(queryHelper.getPredicateList(root, builder, entity, false).isEmpty(),
                    "一对多会展开成子查询，代价高且语义不明确，这里一律不参与");
        }

        @Test
        @DisplayName("@ManyToOne 字段走 INNER JOIN 并把条件加在关联上")
        void manyToOneJoins() {
            Join<Object, Object> join = Mockito.mock(Join.class);
            Mockito.lenient().when(root.join(Mockito.eq("parent"), Mockito.any())).thenReturn(join);
            Mockito.lenient().when(join.get(Mockito.anyString()))
                    .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));

            DemoEntity parent = new DemoEntity();
            parent.setUsername("root");
            DemoEntity entity = new DemoEntity();
            entity.setParent(parent);

            List<Predicate> predicates = queryHelper.getPredicateList(root, builder, entity, false);
            assertEquals(1, predicates.size());
            Mockito.verify(root).join(Mockito.eq("parent"), Mockito.eq(jakarta.persistence.criteria.JoinType.INNER));
            Mockito.verify(builder).equal(Mockito.any(), Mockito.eq("root"));
        }

        @Test
        @DisplayName("未标 @SearchEmpty 的字段传空串时不参与筛选")
        void emptyStringSkippedWithoutAnnotation() {
            // 「不传条件」与「查空值」是两件事：
            // 大量客户端会把空输入框原样提交，若空串被当成「查空值」，
            // 一个没填的搜索框就会把结果集清空
            DemoEntity entity = new DemoEntity();
            entity.setNickname("");
            assertTrue(queryHelper.getPredicateList(root, builder, entity, false).isEmpty());
        }

        @Test
        @DisplayName("未标 @SearchEmpty 的字段传纯空白同样不参与筛选")
        void blankStringSkipped() {
            DemoEntity entity = new DemoEntity();
            entity.setNickname("    ");
            assertTrue(queryHelper.getPredicateList(root, builder, entity, false).isEmpty());
        }

        @Test
        @DisplayName("标了 @SearchEmpty 的字段传空串时会参与筛选")
        void emptyStringIncludedWithAnnotation() {
            DemoEntity entity = new DemoEntity();
            entity.setEmptyProbe("");
            assertEquals(1, queryHelper.getPredicateList(root, builder, entity, false).size());
        }

        @Test
        @DisplayName("多个非空字段产生多个条件")
        void multipleFieldsProduceMultiplePredicates() {
            DemoEntity entity = new DemoEntity();
            entity.setNickname("a");
            entity.setRemark("b");
            entity.setKeyword("c");
            assertEquals(3, queryHelper.getPredicateList(root, builder, entity, false).size());
        }

        @Test
        @DisplayName("返回的条件列表可被调用方继续追加")
        void listIsModifiable() {
            // CurdService.addSearchPredicate 依赖这一点追加自定义条件
            List<Predicate> predicates = queryHelper.getPredicateList(root, builder, new DemoEntity(), false);
            assertNotNull(predicates);
            Predicate extra = Mockito.mock(Predicate.class);
            predicates.add(extra);
            assertEquals(1, predicates.size());
        }
    }
}
