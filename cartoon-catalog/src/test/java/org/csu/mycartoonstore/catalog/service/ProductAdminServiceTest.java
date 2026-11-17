package org.csu.mycartoonstore.catalog.service; // 后台商品服务：列表、新增、编辑与校验。

import org.csu.mycartoonstore.catalog.dto.AdminProductRow; // 列表行。
import org.csu.mycartoonstore.catalog.dto.CategoryOption; // 下拉项。
import org.csu.mycartoonstore.catalog.dto.ProductForm; // 表单。
import org.csu.mycartoonstore.catalog.entity.Category; // 分类实体。
import org.csu.mycartoonstore.catalog.entity.Product; // 商品实体。
import org.csu.mycartoonstore.catalog.mapper.CategoryMapper; // 分类查询。
import org.csu.mycartoonstore.catalog.mapper.ProductMapper; // 商品读写。
import org.csu.mycartoonstore.common.exception.BizException; // 业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.common.page.PageResult; // 分页结果。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.junit.jupiter.api.extension.ExtendWith; // Mockito 扩展。
import org.mockito.ArgumentCaptor; // 捕获写入对象。
import org.mockito.InjectMocks; // 被测对象。
import org.mockito.Mock; // 协作方。
import org.mockito.junit.jupiter.MockitoExtension; // Mockito 生命周期。

import java.math.BigDecimal; // 价格。
import java.util.List; // 列表。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.any; // 任意参数。
import static org.mockito.Mockito.never; // 不该发生的调用。
import static org.mockito.Mockito.verify; // 行为断言。
import static org.mockito.Mockito.when; // 打桩。

