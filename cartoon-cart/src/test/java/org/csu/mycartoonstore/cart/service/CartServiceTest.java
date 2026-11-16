package org.csu.mycartoonstore.cart.service;

import org.csu.mycartoonstore.cart.dto.CartItemView;
import org.csu.mycartoonstore.cart.dto.CartSummary;
import org.csu.mycartoonstore.cart.entity.CartItem;
import org.csu.mycartoonstore.cart.mapper.CartItemMapper;
import org.csu.mycartoonstore.catalog.entity.Product;
import org.csu.mycartoonstore.catalog.mapper.ProductMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车服务测试：累加、上限、库存、归属、版本冲突与汇总口径。
 *
 * <p>Mapper 全部 Mock，只验证 Service 的分支与抛出的错误码；
 * 真实 SQL 的归属与版本条件由集成测试覆盖。</p>
 */
class CartServiceTest {

    private static final long USER = 1L;

    private CartItemMapper cartItemMapper;
    private ProductMapper productMapper;
    private CartService service;

    @BeforeEach
    void setUp() {
        cartItemMapper = mock(CartItemMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new CartService(cartItemMapper, productMapper);
    }

    // ---------------------------------------------------------------- 加车

    @Test
    @DisplayName("首次加入：插入一条数量等于本次输入")
    void addInsertsNewItem() {
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 10, "ON_SALE"));
        when(cartItemMapper.findByUserAndProduct(USER, 9L)).thenReturn(null);
        when(cartItemMapper.countByUser(USER)).thenReturn(3);

        service.add(USER, "9", "2");

