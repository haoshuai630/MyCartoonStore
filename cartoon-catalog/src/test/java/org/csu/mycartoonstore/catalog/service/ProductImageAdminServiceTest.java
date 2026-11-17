package org.csu.mycartoonstore.catalog.service; // 后台相册维护：校验、归属与只删记录。

import org.csu.mycartoonstore.catalog.entity.Product;
import org.csu.mycartoonstore.catalog.entity.ProductImage;
import org.csu.mycartoonstore.catalog.mapper.ProductImageMapper;
import org.csu.mycartoonstore.catalog.mapper.ProductMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// M08-05：依据 docs/01 FR-A-07、docs/06 的相册规则。
// 覆盖：新增/编辑的字段校验、商品与图片归属 404、删除只删记录。
// 不覆盖：页面渲染与封面不变（ImageAdminPageTest 走真实库）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class ProductImageAdminServiceTest {

    @Mock // 商品 Mapper：只用于存在性。
    private ProductMapper productMapper;

    @Mock // 相册 Mapper。
    private ProductImageMapper imageMapper;

    /** 被测服务 */
    private ProductImageAdminService service() {
        return new ProductImageAdminService(productMapper, imageMapper); // 每次构造新实例。
    }

    @Test // 新增：写入四条业务列，排序默认 0。
    @DisplayName("新增：只写商品、路径、说明、排序")
    void addWritesFields() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));
        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);

        service().add(5L, "  images/products/S-1/1.svg  ", "  封面图  ", "3");

        verify(imageMapper).insert(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo(5L);
        assertThat(saved.getValue().getImagePath()).isEqualTo("images/products/S-1/1.svg");
        assertThat(saved.getValue().getAltText()).isEqualTo("封面图");
        assertThat(saved.getValue().getSortOrder()).isEqualTo(3);
    }

    @Test // 路径必填。
    @DisplayName("新增：路径空白 → 400，不写库")
    void addBlankPath() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));

        assertThatThrownBy(() -> service().add(5L, "  ", null, null))
                .isInstanceOf(BizException.class)
                .hasMessage("图片路径不能为空");
        verify(imageMapper, never()).insert(any(ProductImage.class));
    }

    @Test // 外链留到 docs/12。
    @DisplayName("新增：外链路径 → 400")
    void addExternalPath() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));

        assertThatThrownBy(() -> service().add(5L, "https://example.com/a.jpg", null, null))
                .isInstanceOf(BizException.class)
                .hasMessage("图片路径只接受站内路径");
    }

    @Test // 说明超长。
    @DisplayName("新增：说明超过 120 字 → 400")
    void addTooLongAlt() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));

        assertThatThrownBy(() -> service().add(5L, "images/a.svg", "说".repeat(121), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过 120");
    }

    @Test // 排序非法。
    @DisplayName("新增：排序为负 → 400")
    void addNegativeSort() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));

        assertThatThrownBy(() -> service().add(5L, "images/a.svg", null, "-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("0–999999");
    }

    @Test // 商品不存在。
    @DisplayName("新增：商品不存在 → 404")
    void addMissingProduct() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service().add(99L, "images/a.svg", null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(imageMapper, never()).insert(any(ProductImage.class));
    }

    @Test // 编辑：只改三列。
    @DisplayName("编辑：路径、说明、排序都按表单更新")
    void updateChangesFields() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));
        when(imageMapper.selectById(7L)).thenReturn(image(7L, 5L, "images/old.svg"));

        ProductImage updated = service().update(5L, 7L, "images/new.svg", "新说明", "9");

        assertThat(updated.getImagePath()).isEqualTo("images/new.svg");
        assertThat(updated.getAltText()).isEqualTo("新说明");
        assertThat(updated.getSortOrder()).isEqualTo(9);
        verify(imageMapper).updateById(updated);
    }

    @Test // 图片不属于该商品：404，不写库。
    @DisplayName("编辑：图片不属于该商品 → 404")
    void updateNotBelonging() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));
        when(imageMapper.selectById(7L)).thenReturn(image(7L, 6L, "images/old.svg"));

        assertThatThrownBy(() -> service().update(5L, 7L, "images/new.svg", null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(imageMapper, never()).updateById(any(ProductImage.class));
    }

    @Test // 删除：只删这条记录。
    @DisplayName("删除：只删记录")
    void deleteRemovesRecord() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));
        when(imageMapper.selectById(7L)).thenReturn(image(7L, 5L, "images/a.svg"));

        service().delete(5L, 7L);

        verify(imageMapper).deleteById(7L);
    }

    @Test // 删除越权/不存在：404。
    @DisplayName("删除：图片不属于该商品 → 404")
    void deleteNotBelonging() {
        when(productMapper.selectById(5L)).thenReturn(product(5L));
        when(imageMapper.selectById(7L)).thenReturn(image(7L, 6L, "images/a.svg"));

        assertThatThrownBy(() -> service().delete(5L, 7L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(imageMapper, never()).deleteById(anyLong());
    }

    /** 造商品：只用 id 与名称，页面测试不在这里。 */
    private Product product(long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("商品" + id);
        product.setSku("S-" + id);
        return product;
    }

    /** 造相册记录：归属由用例指定，用于越权判断。 */
    private ProductImage image(long id, long productId, String path) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setProductId(productId);
        image.setImagePath(path);
        image.setAltText("说明");
        image.setSortOrder(1);
        return image;
    }
}
