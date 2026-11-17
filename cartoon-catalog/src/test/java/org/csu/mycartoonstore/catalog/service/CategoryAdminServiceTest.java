package org.csu.mycartoonstore.catalog.service; // 分类维护服务：两级树、增删改与引用校验。

import org.csu.mycartoonstore.catalog.dto.CategoryForm; // 表单。
import org.csu.mycartoonstore.catalog.dto.CategoryRow; // 列表行。
import org.csu.mycartoonstore.catalog.dto.CategoryUsage; // 商品计数。
import org.csu.mycartoonstore.catalog.entity.Category; // 分类实体。
import org.csu.mycartoonstore.catalog.mapper.CategoryMapper; // 分类读写。
import org.csu.mycartoonstore.catalog.mapper.ProductMapper; // 商品计数。
import org.csu.mycartoonstore.common.exception.BizException; // 业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码断言。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.junit.jupiter.api.extension.ExtendWith; // Mockito 扩展。
import org.mockito.ArgumentCaptor; // 捕获写入对象。
import org.mockito.InjectMocks; // 被测对象。
import org.mockito.Mock; // 协作方。
import org.mockito.junit.jupiter.MockitoExtension; // Mockito 生命周期。

import java.util.List; // 列表。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.any; // 任意参数。
import static org.mockito.Mockito.never; // 不该发生的调用。
import static org.mockito.Mockito.verify; // 行为断言。
import static org.mockito.Mockito.when; // 打桩。

