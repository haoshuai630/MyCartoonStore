package org.csu.mycartoonstore.catalog.service; // 后台商品查询与维护：列表、新增、编辑（版本校验）。

import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 条件构造。
import org.csu.mycartoonstore.catalog.dto.AdminProductQuery; // 列表条件。
import org.csu.mycartoonstore.catalog.dto.AdminProductRow; // 列表行。
import org.csu.mycartoonstore.catalog.dto.CategoryOption; // 分类下拉项。
import org.csu.mycartoonstore.catalog.dto.ProductForm; // 表单。
import org.csu.mycartoonstore.catalog.entity.Category; // 分类实体。
import org.csu.mycartoonstore.catalog.entity.Product; // 商品实体。
import org.csu.mycartoonstore.catalog.mapper.CategoryMapper; // 分类查询。
import org.csu.mycartoonstore.catalog.mapper.ProductMapper; // 商品读写。
import org.csu.mycartoonstore.common.exception.BizException; // 400/404/409。
import org.csu.mycartoonstore.common.page.PageQuery; // 分页归一。
import org.csu.mycartoonstore.common.page.PageResult; // 分页结果。
import org.springframework.dao.DuplicateKeyException; // 并发下 SKU 唯一键兜底。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 写操作同事务。

import java.math.BigDecimal; // 价格。
import java.math.RoundingMode; // 两位小数。
import java.util.ArrayList; // 组装下拉。
import java.util.List; // 列表。
import java.util.Set; // 状态白名单。
import java.util.regex.Pattern; // SKU 格式。

// M07-05：依据 docs/01 FR-A-03、docs/03 §2.3、docs/04 §3.3、docs/06 AC-25/AC-27。
// 负责：后台列表（含下架、可按一级/二级分类与状态筛选）、新增（默认 DRAFT）、编辑（版本冲突 409）。
// 不做：上下架与补货（M07-07）、删除（用下架代替）、页面渲染（M07-06）。
// 商品的所有写操作都必须维护 version：新增由数据库默认 0，编辑由 SQL 里 version+1。
@Service // 业务服务：只被后台控制器调用。
public class ProductAdminService {

    /** 新增商品的初始状态：完善后再显式上架 */
    private static final String STATUS_DRAFT = "DRAFT"; // 与数据库默认值一致。

    /** 状态白名单：列表筛选用 */
    private static final Set<String> STATUSES = Set.of("ALL", "DRAFT", "ON_SALE"); // 非法值 400。

