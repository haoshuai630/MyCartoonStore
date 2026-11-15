package org.csu.mycartoonstore.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.csu.mycartoonstore.catalog.dto.ProductDetail;
import org.csu.mycartoonstore.catalog.dto.ProductQuery;
import org.csu.mycartoonstore.catalog.dto.ProductSearchCommand;
import org.csu.mycartoonstore.catalog.entity.Category;
import org.csu.mycartoonstore.catalog.entity.Product;
import org.csu.mycartoonstore.catalog.entity.ProductImage;
import org.csu.mycartoonstore.catalog.mapper.CategoryMapper;
import org.csu.mycartoonstore.catalog.mapper.ProductImageMapper;
import org.csu.mycartoonstore.catalog.mapper.ProductMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.page.PageQuery;
import org.csu.mycartoonstore.common.page.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 商品浏览服务：列表查询、商品详情、分类树。
 *
 * <p>所有请求参数在这里归一与校验：非法枚举转 400，不存在或下架转 404。
 * Controller 只负责把表单原样传进来，不自己做判断。</p>
 */
@Service
public class CatalogService {

    /** 商品类型白名单：ALL 表示不过滤 */
    private static final Set<String> TYPES = Set.of("ALL", "COMIC", "MERCH");

    /** 排序白名单，与 ProductMapper.xml 的 choose 分支一一对应 */
    private static final Set<String> SORTS = Set.of("DEFAULT", "PRICE_ASC", "PRICE_DESC", "NEWEST");

    /** 商品列表默认每页 6 条 */
    private static final int DEFAULT_PAGE_SIZE = 6;

    /** 关键字最大长度 */
    private static final int KEYWORD_MAX_LENGTH = 120;

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;

    public CatalogService(ProductMapper productMapper,
                          CategoryMapper categoryMapper,
                          ProductImageMapper productImageMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.productImageMapper = productImageMapper;
    }

    /**
     * 列表查询：校验参数 → 查数据 → 包装分页。
     */
    public PageResult<Product> search(ProductSearchCommand command) {
        PageQuery pageQuery = PageQuery.of(command.getPage(), command.getPageSize(), DEFAULT_PAGE_SIZE);
        ProductQuery query = buildQuery(command, pageQuery);

        List<Product> items = productMapper.selectPage(query);
        long total = productMapper.countPage(query);
        return PageResult.of(items, total, pageQuery);
    }

    /**
     * 商品详情：下架与不存在都返回 404，避免泄露"存在但下架"的信息。
     */
    public ProductDetail detail(long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw BizException.notFound("商品不存在或已下架");
        }

        Category leaf = categoryMapper.selectById(product.getCategoryId());
        if (leaf == null) {
            throw BizException.notFound("商品分类不存在");
        }
        Category top = leaf.getParentId() == null ? leaf : categoryMapper.selectById(leaf.getParentId());

        List<ProductImage> images = productImageMapper.selectList(
                Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getSortOrder)
                        .orderByAsc(ProductImage::getId));

        return new ProductDetail(product, top, leaf, images);
    }

    /** 首页「新品上架」：上架商品按录入时间倒序，取 limit 条 */
    public List<Product> newest(int limit) {
        return productMapper.selectNewest(limit);
    }

    /** 首页「精选推荐」：上架商品按 sort_order 升序，取 limit 条 */
    public List<Product> featured(int limit) {
        return productMapper.selectFeatured(limit);
    }

    /** 作品墙：每部作品一行，带品类数与在售商品数 */
    public java.util.List<org.csu.mycartoonstore.catalog.dto.WorkSummary> works() {
        return categoryMapper.selectWorkSummaries();
    }

    /**
     * 作品页头部：只接受一级分类；二级分类或不存在都按 404 处理。
     */
    public org.csu.mycartoonstore.catalog.dto.WorkDetail work(long id) {
        Category work = categoryMapper.selectById(id);
        if (work == null || work.getParentId() != null) {
            throw BizException.notFound("作品不存在");
        }
        return new org.csu.mycartoonstore.catalog.dto.WorkDetail(
                work, childrenOf(id), categoryMapper.countOnSaleByWork(id));
    }

    /** 一级分类（作品），按 sort_order、id 升序 */
    public List<Category> topCategories() {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery()
                        .isNull(Category::getParentId)
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId));
    }

    /** 某个一级分类下的二级分类 */
    public List<Category> childrenOf(long parentId) {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery()
                        .eq(Category::getParentId, parentId)
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId));
    }

    /** 全部分类，供列表页侧栏一次取完 */
    public List<Category> allCategories() {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getParentId)
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId));
    }

    // ---------------------------------------------------------------- 参数归一

    /** 把原始字符串参数归一成 Mapper 能用的查询对象；非法值在这里转 400。 */
    private ProductQuery buildQuery(ProductSearchCommand command, PageQuery pageQuery) {
        ProductQuery query = new ProductQuery();
        query.setCategoryId(parseCategoryId(command.getCategoryId()));
        query.setWorkId(parseCategoryId(command.getWorkId()));
        query.setProductType(parseEnum(command.getProductType(), "ALL", TYPES, "商品类型"));
        query.setSort(parseEnum(command.getSort(), "DEFAULT", SORTS, "排序方式"));
        query.setKeyword(escapeLike(parseKeyword(command.getKeyword())));
        query.setLimit(pageQuery.pageSize());
        query.setOffset(pageQuery.offset());
        return query;
    }

    /** 分类 id 只接受正整数；空串表示不过滤，格式错误直接 400。 */
    private Long parseCategoryId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw BizException.badRequest("分类参数格式不正确");
            }
            return value;
        } catch (NumberFormatException e) {
            throw BizException.badRequest("分类参数格式不正确");
        }
    }

    /** 白名单校验：缺省用默认值，不在白名单内一律 400，不做静默降级。 */
    private String parseEnum(String raw, String defaultValue, Set<String> allowed, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String value = raw.trim();
        if (!allowed.contains(value)) {
            throw BizException.badRequest(fieldName + "取值不合法");
        }
        return value;
    }

    /** 关键字去首尾空白；空串等价于不搜索；超长拒绝而不是截断。 */
    private String parseKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > KEYWORD_MAX_LENGTH) {
            throw BizException.badRequest("搜索关键字不能超过 " + KEYWORD_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /**
     * 把 LIKE 的通配符转义成普通字符：用户输入 50% 只匹配包含 "50%" 的商品，
     * 不会变成"以 50 开头"的通配查询。MySQL 的 LIKE 默认转义符是反斜杠。
     */
    static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
