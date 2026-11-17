package org.csu.mycartoonstore.cart.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.csu.mycartoonstore.cart.dto.CartItemView;
import org.csu.mycartoonstore.cart.dto.CartLine;
import org.csu.mycartoonstore.cart.dto.CartSummary;
import org.csu.mycartoonstore.cart.entity.CartItem;
import org.csu.mycartoonstore.cart.mapper.CartItemMapper;
import org.csu.mycartoonstore.catalog.entity.Product;
import org.csu.mycartoonstore.catalog.mapper.ProductMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车服务：加车、改数量、移除、汇总。
 *
 * <p>所有规则集中在这里，Controller 只传原始表单值：
 * 数量 1–99、每车最多 20 种、加车与改量都校验<b>累加后</b>的数量与库存、
 * 改量与移除带 {@code version} 做旧页面检测、归属校验写进 SQL 的 WHERE。</p>
 *
 * <p>购物车不占库存、不存价格：页面上看到的价格与库存永远来自 {@code product} 当前值。</p>
 */
@Service
public class CartService {

    /** 每车最多 20 种 SKU（FR-C-01 §2） */
    public static final int MAX_KINDS = 20;

    /** 同一 SKU 合计 1–99，与数据库 CHECK 一致 */
    public static final int MAX_QUANTITY = 99;

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public CartService(CartItemMapper cartItemMapper, ProductMapper productMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
    }

    /**
     * 本人购物车汇总：失效行也返回，由调用方决定是提示还是拦住结算。
     */
    public CartSummary summary(long userId) {
        return CartSummary.of(lines(userId));
    }

    /** 本人购物车行，含失效行 */
    public List<CartLine> lines(long userId) {
        List<CartItemView> views = cartItemMapper.selectViewByUser(userId);
        return views.stream().map(CartLine::from).toList();
    }

    /** 页头徽标：全部条目数量之和（含失效条目） */
    public int badgeQuantity(long userId) {
        return cartItemMapper.sumQuantityByUser(userId);
    }

    /**
     * 加入购物车：同商品累加，累加后校验 99 上限与库存。
     *
     * <p>并发下两个会话同时加同一商品时，靠唯一键 {@code uk_cart_user_product} 兜底：
     * 后插入的一方拿到 {@link DuplicateKeyException}，再按"已存在则累加"重试一次。</p>
     */
    public void add(long userId, String productIdRaw, String quantityRaw) {
        long productId = parseId(productIdRaw, "商品编号");
        int quantity = parseQuantity(quantityRaw);
        Product product = requireOnSaleProduct(productId);

        CartItem existing = cartItemMapper.findByUserAndProduct(userId, productId);
        if (existing != null) {
            mergeInto(existing, quantity, product);
            return;
        }

        if (cartItemMapper.countByUser(userId) >= MAX_KINDS) {
            throw BizException.badRequest("购物车最多放 " + MAX_KINDS + " 种商品，请先移除不需要的");
        }
        ensureStock(quantity, product);

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setVersion(0L);
        try {
            cartItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 另一个会话刚好先插入：退化为"累加"，不把并发冲突报给用户
            mergeInto(cartItemMapper.findByUserAndProduct(userId, productId), quantity, product);
        }
    }

    /**
     * 改数量：归属、版本、库存三道检查都过了才写。
     */
    public void changeQuantity(long userId, String itemIdRaw, String quantityRaw, String versionRaw) {
        long itemId = parseId(itemIdRaw, "条目编号");
        int quantity = parseQuantity(quantityRaw);
        long version = parseVersion(versionRaw);

        CartItem item = requireOwnItem(userId, itemId);
        requireVersion(item, version);

        Product product = productMapper.selectById(item.getProductId());
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw BizException.conflict("商品已下架，请移除该条目");
        }
        ensureStock(quantity, product);

