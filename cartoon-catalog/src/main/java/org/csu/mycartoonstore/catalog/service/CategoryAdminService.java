package org.csu.mycartoonstore.catalog.service; // 分类维护：两级树的增删改与引用校验（后台专用）。

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 条件构造：同级同名要按父级分支。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 条件构造。
import org.csu.mycartoonstore.catalog.dto.CategoryForm; // 表单。
import org.csu.mycartoonstore.catalog.dto.CategoryRow; // 列表行。
import org.csu.mycartoonstore.catalog.dto.CategoryUsage; // 分类商品计数。
import org.csu.mycartoonstore.catalog.entity.Category; // 分类实体。
import org.csu.mycartoonstore.catalog.entity.Product; // 商品实体：删除时查引用。
import org.csu.mycartoonstore.catalog.mapper.CategoryMapper; // 分类读写。
import org.csu.mycartoonstore.catalog.mapper.ProductMapper; // 商品计数（删除校验用）。
import org.csu.mycartoonstore.common.exception.BizException; // 400/404/409。
import org.springframework.dao.DuplicateKeyException; // 并发下唯一键兜底。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 写操作同事务。

import java.util.ArrayList; // 组装列表。
import java.util.HashMap; // 计数索引。
import java.util.List; // 列表。
import java.util.Map; // 父名与计数。
import java.util.regex.Pattern; // 编码格式。

// M07-03：依据 docs/01 FR-A-02、docs/03 §2.2、docs/04 §3.2、docs/05 §4.9。
// 负责：两级树查询（带子分类数与商品数）、新增一级/二级、编辑名称与排序、删除前的引用校验。
// 不做：编码与父级的修改（创建后不可变）、三级分类、商品维护（M07-05）。
// 所有写操作都在这里校验；Controller 只做参数绑定与跳转（M07-04）。
@Service // 业务服务：只被后台控制器调用。
public class CategoryAdminService {

