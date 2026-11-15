package org.csu.mycartoonstore.catalog.service;

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
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.csu.mycartoonstore.common.page.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ProductImageMapper productImageMapper;
    @InjectMocks
    private CatalogService catalogService;

    // ---------------------------------------------------------------- 列表参数

    @Test
    @DisplayName("空参数：类型 ALL、排序 DEFAULT、第 1 页、每页 6 条")
    void searchUsesDefaults() {
        when(productMapper.selectPage(any())).thenReturn(List.of());
        when(productMapper.countPage(any())).thenReturn(0L);

        PageResult<Product> result = catalogService.search(new ProductSearchCommand());

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(6);
        assertThat(result.totalPages()).isZero();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("非法商品类型返回 400")
    void invalidProductType() {
        ProductSearchCommand command = new ProductSearchCommand();
        command.setProductType("PET");

        assertThatThrownBy(() -> catalogService.search(command))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("非法排序值返回 400")
    void invalidSort() {
        ProductSearchCommand command = new ProductSearchCommand();
        command.setSort("PRICE");

        assertThatThrownBy(() -> catalogService.search(command))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("关键字超过 120 字符返回 400")
    void keywordTooLong() {
        ProductSearchCommand command = new ProductSearchCommand();
        command.setKeyword("星".repeat(121));

        assertThatThrownBy(() -> catalogService.search(command))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("关键字里的 % _ 被转义，不会变成通配符")
    void keywordIsEscaped() {
        when(productMapper.selectPage(any())).thenReturn(List.of());
        when(productMapper.countPage(any())).thenReturn(0L);

        ProductSearchCommand command = new ProductSearchCommand();
        command.setKeyword("50%_off");
        catalogService.search(command);

        ArgumentCaptor<ProductQuery> captor = ArgumentCaptor.forClass(ProductQuery.class);
        org.mockito.Mockito.verify(productMapper).selectPage(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("50\\%\\_off");
    }

    @Test
    @DisplayName("页码 0 归一为 1，第 3 页偏移 12")
    void pageIsNormalized() {
        when(productMapper.selectPage(any())).thenReturn(List.of());
        when(productMapper.countPage(any())).thenReturn(30L);

        ProductSearchCommand first = new ProductSearchCommand();
        first.setPage("0");
        catalogService.search(first);

        ProductSearchCommand third = new ProductSearchCommand();
        third.setPage("3");
        PageResult<Product> page3 = catalogService.search(third);

        assertThat(page3.page()).isEqualTo(3);
        ArgumentCaptor<ProductQuery> captor = ArgumentCaptor.forClass(ProductQuery.class);
        org.mockito.Mockito.verify(productMapper, org.mockito.Mockito.atLeastOnce()).selectPage(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getOffset()).isEqualTo(12L);
    }

    // ---------------------------------------------------------------- 详情

    @Test
    @DisplayName("商品不存在返回 404")
    void detailNotFound() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> catalogService.detail(99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("下架商品返回 404，不泄露可购买信息")
    void draftProductIsNotFound() {
        Product draft = product(1L, 10L, "DRAFT");
        when(productMapper.selectById(1L)).thenReturn(draft);

        assertThatThrownBy(() -> catalogService.detail(1L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("详情返回两级分类与相册")
    void detailReturnsCategoryPathAndImages() {
        Product product = product(1L, 10L, "ON_SALE");
        Category leaf = new Category();
        leaf.setId(10L);
        leaf.setParentId(1L);
        leaf.setName("单行本");
        Category top = new Category();
        top.setId(1L);
        top.setName("星轨列车");
        ProductImage image = new ProductImage();
        image.setId(5L);
        image.setImagePath("images/products/X/1.svg");

        when(productMapper.selectById(1L)).thenReturn(product);
        when(categoryMapper.selectById(10L)).thenReturn(leaf);
        when(categoryMapper.selectById(1L)).thenReturn(top);
        when(productImageMapper.selectList(any())).thenReturn(List.of(image));

        ProductDetail detail = catalogService.detail(1L);

        assertThat(detail.topCategory().getName()).isEqualTo("星轨列车");
        assertThat(detail.leafCategory().getName()).isEqualTo("单行本");
        assertThat(detail.hasGallery()).isTrue();
        assertThat(detail.images()).hasSize(1);
    }

    @Test
    @DisplayName("没有相册时 hasGallery 为 false，页面回退封面")
    void detailWithoutGallery() {
        Product product = product(2L, 10L, "ON_SALE");
        Category leaf = new Category();
        leaf.setId(10L);
        leaf.setName("单行本");
        when(productMapper.selectById(2L)).thenReturn(product);
        when(categoryMapper.selectById(10L)).thenReturn(leaf);
        when(productImageMapper.selectList(any())).thenReturn(List.of());

        ProductDetail detail = catalogService.detail(2L);

        assertThat(detail.hasGallery()).isFalse();
        assertThat(detail.topCategory().getId()).isEqualTo(10L);
    }

    private Product product(long id, long categoryId, String status) {
        Product product = new Product();
        product.setId(id);
        product.setSku("TEST-" + id);
        product.setName("测试商品");
        product.setCategoryId(categoryId);
        product.setStatus(status);
        product.setPrice(new BigDecimal("28.00"));
        product.setStock(10);
        return product;
    }
}