        verify(cartItemMapper).insert(any(CartItem.class));
    }

    @Test
    @DisplayName("同商品重复加入：按累加后的数量更新，而不是再插一条")
    void addAccumulatesExisting() {
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 10, "ON_SALE"));
        when(cartItemMapper.findByUserAndProduct(USER, 9L)).thenReturn(item(77L, USER, 9L, 2, 3L));
        when(cartItemMapper.updateQuantity(USER, 77L, 5, 3L)).thenReturn(1);

        service.add(USER, "9", "3");

        verify(cartItemMapper, never()).insert(any(CartItem.class));
        verify(cartItemMapper).updateQuantity(USER, 77L, 5, 3L);
    }

    @Test
    @DisplayName("数量 0 / 100 / 小数 / 非数字：400，且不碰数据库")
    void addRejectsInvalidQuantity() {
        for (String raw : List.of("0", "-1", "100", "1.5", "abc", "")) {
            assertThatThrownBy(() -> service.add(USER, "9", raw))
                    .as("数量 %s", raw)
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
        }
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    @DisplayName("累加后超过 99：400，原条目不动")
    void addRejectsAccumulatedOverLimit() {
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 200, "ON_SALE"));
        when(cartItemMapper.findByUserAndProduct(USER, 9L)).thenReturn(item(77L, USER, 9L, 98, 0L));

        assertThatThrownBy(() -> service.add(USER, "9", "2"))
                .hasMessageContaining("99");
        verify(cartItemMapper, never()).updateQuantity(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("累加后超过库存：400，提示可购买数量")
    void addRejectsAccumulatedOverStock() {
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 3, "ON_SALE"));
        when(cartItemMapper.findByUserAndProduct(USER, 9L)).thenReturn(item(77L, USER, 9L, 2, 0L));

        assertThatThrownBy(() -> service.add(USER, "9", "2"))
                .hasMessageContaining("最多可购买 3 件");
        verify(cartItemMapper, never()).updateQuantity(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("商品不存在或已下架：404")
    void addRejectsMissingOrDraftProduct() {
        when(productMapper.selectById(9L)).thenReturn(null);
        when(productMapper.selectById(10L)).thenReturn(product(10L, "28.00", 10, "DRAFT"));

        assertThatThrownBy(() -> service.add(USER, "9", "1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.add(USER, "10", "1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("已有 20 种商品：拒绝第 21 种")
    void addRejectsWhenKindsFull() {
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 10, "ON_SALE"));
        when(cartItemMapper.findByUserAndProduct(USER, 9L)).thenReturn(null);
        when(cartItemMapper.countByUser(USER)).thenReturn(CartService.MAX_KINDS);

        assertThatThrownBy(() -> service.add(USER, "9", "1"))
                .hasMessageContaining("20 种");
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    @DisplayName("并发下插入撞唯一键：退化为累加，不报 500")
    void addFallsBackToAccumulateOnDuplicateKey() {
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 10, "ON_SALE"));
        when(cartItemMapper.findByUserAndProduct(USER, 9L))
                .thenReturn(null, item(77L, USER, 9L, 1, 0L));
        when(cartItemMapper.insert(any(CartItem.class)))
                .thenThrow(new DuplicateKeyException("uk_cart_user_product"));
        when(cartItemMapper.updateQuantity(USER, 77L, 2, 0L)).thenReturn(1);

        service.add(USER, "9", "1");

        verify(cartItemMapper).updateQuantity(USER, 77L, 2, 0L);
    }

    // ---------------------------------------------------------------- 改数量

    @Test
    @DisplayName("改数量：校验通过后带旧版本写库")
    void changeQuantityWritesWithVersion() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, USER, 9L, 2, 4L));
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 10, "ON_SALE"));
        when(cartItemMapper.updateQuantity(USER, 77L, 6, 4L)).thenReturn(1);

        service.changeQuantity(USER, "77", "6", "4");

        verify(cartItemMapper).updateQuantity(USER, 77L, 6, 4L);
    }

    @Test
    @DisplayName("改他人条目：统一 404，不暴露条目是否存在")
    void changeQuantityRejectsOtherUser() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, 2L, 9L, 2, 0L));

        assertThatThrownBy(() -> service.changeQuantity(USER, "77", "3", "0"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(cartItemMapper, never()).updateQuantity(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("旧版本提交：409，不覆盖新数量")
    void changeQuantityRejectsStaleVersion() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, USER, 9L, 2, 5L));

        assertThatThrownBy(() -> service.changeQuantity(USER, "77", "3", "4"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(cartItemMapper, never()).updateQuantity(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("改成超过库存：400，不写库")
    void changeQuantityRejectsOverStock() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, USER, 9L, 2, 0L));
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 3, "ON_SALE"));

        assertThatThrownBy(() -> service.changeQuantity(USER, "77", "4", "0"))
                .hasMessageContaining("最多可购买 3 件");
        verify(cartItemMapper, never()).updateQuantity(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("条目对应商品已下架：409，先移除再买")
    void changeQuantityRejectsDraftProduct() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, USER, 9L, 2, 0L));
        when(productMapper.selectById(9L)).thenReturn(product(9L, "28.00", 10, "DRAFT"));

        assertThatThrownBy(() -> service.changeQuantity(USER, "77", "2", "0"))
                .hasMessageContaining("已下架");
    }

    // ---------------------------------------------------------------- 移除

    @Test
    @DisplayName("移除本人条目：带旧版本删除")
    void removeDeletesOwnItem() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, USER, 9L, 2, 1L));
        when(cartItemMapper.deleteByIdAndUser(USER, 77L, 1L)).thenReturn(1);

        service.remove(USER, "77", "1");

        verify(cartItemMapper).deleteByIdAndUser(USER, 77L, 1L);
    }

    @Test
    @DisplayName("移除时版本已变：409，不删除")
    void removeRejectsStaleVersion() {
        when(cartItemMapper.selectById(77L)).thenReturn(item(77L, USER, 9L, 2, 2L));

        assertThatThrownBy(() -> service.remove(USER, "77", "1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(cartItemMapper, never()).deleteByIdAndUser(anyLong(), anyLong(), anyLong());
    }

    // ---------------------------------------------------------------- 汇总

    @Test
    @DisplayName("汇总：徽标含失效行，金额只算可用行")
    void summaryCountsBadgeAndOnlyValidAmount() {
        when(cartItemMapper.selectViewByUser(USER)).thenReturn(List.of(
                view(1L, 9L, 2, "28.00", 10, "ON_SALE", 0L),
                view(2L, 10L, 1, "30.00", 0, "ON_SALE", 0L),
                view(3L, 11L, 3, "50.00", 5, "DRAFT", 0L)));

        CartSummary summary = service.summary(USER);

        assertThat(summary.getBadgeQuantity()).isEqualTo(6);
        assertThat(summary.getValidQuantity()).isEqualTo(2);
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("56.00");
        assertThat(summary.getInvalidCount()).isEqualTo(2);
        assertThat(summary.isAllAvailable()).isFalse();
        assertThat(summary.isSettleable()).isFalse();
    }

    @Test
    @DisplayName("汇总：空车徽标 0、总额 0.00，且不可结算")
    void summaryOfEmptyCart() {
        when(cartItemMapper.selectViewByUser(USER)).thenReturn(List.of());

        CartSummary summary = service.summary(USER);

        assertThat(summary.isEmpty()).isTrue();
        assertThat(summary.getBadgeQuantity()).isZero();
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.isSettleable()).isFalse();
    }

    @Test
    @DisplayName("徽标直接取 SQL 求和结果")
    void badgeDelegatesToMapper() {
        when(cartItemMapper.sumQuantityByUser(USER)).thenReturn(7);

        assertThat(service.badgeQuantity(USER)).isEqualTo(7);
    }

    // ---------------------------------------------------------------- 夹具

    private Product product(long id, String price, int stock, String status) {
        Product p = new Product();
        p.setId(id);
        p.setName("测试商品" + id);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setStatus(status);
        return p;
    }

    private CartItem item(long id, long userId, long productId, int quantity, long version) {
        CartItem i = new CartItem();
        i.setId(id);
        i.setUserId(userId);
        i.setProductId(productId);
        i.setQuantity(quantity);
        i.setVersion(version);
        return i;
    }

    private CartItemView view(long id, long productId, int quantity, String price, int stock,
                              String status, long version) {
        CartItemView v = new CartItemView();
        v.setId(id);
        v.setProductId(productId);
        v.setProductName("测试商品" + productId);
        v.setCoverImage("images/products/T/cover.svg");
        v.setCategoryName("单行本");
        v.setPrice(new BigDecimal(price));
        v.setQuantity(quantity);
        v.setStock(stock);
        v.setProductStatus(status);
        v.setVersion(version);
        return v;
    }
}