// M07-05：依据 docs/01 FR-A-03、docs/06 AC-25/AC-27。
// 覆盖：列表参数归一与白名单、新增的字段校验与默认 DRAFT、编辑的版本冲突、分类下拉与表单回显。
// 不覆盖：页面渲染（M07-06）、上下架与补货（M07-07）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class ProductAdminServiceTest {

    @Mock // 商品读写。
    private ProductMapper productMapper;

    @Mock // 分类查询。
    private CategoryMapper categoryMapper;

    @InjectMocks // 被测服务。
    private ProductAdminService service;

    // ---------------------------------------------------------------- 列表

    @Test // 空参数：第 1 页、每页 10 条、状态 ALL。
    @DisplayName("列表：空参数用默认分页与 ALL")
    void searchUsesDefaults() {
        when(productMapper.selectAdminPage(any())).thenReturn(List.of()); // 没有数据。
        when(productMapper.countAdminPage(any())).thenReturn(0L); // 总数 0。

        PageResult<AdminProductRow> result = service.search(null, null, null, null, null); // 什么都不传。

        assertThat(result.page()).isEqualTo(1); // 默认第 1 页。
        assertThat(result.pageSize()).isEqualTo(10); // 后台默认 10 条。
        assertThat(result.isEmpty()).isTrue(); // 空结果。
    }

    @Test // 状态只认 ALL / DRAFT / ON_SALE。
    @DisplayName("列表：非法状态 → 400")
    void searchBadStatus() {
        assertThatThrownBy(() -> service.search(null, null, "SOLD", null, null))
                .isInstanceOf(BizException.class) // 业务异常。
                .extracting(e -> ((BizException) e).code()) // 错误码。
                .isEqualTo(ErrorCode.BAD_REQUEST); // 400。
    }

    @Test // 页码非法：交给 PageQuery 归一/拒绝。
    @DisplayName("列表：非法页码 → 400")
    void searchBadPage() {
        assertThatThrownBy(() -> service.search(null, null, null, "abc", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test // 关键字超长：400，不静默截断。
    @DisplayName("列表：超长关键字 → 400")
    void searchLongKeyword() {
        assertThatThrownBy(() -> service.search(null, "长".repeat(121), null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("120"); // 提示上限。
    }

    @Test // 分类参数格式错：400。
    @DisplayName("列表：分类参数非数字 → 400")
    void searchBadCategory() {
        assertThatThrownBy(() -> service.search("abc", null, null, null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- 新增

    @Test // 正常新增：状态 DRAFT、价格两位小数、库存与排序按填写。
    @DisplayName("新增：默认 DRAFT，价格统一两位小数")
    void createOk() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 二级分类存在。
        when(productMapper.selectCount(any())).thenReturn(0L); // SKU 不重复。
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class); // 捕获写入。

        service.create(form(" STARLINE-C09 ", " 第9卷 ", "8", " 28.5 ", "3", 2)); // SKU 两侧带空格。

        verify(productMapper).insert(saved.capture()); // 写入一次。
        assertThat(saved.getValue().getStatus()).isEqualTo("DRAFT"); // 默认下架。
        assertThat(saved.getValue().getSku()).isEqualTo("STARLINE-C09"); // 去空白后入库。
        assertThat(saved.getValue().getPrice()).isEqualTo(new BigDecimal("28.50")); // 两位小数。
        assertThat(saved.getValue().getStock()).isEqualTo(3); // 初始库存。
        assertThat(saved.getValue().getSortOrder()).isEqualTo(2); // 排序。
    }

    @Test // SKU 必须是全大写：小写直接 400，不静默转换。
    @DisplayName("新增：小写 SKU → 400")
    void createLowercaseSku() {
        assertThatThrownBy(() -> service.create(form("starline", "第9卷", "8", "28.00", "0", 0)))
                .isInstanceOf(BizException.class) // 400。
                .hasMessageContaining("大写"); // 文案说明字符集。
        verify(productMapper, never()).insert(any(Product.class)); // 不写库。
    }

    @Test // SKU 重复：409。
    @DisplayName("新增：SKU 重复 → 409")
    void createDuplicateSku() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法。
        when(productMapper.selectCount(any())).thenReturn(1L); // SKU 已存在。

        assertThatThrownBy(() -> service.create(form("STARLINE-C09", "第9卷", "8", "28.00", "0", 0)))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU 已存在");
    }

    @Test // 分类必须挂二级：一级分类被拒。
    @DisplayName("新增：分类是一级 → 400")
    void createCategoryIsTop() {
        when(categoryMapper.selectById(7L)).thenReturn(top(7L, "星轨列车")); // 一级分类。

        assertThatThrownBy(() -> service.create(form("STARLINE-C09", "第9卷", "7", "28.00", "0", 0)))
                .isInstanceOf(BizException.class)
                .hasMessage("所属分类必须是二级分类");
    }

    @Test // 价格边界：0、三位小数、非数字都拒绝。
    @DisplayName("新增：价格非法 → 400")
    void createBadPrice() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法；价格在查重之前就被校验。

        assertThatThrownBy(() -> service.create(form("S-1", "商品", "8", "0", "0", 0)))
                .isInstanceOf(BizException.class); // 必须大于 0。
        assertThatThrownBy(() -> service.create(form("S-1", "商品", "8", "12.345", "0", 0)))
                .isInstanceOf(BizException.class); // 最多两位小数。
        assertThatThrownBy(() -> service.create(form("S-1", "商品", "8", "abc", "0", 0)))
                .isInstanceOf(BizException.class); // 必须是数字。
    }

    @Test // 库存为负：400。
    @DisplayName("新增：负库存 → 400")
    void createNegativeStock() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法；库存在查重之前就被校验。

        assertThatThrownBy(() -> service.create(form("S-2", "商品", "8", "10.00", "-1", 0)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为负");
    }

    @Test // 封面必填。
    @DisplayName("新增：封面为空 → 400")
    void createBlankCover() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法；封面在查重之前就被校验。
        ProductForm form = form("S-3", "商品", "8", "10.00", "0", 0); // 正常字段。
        form.setCoverImage("  "); // 封面空白。

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("封面");
    }

    @Test // 描述超长：400，不写库（M09-05）。
    @DisplayName("新增：描述超 2000 字 → 400")
    void createTooLongDescription() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法，让校验走到描述。
        ProductForm form = form("S-4", "商品", "8", "10.00", "0", 0); // 常规表单。
        form.setDescription("描".repeat(2001)); // 刚好超一个字符。

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("2000"); // 文案带上限值。
        verify(productMapper, never()).insert(any(Product.class)); // 不写库。
    }

    @Test // 空白描述：存 NULL，而不是空串。
    @DisplayName("新增：空白描述存 NULL")
    void createBlankDescriptionStoredAsNull() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本"));
        when(productMapper.selectCount(any())).thenReturn(0L); // SKU 不重复。
        ProductForm form = form("S-5", "商品", "8", "10.00", "0", 0);
        form.setDescription("   "); // 只有空白。
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);

        service.create(form);

        verify(productMapper).insert(saved.capture());
        assertThat(saved.getValue().getDescription()).isNull(); // 空描述不入库为空串。
    }

    @Test // 外链封面：400（外链留到 docs/12）。
    @DisplayName("新增：外链封面 → 400")
    void createExternalCover() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法。
        ProductForm form = form("S-6", "商品", "8", "10.00", "0", 0);
        form.setCoverImage("https://evil.example.com/a.png"); // 外部地址。

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("站内路径"); // 文案说明只收站内路径。
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test // 目录上跳与缺前缀：都按非站内路径拒绝。
    @DisplayName("新增：封面含 .. 或缺少 images/ 前缀 → 400")
    void createBadCoverPath() {
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法。
        ProductForm traversal = form("S-7", "商品", "8", "10.00", "0", 0);
        traversal.setCoverImage("images/../secret.svg"); // 上跳目录。
        ProductForm noPrefix = form("S-8", "商品", "8", "10.00", "0", 0);
        noPrefix.setCoverImage("products/S/cover.svg"); // 少了 images/ 前缀。

        assertThatThrownBy(() -> service.create(traversal)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(noPrefix)).isInstanceOf(BizException.class);
    }

    // ---------------------------------------------------------------- 编辑
    @Test // 编辑：只写资料列，版本进条件，SKU 与库存不写。
    @DisplayName("编辑：版本进条件，SKU 与库存不写")
    void updateWritesProfileOnly() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 已存在，版本 3。
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法。
        when(productMapper.updateProfile(any())).thenReturn(1); // 条件更新成功。
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class); // 捕获更新对象。

        ProductForm form = form("HACKED", "新名字", "8", "39.90", "999", 9); // 表单里故意带 SKU 与库存。
        form.setVersion(3L); // 隐藏域带回的版本。
        service.update(5L, form); // 提交编辑。

        verify(productMapper).updateProfile(saved.capture()); // 只走条件更新。
        assertThat(saved.getValue().getId()).isEqualTo(5L); // 条件 id。
        assertThat(saved.getValue().getVersion()).isEqualTo(3L); // 条件版本。
        assertThat(saved.getValue().getName()).isEqualTo("新名字"); // 名称生效。
        assertThat(saved.getValue().getSku()).isNull(); // SKU 不写。
        assertThat(saved.getValue().getStock()).isNull(); // 库存不写。
        assertThat(saved.getValue().getPrice()).isEqualTo(new BigDecimal("39.90")); // 价格两位小数。
    }

    @Test // 版本冲突：影响 0 行 → 409（AC-27）。
    @DisplayName("编辑：版本冲突 → 409（AC-27）")
    void updateVersionConflict() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 已存在。
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法。
        when(productMapper.updateProfile(any())).thenReturn(0); // 版本已被别人改掉。

        ProductForm stale = form("S-5", "新名字", "8", "39.90", "0", 9); // 旧页面提交。
        stale.setVersion(2L); // 旧页面手里的版本是 2，库里已经是 3。
        assertThatThrownBy(() -> service.update(5L, stale))
                .isInstanceOf(BizException.class)
                .hasMessage("数据已发生变化，请刷新后重试"); // 可读的 409 文案。
    }

    @Test // 缺少版本号：400，避免"没有条件"的更新。
    @DisplayName("编辑：缺版本号 → 400")
    void updateMissingVersion() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 已存在。
        when(categoryMapper.selectById(8L)).thenReturn(leaf(8L, 7L, "单行本")); // 分类合法。

        assertThatThrownBy(() -> service.update(5L, form("S-5", "新名字", "8", "39.90", "0", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本号");
    }

    @Test // 商品不存在：404。
    @DisplayName("编辑：商品不存在 → 404")
    void updateMissing() {
        when(productMapper.selectById(404L)).thenReturn(null); // 查不到。

        assertThatThrownBy(() -> service.update(404L, form("S-5", "名字", "8", "1.00", "0", 0)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------------------------------------------------------------- 下拉与回显

    @Test // 分类下拉：只列二级，显示「作品 / 品类」。
    @DisplayName("下拉：只列二级，带作品前缀")
    void categoryOptions() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                top(7L, "星轨列车"), leaf(8L, 7L, "单行本"), leaf(9L, 7L, "徽章"))); // 一个作品两个品类。

        List<CategoryOption> options = service.categoryOptions(); // 取下拉。

        assertThat(options).hasSize(2); // 只有二级。
        assertThat(options.get(0).getLabel()).isEqualTo("星轨列车 / 单行本"); // 前缀是作品名。
        assertThat(options.get(1).getId()).isEqualTo(9L); // 提交的是二级 id。
    }

    @Test // 编辑回显：价格两位小数、版本带出。
    @DisplayName("回显：价格与版本")
    void formEchoes() {
        Product product = product(5L, "S-5", 4L); // 版本 4。
        product.setPrice(new BigDecimal("28.00")); // 两位小数。
        product.setStock(6); // 库存只读展示。
        product.setSummary("简介"); // 可空字段。
        when(productMapper.selectById(5L)).thenReturn(product); // 已存在。

        ProductForm form = service.form(5L); // 取表单。

        assertThat(form.getPrice()).isEqualTo("28.00"); // 原样回显。
        assertThat(form.getVersion()).isEqualTo(4L); // 隐藏域带回。
        assertThat(form.getStock()).isEqualTo("6"); // 只读展示。
        assertThat(form.getSku()).isEqualTo("S-5"); // 只读展示。
    }

    // ---------------------------------------------------------------- 上下架

    @Test // 上下架：状态与版本一起提交，版本一致才更新。
    @DisplayName("上下架：版本一致 → 切换成功")
    void changeStatusOk() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 已存在，版本 3。
        when(productMapper.updateStatus(5L, "ON_SALE", 3L)).thenReturn(1); // 条件更新成功。

        service.changeStatus(5L, "ON_SALE", "3"); // 上架。

        verify(productMapper).updateStatus(5L, "ON_SALE", 3L); // 参数原样传下去。
    }

    @Test // 状态只能是上架或下架。
    @DisplayName("上下架：非法状态 → 400")
    void changeStatusBadValue() {
        assertThatThrownBy(() -> service.changeStatus(5L, "SOLD_OUT", "0")) // 不存在的状态。
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上架或下架");
        verify(productMapper, never()).updateStatus(any(), any(), any(Long.class)); // 不写库。
    }

    @Test // 版本冲突：409。
    @DisplayName("上下架：版本冲突 → 409")
    void changeStatusStale() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 已存在。
        when(productMapper.updateStatus(5L, "DRAFT", 2L)).thenReturn(0); // 旧版本。

        assertThatThrownBy(() -> service.changeStatus(5L, "DRAFT", "2"))
                .isInstanceOf(BizException.class)
                .hasMessage("数据已发生变化，请刷新后重试");
    }

    @Test // 缺版本号：400。
    @DisplayName("上下架：缺版本号 → 400")
    void changeStatusMissingVersion() {
        assertThatThrownBy(() -> service.changeStatus(5L, "ON_SALE", " "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本号");
    }

    // ---------------------------------------------------------------- 补货

    @Test // 补货成功：返回最新库存。
    @DisplayName("补货：返回累加后的库存")
    void restockOk() {
        Product before = product(5L, "S-5", 3L); // 补货前库存 5。
        Product after = product(5L, "S-5", 4L); // 补货后版本 4。
        after.setStock(8); // 5 + 3。
        when(productMapper.selectById(5L)).thenReturn(before, after); // 更新前、更新后各读一次。
        when(productMapper.restock(5L, 3)).thenReturn(1); // 原子累加成功。

        int stock = service.restock(5L, "3"); // 补 3 件。

        assertThat(stock).isEqualTo(8); // 返回最新库存。
    }

    @Test // 数量边界：0、超上限、非数字都拒绝。
    @DisplayName("补货：数量非法 → 400")
    void restockBadQuantity() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 商品存在。

        assertThatThrownBy(() -> service.restock(5L, "0")) // 不能为 0。
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.restock(5L, "100001")) // 超过单次上限。
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.restock(5L, "abc")) // 非整数。
                .isInstanceOf(BizException.class);
    }

    @Test // 更新 0 行且商品不存在：404。
    @DisplayName("补货：商品不存在 → 404")
    void restockMissing() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L), null); // 第一次存在，更新后已被删。
        when(productMapper.restock(5L, 3)).thenReturn(0); // 没更新到。

        assertThatThrownBy(() -> service.restock(5L, "3"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test // 更新 0 行但商品还在：库存到上限，409。
    @DisplayName("补货：库存溢出 → 409")
    void restockOverflow() {
        when(productMapper.selectById(5L)).thenReturn(product(5L, "S-5", 3L)); // 商品还在。
        when(productMapper.restock(5L, 100000)).thenReturn(0); // 溢出保护挡住了。

        assertThatThrownBy(() -> service.restock(5L, "100000"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }

    // ---------------------------------------------------------------- 夹具

    /** 造表单：常用字段一次给全 */
    private ProductForm form(String sku, String name, String categoryId, String price, String stock, Integer sort) {
        ProductForm form = new ProductForm(); // 空表单。
        form.setSku(sku); // SKU。
        form.setName(name); // 名称。
        form.setCategoryId(categoryId == null ? null : Long.valueOf(categoryId)); // 分类。
        form.setPrice(price); // 价格原串。
        form.setStock(stock); // 库存原串。
        form.setSummary("简介"); // 可空。
        form.setDescription("描述"); // 可空。
        form.setCoverImage("images/products/S/cover.svg"); // 必填。
        form.setSortOrder(sort); // 排序。
        return form; // 交给服务。
    }

    /** 造一级分类 */
    private Category top(long id, String name) {
        Category category = new Category(); // 空实体。
        category.setId(id); // 主键。
        category.setParentId(null); // 一级。
        category.setName(name); // 名称。
        category.setSortOrder(1); // 排序。
        return category; // 交给用例。
    }

    /** 造二级分类 */
    private Category leaf(long id, long parentId, String name) {
        Category category = new Category(); // 空实体。
        category.setId(id); // 主键。
        category.setParentId(parentId); // 父级。
        category.setName(name); // 名称。
        category.setSortOrder(1); // 排序。
        return category; // 交给用例。
    }

    /** 造商品实体 */
    private Product product(long id, String sku, long version) {
        Product product = new Product(); // 空实体。
        product.setId(id); // 主键。
        product.setSku(sku); // SKU。
        product.setName("旧名字"); // 名称。
        product.setCategoryId(8L); // 分类。
        product.setCoverImage("images/products/S/cover.svg"); // 封面。
        product.setPrice(new BigDecimal("28.00")); // 价格。
        product.setStock(5); // 库存。
        product.setStatus("DRAFT"); // 状态。
        product.setSortOrder(0); // 排序。
        product.setVersion(version); // 版本。
        return product; // 交给用例。
    }
}