    /** SKU 格式：1–40 位大写字母、数字与连字符 */
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9-]{1,40}$"); // 与 docs/04 §3.3 一致。

    /** 后台列表每页 10 条 */
    private static final int PAGE_SIZE = 10; // 与 docs/04 §4 的"其他列表缺省 10"一致。

    /** 名称上限：VARCHAR(120) */
    private static final int NAME_MAX = 120; // 去空白后判断。

    /** 简介上限：VARCHAR(500) */
    private static final int SUMMARY_MAX = 500; // 可空。

    /** 封面上限：VARCHAR(255) */
    private static final int COVER_MAX = 255; // 必填。

    /** 关键字上限：与前台列表一致 */
    private static final int KEYWORD_MAX = 120; // 超长 400。

    /** 价格上限：DECIMAL(12,2) */
    private static final BigDecimal PRICE_MAX = new BigDecimal("9999999999.99"); // 超出即拒绝。

    /** 初始库存上限：教学场景足够，避免误填把 INT 撑爆 */
    private static final int STOCK_MAX = 1_000_000; // 正整数校验。

    private final ProductMapper productMapper; // 商品读写。
    private final CategoryMapper categoryMapper; // 分类存在性与下拉。

    public ProductAdminService(ProductMapper productMapper, CategoryMapper categoryMapper) { // 构造器注入。
        this.productMapper = productMapper; // 保存商品 Mapper。
        this.categoryMapper = categoryMapper; // 保存分类 Mapper。
    }

    /**
     * 后台列表：含下架商品，可按分类（一级取子树）、名称、状态筛选，按 id 倒序。
     *
     * <p>所有参数在这里归一与校验：状态不在白名单、页码/页大小越界、关键字超长都给 400，
     * 不把非法值带进 SQL。</p>
     */
    public PageResult<AdminProductRow> search(String rawCategoryId, String rawKeyword, String rawStatus,
                                              String rawPage, String rawPageSize) {
        PageQuery pageQuery = PageQuery.of(rawPage, rawPageSize, PAGE_SIZE); // 页码与页大小。
        String status = parseStatus(rawStatus); // 白名单。
        Long categoryId = parseCategoryId(rawCategoryId); // 一级或二级。
        String keyword = parseKeyword(rawKeyword); // 转义 LIKE。

        AdminProductQuery query = new AdminProductQuery(); // 组装查询条件。
        query.setCategoryId(categoryId); // 分类过滤。
        query.setStatus(status); // 状态过滤。
        query.setKeyword(keyword); // 名称包含。
        query.setLimit(pageQuery.pageSize()); // 每页数量。
        query.setOffset(pageQuery.offset()); // 偏移量。

        List<AdminProductRow> rows = productMapper.selectAdminPage(query); // 当前页。
        long total = productMapper.countAdminPage(query); // 同条件总数。
        return PageResult.of(rows, total, pageQuery); // 交给页面。
    }

    /** 按 id 取商品：不存在 404。 */
    public Product detail(long id) {
        Product product = productMapper.selectById(id); // 主键查询。
        if (product == null) {
            throw BizException.notFound("商品不存在"); // 与越权同码，不泄露存在性。
        }
        return product; // 已存在的商品。
    }

    /**
     * 新增商品：状态固定 DRAFT，版本由数据库默认 0。
     *
     * <p>SKU 唯一由服务先查一次给出可读的 409；并发下由唯一键兜底。</p>
     */
    @Transactional // 校验 + 写入同一事务。
    public Product create(ProductForm form) {
        String sku = normalizeSku(form.getSku()); // 格式与唯一性。
        String name = normalizeName(form.getName()); // 1–120。
        Category category = requireLeafCategory(form.getCategoryId()); // 必须是二级分类。
        BigDecimal price = parsePrice(form.getPrice()); // >0、两位小数。
        int stock = parseStock(form.getStock(), 0); // 新增时的初始库存。
        String summary = normalizeSummary(form.getSummary()); // 可空 ≤500。
        String coverImage = normalizeCover(form.getCoverImage()); // 必填 ≤255。
        int sortOrder = normalizeSort(form.getSortOrder()); // 非负，缺省 0。

        if (skuExists(sku)) { // 先查一次，给出可读文案。
            throw BizException.conflict("SKU 已存在"); // 409。
        }

        Product product = new Product(); // 新商品。
        product.setSku(sku); // 创建后不可改。
        product.setName(name); // 名称。
        product.setCategoryId(category.getId()); // 挂在二级分类上。
        product.setPrice(price); // 价格。
        product.setStock(stock); // 初始库存。
        product.setSummary(summary); // 简介。
        product.setDescription(form.getDescription()); // 描述：纯文本，模板转义。
        product.setCoverImage(coverImage); // 封面路径。
        product.setSortOrder(sortOrder); // 排序。
        product.setStatus(STATUS_DRAFT); // 新增默认下架，完善后显式上架。
        try {
            productMapper.insert(product); // version 留给数据库默认 0。
        } catch (DuplicateKeyException e) { // 并发下两个请求同时通过查重。
            throw BizException.conflict("SKU 已存在"); // 交回页面刷新重试。
        }
        return product; // 返回带自增 id 的实体。
    }

    /**
     * 编辑商品：只改资料与价格，不改 SKU、库存、状态。
     *
     * <p>版本一致才写入（SQL 里 version+1）；影响 0 行说明别处已改过，返回 409 让页面刷新，
     * 不覆盖别人刚保存的库存或价格（AC-27）。</p>
     */
    @Transactional // 校验 + 条件更新同一事务。
    public Product update(long id, ProductForm form) {
        Product existing = detail(id); // 不存在 404。
        String name = normalizeName(form.getName()); // 名称。
        Category category = requireLeafCategory(form.getCategoryId()); // 分类必须是二级。
        BigDecimal price = parsePrice(form.getPrice()); // 价格。
        String summary = normalizeSummary(form.getSummary()); // 简介。
        String coverImage = normalizeCover(form.getCoverImage()); // 封面。
        int sortOrder = normalizeSort(form.getSortOrder()); // 排序。
        long version = parseVersion(form.getVersion()); // 隐藏域带回的版本。

        Product update = new Product(); // 只装要改的列。
        update.setId(id); // 条件里的 id。
        update.setVersion(version); // 条件里的版本。
        update.setName(name); // 名称。
        update.setCategoryId(category.getId()); // 分类。
        update.setPrice(price); // 价格。
        update.setSummary(summary); // 简介。
        update.setDescription(form.getDescription()); // 描述：允许清空。
        update.setCoverImage(coverImage); // 封面。
        update.setSortOrder(sortOrder); // 排序。
        int rows = productMapper.updateProfile(update); // WHERE id=? AND version=?
        if (rows == 0) { // 版本已被别人改掉。
            throw BizException.conflict("数据已发生变化，请刷新后重试"); // 409。
        }
        return existing; // 调用方只需提示成功并回列表。
    }

    /** 编辑页回填：价格转成两位小数字符串，库存不放进表单（编辑页库存只读）。 */
    public ProductForm form(long id) {
        Product product = detail(id); // 不存在 404。
        ProductForm form = new ProductForm(); // 复用同一个表单类型。
        form.setId(product.getId()); // 主键。
        form.setSku(product.getSku()); // 只读展示。
        form.setName(product.getName()); // 可改。
        form.setCategoryId(product.getCategoryId()); // 可改。
        form.setPrice(product.getPrice() == null ? "" : product.getPrice().toPlainString()); // 原样回显两位小数。
        form.setStock(String.valueOf(product.getStock())); // 只读展示，不参与提交。
        form.setSummary(product.getSummary()); // 可空。
        form.setDescription(product.getDescription()); // 可空。
        form.setCoverImage(product.getCoverImage()); // 可改。
        form.setSortOrder(product.getSortOrder()); // 可改。
        form.setVersion(product.getVersion()); // 隐藏域带回。
        return form; // 交给模板。
    }

    /** 新增页的初始值：状态 DRAFT、排序 0、库存 0。 */
    public ProductForm blankForm() {
        ProductForm form = new ProductForm(); // 空表单。
        form.setStock("0"); // 初始库存缺省 0。
        form.setSortOrder(0); // 排序缺省 0。
        return form; // 交给模板。
    }

    /** 商品表单的分类下拉：只列二级分类，显示「作品 / 品类」。 */
    public List<CategoryOption> categoryOptions() {
        List<Category> all = allCategories(); // 一次取全部分类。
        List<CategoryOption> options = new ArrayList<>(); // 结果。
        for (Category top : all) { // 外层遍历一级。
            if (top.getParentId() != null) {
                continue; // 二级在内层跟随父级。
            }
            for (Category child : all) { // 内层找它的二级。
                if (top.getId().equals(child.getParentId())) { // 同一父级。
                    options.add(new CategoryOption(child.getId(), top.getName() + " / " + child.getName())); // 提交二级 id。
                }
            }
        }
        return options; // 顺序与分类排序一致。
    }

    /** 列表筛选的分类下拉：一级与二级都列出来，一级表示"整个作品"。 */
    public List<CategoryOption> filterOptions() {
        List<Category> all = allCategories(); // 同一份数据。
        List<CategoryOption> options = new ArrayList<>(); // 结果。
        for (Category top : all) { // 外层一级。
            if (top.getParentId() != null) {
                continue;
            }
            options.add(new CategoryOption(top.getId(), top.getName())); // 一级：取该作品全部商品。
            for (Category child : all) { // 内层二级。
                if (top.getId().equals(child.getParentId())) { // 同一父级。
                    options.add(new CategoryOption(child.getId(), "　" + child.getName())); // 缩进显示。
                }
            }
        }
        return options; // 交给筛选表单。
    }

    // ---------------------------------------------------------------- 校验

    /** 分类必须存在且是二级分类：商品只能挂在品类下。 */
    private Category requireLeafCategory(Long categoryId) {
        if (categoryId == null) { // 必填。
            throw BizException.badRequest("所属分类不能为空"); // 400。
        }
        Category category = categoryMapper.selectById(categoryId); // 主键查询。
        if (category == null || category.getParentId() == null) { // 不存在或是一级。
            throw BizException.badRequest("所属分类必须是二级分类"); // 400。
        }
        return category; // 已确认的二级分类。
    }

    /** SKU：去空白、大写、1–40 位。 */
    private String normalizeSku(String raw) {
        String sku = raw == null ? "" : raw.trim(); // 容忍首尾空格。
        if (sku.isEmpty()) {
            throw BizException.badRequest("SKU 不能为空"); // 必填。
        }
        if (!SKU.matcher(sku).matches()) { // 与文档的字符集一致。
            throw BizException.badRequest("SKU 只能使用大写字母、数字与连字符，1–40 位");
        }
        return sku; // 通过校验。
    }

    /** 名称：去空白、1–120。 */
    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim(); // 容忍首尾空格。
        if (name.isEmpty()) {
            throw BizException.badRequest("商品名不能为空"); // 必填。
        }
        if (name.length() > NAME_MAX) {
            throw BizException.badRequest("商品名不能超过 " + NAME_MAX + " 个字符"); // 与列宽一致。
        }
        return name; // 通过校验。
    }

    /** 简介：可空，去空白后 ≤500。 */
    private String normalizeSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null; // 空简介按不填处理。
        }
        String summary = raw.trim(); // 去掉首尾空白。
        if (summary.length() > SUMMARY_MAX) {
            throw BizException.badRequest("简介不能超过 " + SUMMARY_MAX + " 个字符"); // 400。
        }
        return summary; // 通过校验。
    }

    /** 封面：必填、≤255。 */
    private String normalizeCover(String raw) {
        String cover = raw == null ? "" : raw.trim(); // 容忍首尾空格。
        if (cover.isEmpty()) {
            throw BizException.badRequest("封面路径不能为空"); // 必填。
        }
        if (cover.length() > COVER_MAX) {
            throw BizException.badRequest("封面路径不能超过 " + COVER_MAX + " 个字符"); // 400。
        }
        return cover; // 通过校验。
    }

    /** 排序：空按 0，负数拒绝。 */
    private int normalizeSort(Integer raw) {
        if (raw == null) {
            return 0; // 缺省。
        }
        if (raw < 0) {
            throw BizException.badRequest("排序不能为负数"); // 与文档一致。
        }
        return raw; // 合法值。
    }

    /** 价格：数字、>0、最多两位小数、不超 DECIMAL(12,2)。 */
    private BigDecimal parsePrice(String raw) {
        String text = raw == null ? "" : raw.trim(); // 容忍首尾空格。
        if (text.isEmpty()) {
            throw BizException.badRequest("价格不能为空"); // 必填。
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text); // 交给 BigDecimal 解析，避免 double 精度问题。
        } catch (NumberFormatException e) {
            throw BizException.badRequest("价格必须是数字"); // 400。
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("价格必须大于 0"); // 与 CHECK 约束一致。
        }
        if (value.scale() > 2) {
            throw BizException.badRequest("价格最多两位小数"); // 不做静默舍入。
        }
        if (value.compareTo(PRICE_MAX) > 0) {
            throw BizException.badRequest("价格超出可存储范围"); // DECIMAL(12,2)。
        }
        return value.setScale(2, RoundingMode.UNNECESSARY); // 统一两位小数，数据库列也是两位。
    }

    /** 库存：空按默认值，非负整数，不超上限。 */
    private int parseStock(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue; // 新增页缺省 0。
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim()); // 只接受整数。
        } catch (NumberFormatException e) {
            throw BizException.badRequest("库存必须是整数"); // 400。
        }
        if (value < 0) {
            throw BizException.badRequest("库存不能为负数"); // 与 CHECK 一致。
        }
        if (value > STOCK_MAX) {
            throw BizException.badRequest("库存不能超过 " + STOCK_MAX); // 防误填。
        }
        return value; // 合法值。
    }

    /** 版本号：编辑时必须带，缺了就不允许保存（否则无法发现并发覆盖）。 */
    private long parseVersion(Long raw) {
        if (raw == null || raw < 0) {
            throw BizException.badRequest("缺少版本号，请刷新后重试"); // 400。
        }
        return raw; // 合法版本。
    }

    /** 状态白名单：空按 ALL。 */
    private String parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ALL"; // 不筛选。
        }
        String status = raw.trim(); // 去掉空白。
        if (!STATUSES.contains(status)) {
            throw BizException.badRequest("商品状态不合法"); // 400。
        }
        return status; // ALL / DRAFT / ON_SALE。
    }

    /** 分类 id：空表示不过滤；格式错误或非正数 400。 */
    private Long parseCategoryId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null; // 不过滤。
        }
        try {
            long value = Long.parseLong(raw.trim()); // 只接受整数。
            if (value <= 0) {
                throw BizException.badRequest("分类参数格式不正确"); // 0 与负数无效。
            }
            return value; // 合法 id。
        } catch (NumberFormatException e) {
            throw BizException.badRequest("分类参数格式不正确"); // 400。
        }
    }

    /** 关键字：去空白、超长 400、通配符转义。 */
    private String parseKeyword(String raw) {
        if (raw == null) {
            return null; // 不搜索。
        }
        String keyword = raw.trim(); // 去首尾空白。
        if (keyword.isEmpty()) {
            return null; // 等价于不搜索。
        }
        if (keyword.length() > KEYWORD_MAX) {
            throw BizException.badRequest("搜索关键字不能超过 " + KEYWORD_MAX + " 个字符"); // 400。
        }
        return CatalogService.escapeLike(keyword); // 复用前台列表的转义规则。
    }

    /** SKU 是否已存在：新增时查重。 */
    private boolean skuExists(String sku) {
        return productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSku, sku)) > 0; // 唯一键不区分大小写，这里也按库比较。
    }

    /** 全部分类：按父级、排序、id 排好，供下拉与筛选复用。 */
    private List<Category> allCategories() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getParentId) // 一级在前。
                .orderByAsc(Category::getSortOrder) // 同级按排序。
                .orderByAsc(Category::getId)); // 稳定顺序。
    }
}