        int rows = cartItemMapper.updateQuantity(userId, itemId, quantity, version);
        if (rows == 0) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
    }

    /**
     * 移除条目：同样校验归属与版本，避免旧页面把别人或新数量的条目删掉。
     */
    public void remove(long userId, String itemIdRaw, String versionRaw) {
        long itemId = parseId(itemIdRaw, "条目编号");
        long version = parseVersion(versionRaw);

        CartItem item = requireOwnItem(userId, itemId);
        requireVersion(item, version);

        int rows = cartItemMapper.deleteByIdAndUser(userId, itemId, version);
        if (rows == 0) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
    }

    /**
     * 下单前锁住本人购物车并返回当前行。
     *
     * <p>先 {@code SELECT ... FOR UPDATE} 锁行，再读联表视图：
     * 同一辆车的并发提交会在这里排队，后到的事务拿到的是已提交后的状态。</p>
     */
    public List<CartLine> lockForCheckout(long userId) {
        cartItemMapper.selectItemsForUpdate(userId); // 锁住本人全部行，按 id 升序。
        return lines(userId); // 再读当前行（价格、库存、状态都是最新值）。
    }

    /**
     * 下单成功后删除本次核对的条目。
     *
     * @return 实际删除行数；调用方要求它等于本次条目数，否则回滚
     */
    public int removeCheckedItems(long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return 0; // 没有条目可删，交给调用方按冲突处理。
        }
        return cartItemMapper.deleteByIdsAndUser(userId, cartItemIds); // 归属条件仍在 SQL 里。
    }

    /**
     * 清掉所有用户购物车里指向该商品的条目。
     *
     * <p>后台删除商品前调用：{@code cart_item.product_id} 有外键指向商品，
     * 不先清条目就无法删除商品。返回实际删除行数，供接口提示"顺手清了几条"。</p>
     */
    public int clearProduct(long productId) {
        return cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getProductId, productId)); // 只按商品过滤：这是跨用户的维护动作。
    }

    // ---------------------------------------------------------------- 内部规则

    /** 商品必须存在且上架；下架与不存在都按 404，不泄露"存在但下架" */
    private Product requireOnSaleProduct(long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw BizException.notFound("商品不存在或已下架");
        }
        return product;
    }

    /**
     * 条目必须存在且属于本人：不存在与他人条目统一 404。
     *
     * <p>不返回 403，是因为 403 等于承认"这个条目 id 存在、只是不归你"；
     * 统一 404 连存在性也不暴露（AC-18 允许 403/404，这里选更严的 404）。</p>
     */
    private CartItem requireOwnItem(long userId, long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw BizException.notFound("购物车条目不存在，可能已被移除");
        }
        return item;
    }

    /** 旧页面带回过期版本号时拒绝，不覆盖其他会话改过的新数量 */
    private void requireVersion(CartItem item, long version) {
        if (item.getVersion() == null || item.getVersion() != version) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
    }

    /** 累加进已有条目：先算总数，再一次性校验上限与库存 */
    private void mergeInto(CartItem existing, int added, Product product) {
        if (existing == null) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
        int newQuantity = existing.getQuantity() + added;
        if (newQuantity > MAX_QUANTITY) {
            throw BizException.badRequest("同一商品最多 " + MAX_QUANTITY + " 件");
        }
        ensureStock(newQuantity, product);

        int rows = cartItemMapper.updateQuantity(
                existing.getUserId(), existing.getId(), newQuantity, existing.getVersion());
        if (rows == 0) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
    }

    /** 库存校验：购物车不占库存，但也不能放进买不到的数量 */
    private void ensureStock(int quantity, Product product) {
        int stock = product.getStock() == null ? 0 : product.getStock();
        if (stock <= 0) {
            throw BizException.badRequest("商品暂时缺货");
        }
        if (quantity > stock) {
            throw BizException.badRequest("库存不足，最多可购买 " + stock + " 件");
        }
    }

    // ---------------------------------------------------------------- 参数解析

    /** 表单里的 id：空、非数字、非正数一律 400，不静默当成 0 */
    private long parseId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest(fieldName + "不能为空");
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw BizException.badRequest(fieldName + "不正确");
            }
            return value;
        } catch (NumberFormatException e) {
            throw BizException.badRequest(fieldName + "不正确");
        }
    }

    /** 数量必须是 1–99 的整数：小数、0、负数、超上限都不写库 */
    private int parseQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("数量不能为空");
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest("数量必须是 1–99 的整数");
        }
        if (value < 1 || value > MAX_QUANTITY) {
            throw BizException.badRequest("数量必须是 1–99 的整数");
        }
        return value;
    }

    /** 版本号：缺失说明页面是旧的，直接要求刷新 */
    private long parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw BizException.conflict("购物车已变化，请刷新后重试");
            }
            return value;
        } catch (NumberFormatException e) {
            throw BizException.conflict("购物车已变化，请刷新后重试");
        }
    }
}