// M07-03：依据 docs/01 FR-A-02、docs/06 AC-25/AC-26。
// 覆盖：编码格式与大小写、同级同名、二级后缀与父级层级、排序非负、编辑只改名称与排序、
//       删除前的子分类与商品引用校验、树形列表的层级/父名/两个计数。
// 不覆盖：页面渲染与跳转（M07-04）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class CategoryAdminServiceTest {

    @Mock // 分类读写。
    private CategoryMapper categoryMapper;

    @Mock // 商品计数。
    private ProductMapper productMapper;

    @InjectMocks // 被测服务。
    private CategoryAdminService service;

    // ---------------------------------------------------------------- 新增

    @Test // 一级分类：编码与名称去空白，排序缺省 0。
    @DisplayName("新增一级：去空白、排序缺省 0、父级为空")
    void createTop() {
        when(categoryMapper.selectCount(any())).thenReturn(0L); // 编码与名称都不冲突。
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class); // 捕获写入对象。

        Category created = service.create(form("  STARLINE  ", "  星轨列车  ", null, null)); // 两侧带空格。

        verify(categoryMapper).insert(saved.capture()); // 确认插入一次。
        assertThat(saved.getValue().getCode()).isEqualTo("STARLINE"); // 编码去空白。
        assertThat(saved.getValue().getName()).isEqualTo("星轨列车"); // 名称去空白。
        assertThat(saved.getValue().getParentId()).isNull(); // 一级没有父级。
        assertThat(saved.getValue().getSortOrder()).isZero(); // 缺省排序 0。
        assertThat(created.getCode()).isEqualTo("STARLINE"); // 返回值带 id 由数据库回填。
    }

    @Test // 编码必须大写：小写不静默转换，直接 400。
    @DisplayName("新增：小写编码被拒")
    void createLowercaseCode() {
        assertThatThrownBy(() -> service.create(form("starline", "星轨列车", null, 1)))
                .isInstanceOf(BizException.class) // 业务异常。
                .extracting(e -> ((BizException) e).code()) // 取错误码。
                .isEqualTo(ErrorCode.BAD_REQUEST); // 400。
        verify(categoryMapper, never()).insert(any(Category.class)); // 不写库。
    }

    @Test // 编码字符集：下划线不是合法字符。
    @DisplayName("新增：非法字符的编码被拒")
    void createIllegalCode() {
        assertThatThrownBy(() -> service.create(form("STAR_LINE", "星轨列车", null, 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test // 编码必填。
    @DisplayName("新增：空编码被拒")
    void createBlankCode() {
        assertThatThrownBy(() -> service.create(form("   ", "星轨列车", null, 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("编码不能为空"); // 文案指到具体字段。
    }

    @Test // 名称必填且不超 50。
    @DisplayName("新增：空名称与超长名称被拒")
    void createBadName() {
        assertThatThrownBy(() -> service.create(form("STARLINE", "  ", null, 1))) // 全空白。
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThatThrownBy(() -> service.create(form("STARLINE", "长".repeat(51), null, 1))) // 51 字。
                .isInstanceOf(BizException.class)
                .hasMessageContaining("50"); // 提示上限。
    }

    @Test // 排序不能为负。
    @DisplayName("新增：负排序被拒")
    void createNegativeSort() {
        assertThatThrownBy(() -> service.create(form("STARLINE", "星轨列车", null, -1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test // 编码全局唯一：冲突给 409，不是 500。
    @DisplayName("新增：编码重复 → 409")
    void createDuplicateCode() {
        when(categoryMapper.selectCount(any())).thenReturn(1L); // 第一次查重就命中。

        assertThatThrownBy(() -> service.create(form("STARLINE", "星轨列车", null, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("编码已存在"); // 可读文案。
    }

    @Test // 同级同名唯一：编码不冲突但名称冲突。
    @DisplayName("新增：同级同名 → 409")
    void createDuplicateSiblingName() {
        when(categoryMapper.selectCount(any())).thenReturn(0L, 1L); // 编码通过、名称命中。

        assertThatThrownBy(() -> service.create(form("STARLINE2", "星轨列车", null, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("同级已存在同名分类");
    }

    @Test // 二级分类：父级必须存在且是一级，编码以 -COMIC 结尾。
    @DisplayName("新增二级：父级存在且是一级即可")
    void createChild() {
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, "STARLINE", null, "星轨列车", 1)); // 一级父分类。
        when(categoryMapper.selectCount(any())).thenReturn(0L); // 编码与名称都不冲突。
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class); // 捕获写入。

        service.create(form("STARLINE-COMIC", "单行本", 7L, 1)); // 新增二级。

        verify(categoryMapper).insert(saved.capture()); // 写入一次。
        assertThat(saved.getValue().getParentId()).isEqualTo(7L); // 挂在父级下。
    }

    @Test // 后缀规则：二级必须是 -COMIC / -MERCH。
    @DisplayName("新增二级：后缀不合法 → 400")
    void createChildBadSuffix() {
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, "STARLINE", null, "星轨列车", 1)); // 父级合法。

        assertThatThrownBy(() -> service.create(form("STARLINE-BADGE", "徽章", 7L, 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("-COMIC"); // 提示合法后缀。
    }

    @Test // 父级不存在。
    @DisplayName("新增二级：父级不存在 → 400")
    void createChildParentMissing() {
        when(categoryMapper.selectById(99L)).thenReturn(null); // 父级查不到。

        assertThatThrownBy(() -> service.create(form("X-COMIC", "单行本", 99L, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("父分类必须是一级分类");
    }

    @Test // 禁止三级：父级本身是二级。
    @DisplayName("新增：父级是二级 → 400")
    void createChildUnderLeaf() {
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, "STARLINE-COMIC", 7L, "单行本", 1)); // 二级当父。

        assertThatThrownBy(() -> service.create(form("X-COMIC", "第三级", 8L, 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- 编辑

    @Test // 编辑只写名称与排序，编码与父级不会被覆盖。
    @DisplayName("编辑：只改名称与排序")
    void updateOnlyNameAndSort() {
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, "STARLINE", null, "星轨列车", 1)); // 已存在。
        when(categoryMapper.selectCount(any())).thenReturn(0L); // 同级没有重名。
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class); // 捕获更新对象。

        service.update(7L, form("HACKED", "星轨列车（新）", 99L, 3)); // 表单里故意带上 code 与父级。

        verify(categoryMapper).updateById(saved.capture()); // 更新一次。
        assertThat(saved.getValue().getName()).isEqualTo("星轨列车（新）"); // 名称生效。
        assertThat(saved.getValue().getSortOrder()).isEqualTo(3); // 排序生效。
        assertThat(saved.getValue().getCode()).isNull(); // 编码不写：非空字段才更新。
        assertThat(saved.getValue().getParentId()).isNull(); // 父级也不写。
    }

    @Test // 编辑后同级重名：409。
    @DisplayName("编辑：同级同名 → 409")
    void updateDuplicateName() {
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, "STARLINE", null, "星轨列车", 1));
        when(categoryMapper.selectCount(any())).thenReturn(1L); // 另一个同级分类同名。

        assertThatThrownBy(() -> service.update(7L, form("STARLINE", "雾港侦探", null, 1)))
                .isInstanceOf(BizException.class)
                .hasMessage("同级已存在同名分类");
    }

    @Test // 分类不存在：404。
    @DisplayName("编辑：分类不存在 → 404")
    void updateMissing() {
        when(categoryMapper.selectById(404L)).thenReturn(null); // 查不到。

        assertThatThrownBy(() -> service.update(404L, form("X", "某分类", null, 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------------------------------------------------------------- 删除

    @Test // 有子分类：拒绝删除（AC-26）。
    @DisplayName("删除：有子分类 → 409")
    void deleteWithChildren() {
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, "STARLINE", null, "星轨列车", 1));
        when(categoryMapper.selectCount(any())).thenReturn(2L); // 下面还有两个品类。

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("子分类"); // 说明原因。
        verify(categoryMapper, never()).deleteById(any(Long.class)); // 不删。
    }

    @Test // 有商品引用（含下架）：同样拒绝（AC-26）。
    @DisplayName("删除：有商品引用 → 409")
    void deleteWithProducts() {
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, "STARLINE-COMIC", 7L, "单行本", 1));
        when(categoryMapper.selectCount(any())).thenReturn(0L); // 无子分类。
        when(productMapper.selectCount(any())).thenReturn(3L); // 有 3 件商品（含下架）。

        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品");
        verify(categoryMapper, never()).deleteById(any(Long.class)); // 不删。
    }

    @Test // 无引用：可以删。
    @DisplayName("删除：无引用 → 删除成功")
    void deleteOk() {
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, "STARLINE-PAPER", 7L, "色纸", 5));
        when(categoryMapper.selectCount(any())).thenReturn(0L); // 无子分类。
        when(productMapper.selectCount(any())).thenReturn(0L); // 无商品。

        service.delete(9L); // 执行删除。

        verify(categoryMapper).deleteById(9L); // 按主键删。
    }

    @Test // 不存在：404。
    @DisplayName("删除：分类不存在 → 404")
    void deleteMissing() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------------------------------------------------------------- 列表与回显

    @Test // 树形：一级在前，二级紧随，带父名、层级、子分类数与商品数。
    @DisplayName("列表：一级在前、二级紧随，计数正确")
    void treeAssembles() {
        Category top = category(7L, "STARLINE", null, "星轨列车", 1); // 一级。
        Category comic = category(8L, "STARLINE-COMIC", 7L, "单行本", 1); // 二级。
        Category badge = category(9L, "STARLINE-BADGE", 7L, "徽章", 2); // 二级。
        when(categoryMapper.selectList(any())).thenReturn(List.of(top, comic, badge)); // 查询顺序即展示顺序。
        when(productMapper.countByCategory()).thenReturn(List.of(usage(8L, 6))); // 单行本下 6 件商品。

        List<CategoryRow> rows = service.tree(); // 组装。

        assertThat(rows).hasSize(3); // 一行一个分类。
        assertThat(rows.get(0).getLevel()).isEqualTo(1); // 一级。
        assertThat(rows.get(0).getChildCount()).isEqualTo(2); // 两个品类。
        assertThat(rows.get(0).getProductCount()).isZero(); // 商品挂在二级上。
        assertThat(rows.get(1).getLevel()).isEqualTo(2); // 二级。
        assertThat(rows.get(1).getParentName()).isEqualTo("星轨列车"); // 父名来自同一份数据。
        assertThat(rows.get(1).getProductCount()).isEqualTo(6); // 商品数来自分组查询。
        assertThat(rows.get(2).getProductCount()).isZero(); // 没有商品的分类默认 0。
    }

    @Test // 删除按钮状态：有引用就给出具体原因。
    @DisplayName("列表行：可删性与禁用原因")
    void rowDeleteHint() {
        CategoryRow leaf = new CategoryRow(8L, "STARLINE-COMIC", "单行本", 7L, "星轨列车", 1, 2, 0, 3); // 3 件商品。
        CategoryRow top = new CategoryRow(7L, "STARLINE", "星轨列车", null, null, 1, 1, 2, 0); // 2 个子分类。

        assertThat(leaf.isDeletable()).isFalse(); // 有商品不能删。
        assertThat(leaf.getDeleteHint()).contains("3"); // 说明数量。
        assertThat(top.getDeleteHint()).contains("2"); // 一级先报子分类。
    }

    @Test // 编辑页回显：把库里的值装进表单。
    @DisplayName("回显：编辑表单带出当前值")
    void formEchoes() {
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, "STARLINE-COMIC", 7L, "单行本", 2)); // 已存在。

        CategoryForm form = service.form(8L); // 取表单。

        assertThat(form.getCode()).isEqualTo("STARLINE-COMIC"); // 只读展示。
        assertThat(form.getName()).isEqualTo("单行本"); // 可改。
        assertThat(form.getParentId()).isEqualTo(7L); // 只读展示。
        assertThat(form.getSortOrder()).isEqualTo(2); // 可改。
    }

    // ---------------------------------------------------------------- 夹具

    /** 造表单：四个字段都由用例指定 */
    private CategoryForm form(String code, String name, Long parentId, Integer sortOrder) {
        CategoryForm form = new CategoryForm(); // 空表单。
        form.setCode(code); // 编码。
        form.setName(name); // 名称。
        form.setParentId(parentId); // 父级。
        form.setSortOrder(sortOrder); // 排序。
        return form; // 交给服务。
    }

    /** 造分类实体：只填本任务用到的列 */
    private Category category(Long id, String code, Long parentId, String name, int sortOrder) {
        Category category = new Category(); // 空实体。
        category.setId(id); // 主键。
        category.setCode(code); // 编码。
        category.setParentId(parentId); // 父级。
        category.setName(name); // 名称。
        category.setSortOrder(sortOrder); // 排序。
        return category; // 交给用例。
    }

    /** 造一条分类商品计数 */
    private CategoryUsage usage(long categoryId, long productCount) {
        CategoryUsage usage = new CategoryUsage(); // 空对象。
        usage.setCategoryId(categoryId); // 分类。
        usage.setProductCount(productCount); // 数量。
        return usage; // 交给用例。
    }
}