    /** 编码格式：1–32 位大写字母、数字与连字符 */
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9-]{1,32}$"); // 小写留空，由用户改成大写。

    /** 名称最大长度：与 VARCHAR(50) 一致 */
    private static final int NAME_MAX = 50; // 去空白后判断。

    /** 二级分类编码允许的后缀：决定商品类型（漫画 / 周边） */
    private static final String COMIC_SUFFIX = "-COMIC"; // 漫画。

    /** 二级分类编码的另一个后缀 */
    private static final String MERCH_SUFFIX = "-MERCH"; // 周边。

    /** 一级层级值 */
    private static final int LEVEL_TOP = 1; // 作品。

    /** 二级层级值 */
    private static final int LEVEL_CHILD = 2; // 品类。

    private final CategoryMapper categoryMapper; // 分类读写。
    private final ProductMapper productMapper; // 商品计数。

    public CategoryAdminService(CategoryMapper categoryMapper, ProductMapper productMapper) { // 构造器注入。
        this.categoryMapper = categoryMapper; // 保存分类 Mapper。
        this.productMapper = productMapper; // 保存商品 Mapper。
    }

    /**
     * 两级树：一级在前、其下二级紧随，带子分类数与商品数。
     *
     * <p>两次查询完成：一次取全部分类，一次按分类分组取商品数；
     * 页面不再逐行查库，也不怕分类数量增长。</p>
     */
    public List<CategoryRow> tree() {
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getParentId) // 一级（null）排最前。
                .orderByAsc(Category::getSortOrder) // 同级按排序。
                .orderByAsc(Category::getId)); // 同序按 id 稳定。
        Map<Long, Long> productCounts = productCounts(); // 商品数索引。
        Map<Long, List<Category>> byParent = groupByParent(all); // 父 → 子。
        Map<Long, String> names = new HashMap<>(); // id → 名称，供子行显示父名。
        for (Category category : all) {
            names.put(category.getId(), category.getName()); // 一次遍历建索引。
        }

        List<CategoryRow> rows = new ArrayList<>(all.size()); // 结果行。
        for (Category top : all) {
            if (top.getParentId() != null) {
                continue; // 二级在后面跟着父级一起加。
            }
            List<Category> children = byParent.getOrDefault(top.getId(), List.of()); // 该作品下的品类。
            rows.add(toRow(top, null, LEVEL_TOP, children.size(), productCounts)); // 一级行。
            for (Category child : children) {
                rows.add(toRow(child, names.get(child.getParentId()), LEVEL_CHILD, 0, productCounts)); // 二级行紧随。
            }
        }
        return rows; // 扁平化后的树：模板只需按 level 缩进。
    }

    /** 新增分类：一级只要编码名称，二级还要父级存在且编码后缀合法。 */
    @Transactional // 校验 + 写入同一事务。
    public Category create(CategoryForm form) {
        String code = normalizeCode(form.getCode()); // 格式与去空白。
        String name = normalizeName(form.getName()); // 长度与去空白。
        int sortOrder = normalizeSort(form.getSortOrder()); // 默认 0，负数拒绝。
        Long parentId = form.getParentId(); // 一级为空。
        validateParentAndSuffix(parentId, code); // 层级与后缀规则。

        if (codeExists(code, null)) { // 编码全局唯一（库不区分大小写）。
            throw BizException.conflict("编码已存在"); // 409 而不是 500。
        }
        if (nameExists(parentId, name, null)) { // 同级同名唯一。
            throw BizException.conflict("同级已存在同名分类"); // 409。
        }

        Category category = new Category(); // 新分类。
        category.setCode(code); // 编码创建后不可改。
        category.setParentId(parentId); // 一级为空。
        category.setName(name); // 名称。
        category.setSortOrder(sortOrder); // 排序。
        try {
            categoryMapper.insert(category); // parent_key 是生成列，实体不写。
        } catch (DuplicateKeyException e) { // 并发下两个请求同时通过上面的查重。
            throw BizException.conflict("编码或同级名称已存在"); // 交回页面刷新重试。
        }
        return category; // 返回带自增 id 的实体。
    }

    /**
     * 编辑分类：只改名称与排序。
     *
     * <p>编码与父级创建后不可变（docs/01 FR-A-02），表单里即便带了也不采纳；
     * 这样历史商品与类型判定不会被一次改名改动打乱。</p>
     */
    @Transactional // 校验 + 更新同一事务。
    public Category update(long id, CategoryForm form) {
        Category existing = require(id); // 不存在直接 404。
        String name = normalizeName(form.getName()); // 只取名称。
        int sortOrder = normalizeSort(form.getSortOrder()); // 只取排序。
        if (nameExists(existing.getParentId(), name, id)) { // 同级同名唯一，排除自己。
            throw BizException.conflict("同级已存在同名分类"); // 409。
        }

        Category update = new Category(); // 只装要改的两列。
        update.setId(id); // 按主键更新。
        update.setName(name); // 名称。
        update.setSortOrder(sortOrder); // 排序。
        categoryMapper.updateById(update); // 非空字段才写，code/parent 不会被清空。
        return existing; // 返回原实体，Controller 只需跳转。
    }

    /** 删除分类：有子分类或有任意商品引用（含下架）都拒绝。 */
    @Transactional // 校验 + 删除同一事务。
    public void delete(long id) {
        require(id); // 不存在 404。
        long children = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, id)); // 直接子分类。
        if (children > 0) {
            throw BizException.conflict("该分类下还有子分类，不能删除"); // 一级分类通常卡这里。
        }
        long products = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, id)); // 任意状态的商品。
        if (products > 0) {
            throw BizException.conflict("该分类下还有商品，不能删除"); // 下架商品也算引用。
        }
        categoryMapper.deleteById(id); // 只删记录，不动历史订单快照。
    }

    /** 编辑页回显：把库里的值装进表单对象。 */
    public CategoryForm form(long id) {
        Category category = require(id); // 不存在 404。
        CategoryForm form = new CategoryForm(); // 复用同一个表单类型。
        form.setId(category.getId()); // 只读展示。
        form.setCode(category.getCode()); // 只读展示。
        form.setName(category.getName()); // 可改。
        form.setParentId(category.getParentId()); // 只读展示。
        form.setSortOrder(category.getSortOrder()); // 可改。
        return form; // 交给模板。
    }

    /** 新增表单的初始值：新增二级时带上父级 id。 */
    public CategoryForm blankForm(Long parentId) {
        CategoryForm form = new CategoryForm(); // 空表单。
        form.setParentId(parentId); // 一级为空，二级带父级。
        form.setSortOrder(0); // 缺省排序 0。
        return form; // 交给模板。
    }

    /** 一级分类：新增二级时给父级下拉/只读展示用 */
    public List<Category> topCategories() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .isNull(Category::getParentId) // 只取一级。
                .orderByAsc(Category::getSortOrder) // 与前台一致。
                .orderByAsc(Category::getId));
    }

    // ---------------------------------------------------------------- 校验

    /** 取分类或 404：编辑与删除前的统一入口。 */
    private Category require(long id) {
        Category category = categoryMapper.selectById(id); // 主键查询。
        if (category == null) {
            throw BizException.notFound("分类不存在"); // 与"没权限"同码，不泄露存在性。
        }
        return category; // 已存在的分类。
    }

    /** 编码：去空白、必须大写、1–32 位。 */
    private String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.trim(); // 容忍首尾空格。
        if (code.isEmpty()) {
            throw BizException.badRequest("编码不能为空"); // 必填。
        }
        if (!CODE.matcher(code).matches()) {
            throw BizException.badRequest("编码只能使用大写字母、数字与连字符，1–32 位"); // 不做静默大写化。
        }
        return code; // 通过校验的编码。
    }

    /** 名称：去空白、1–50 字符。 */
    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim(); // 容忍首尾空格。
        if (name.isEmpty()) {
            throw BizException.badRequest("名称不能为空"); // 必填。
        }
        if (name.length() > NAME_MAX) {
            throw BizException.badRequest("名称不能超过 " + NAME_MAX + " 个字符"); // 与列宽一致。
        }
        return name; // 通过校验的名称。
    }

    /** 排序：空按 0，负数拒绝。 */
    private int normalizeSort(Integer raw) {
        if (raw == null) {
            return 0; // 缺省。
        }
        if (raw < 0) {
            throw BizException.badRequest("排序不能为负数"); // 与文档"非负整数"一致。
        }
        return raw; // 合法值。
    }

    /** 二级分类必须挂在存在的一级下，且编码后缀合法；一级不接受后缀限制。 */
    private void validateParentAndSuffix(Long parentId, String code) {
        if (parentId == null) {
            return; // 一级分类：到此为止。
        }
        Category parent = categoryMapper.selectById(parentId); // 父级必须存在。
        if (parent == null || parent.getParentId() != null) {
            throw BizException.badRequest("父分类必须是一级分类"); // 禁止三级与自引用。
        }
        if (!code.endsWith(COMIC_SUFFIX) && !code.endsWith(MERCH_SUFFIX)) {
            throw BizException.badRequest("二级分类编码必须以 -COMIC 或 -MERCH 结尾"); // 类型判定唯一来源。
        }
    }

    /** 编码是否已被占用；编辑时排除自己。 */
    private boolean codeExists(String code, Long excludeId) {
        return categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getCode, code) // 库排序规则不区分大小写，这里也不额外处理。
                .ne(excludeId != null, Category::getId, excludeId)) > 0; // 编辑时排除自身。
    }

    /** 同级是否已有同名分类；一级之间比 parent_id IS NULL，二级之间比 parent_id。 */
    private boolean nameExists(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<Category> query = Wrappers.<Category>lambdaQuery(); // 条件按父级分支拼。
        if (parentId == null) {
            query.isNull(Category::getParentId); // 一级：彼此比较。
        } else {
            query.eq(Category::getParentId, parentId); // 二级：同一父下比较。
        }
        query.eq(Category::getName, name); // 名称相同。
        if (excludeId != null) {
            query.ne(Category::getId, excludeId); // 编辑时排除自身。
        }
        return categoryMapper.selectCount(query) > 0; // 命中即为重名。
    }

    /** 商品数：id → 数量。 */
    private Map<Long, Long> productCounts() {
        List<CategoryUsage> usage = productMapper.countByCategory(); // 一次分组查询。
        Map<Long, Long> counts = new HashMap<>(); // 结果索引。
        for (CategoryUsage item : usage) {
            counts.put(item.getCategoryId(), item.getProductCount()); // 只保留有商品的分类。
        }
        return counts; // 没有商品的分类取默认 0。
    }

    /** 按父级分组：parentId → 子分类列表（保持查询顺序）。 */
    private Map<Long, List<Category>> groupByParent(List<Category> all) {
        Map<Long, List<Category>> byParent = new HashMap<>(); // 分组结果。
        for (Category category : all) {
            if (category.getParentId() != null) { // 只看二级。
                byParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category); // 追加。
            }
        }
        return byParent; // 供一级行取子分类。
    }

    /** 组装一行：父名、层级、两个计数都来自本次查询。 */
    private CategoryRow toRow(Category category, String parentName, int level,
                              long childCount, Map<Long, Long> productCounts) {
        return new CategoryRow(category.getId(), category.getCode(), category.getName(),
                category.getParentId(), parentName, category.getSortOrder() == null ? 0 : category.getSortOrder(),
                level, childCount, productCounts.getOrDefault(category.getId(), 0L)); // 缺省计数为 0。
    }
}
