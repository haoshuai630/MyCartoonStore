package org.csu.mycartoonstore.catalog.service; // 后台相册维护：按商品列出、新增、编辑、删除图片记录。

import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 条件与排序。
import org.csu.mycartoonstore.catalog.dto.ImageForm; // 表单校验。
import org.csu.mycartoonstore.catalog.entity.Product; // 商品：存在性与页面展示。
import org.csu.mycartoonstore.catalog.entity.ProductImage; // 相册记录。
import org.csu.mycartoonstore.catalog.mapper.ProductImageMapper; // 相册读写。
import org.csu.mycartoonstore.catalog.mapper.ProductMapper; // 商品存在性。
import org.csu.mycartoonstore.common.exception.BizException; // 404。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 写操作同一事务。

import java.util.List; // 列表。

// M08-05：依据 docs/01 FR-A-07、docs/04 §3.5、docs/05 §4.9。
// 负责：按商品查看相册（sort_order、id 升序）、新增、编辑、删除记录。
// 不做：上传/删除文件、改 product.cover_image、动商品 version（相册与商品资料是两个写面）。
// 删除只删记录：详情页在无相册时回退封面，因此删空相册是安全操作。
@Service // 业务服务：后台控制器只调它。
public class ProductImageAdminService {

    private final ProductMapper productMapper; // 商品存在性与页面展示。
    private final ProductImageMapper imageMapper; // 相册读写。

    public ProductImageAdminService(ProductMapper productMapper, ProductImageMapper imageMapper) { // 构造器注入。
        this.productMapper = productMapper; // 保存商品 Mapper。
        this.imageMapper = imageMapper; // 保存相册 Mapper。
    }

    /** 商品：不存在时 404，页面与所有写操作都先过这一关。 */
    public Product product(long productId) {
        Product product = productMapper.selectById(productId); // 主键查询。
        if (product == null) {
            throw BizException.notFound("商品不存在"); // 404。
        }
        return product; // 存在的商品。
    }

    /** 某商品的相册：按 sort_order、id 升序，与详情页取图顺序一致。 */
    public List<ProductImage> images(long productId) {
        product(productId); // 先确认商品存在。
        return imageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, productId) // 只取该商品。
                .orderByAsc(ProductImage::getSortOrder) // 排序值小的在前。
                .orderByAsc(ProductImage::getId)); // 同排序按 id 稳定。
    }

    /** 新增一张相册图：校验在 ImageForm，落库只写四条业务列。 */
    @Transactional
    public ProductImage add(long productId, String rawPath, String rawAlt, String rawSort) {
        product(productId); // 商品必须存在。
        ImageForm form = ImageForm.parse(rawPath, rawAlt, rawSort); // 去空白 + 校验。
        ProductImage image = new ProductImage(); // 新记录。
        image.setProductId(productId); // 归属商品。
        image.setImagePath(form.imagePath()); // 站内路径。
        image.setAltText(form.altText()); // 可空。
        image.setSortOrder(form.sortOrder()); // 默认 0。
        imageMapper.insert(image); // 自增 id 回填。
        return image; // 交给控制器做提示。
    }

    /** 编辑一张相册图：只改路径、说明与排序，不改所属商品。 */
    @Transactional
    public ProductImage update(long productId, long imageId, String rawPath, String rawAlt, String rawSort) {
        product(productId); // 商品必须存在。
        ProductImage image = requireImage(productId, imageId); // 图片必须属于该商品。
        ImageForm form = ImageForm.parse(rawPath, rawAlt, rawSort); // 同一套校验。
        image.setImagePath(form.imagePath()); // 换路径。
        image.setAltText(form.altText()); // 换说明。
        image.setSortOrder(form.sortOrder()); // 换排序。
        imageMapper.updateById(image); // product_id 不在改动之列。
        return image; // 最新值。
    }

    /** 删除一张相册图：只删记录，不删文件、不改封面。 */
    @Transactional
    public void delete(long productId, long imageId) {
        product(productId); // 商品必须存在。
        requireImage(productId, imageId); // 越权/不存在同一处理。
        imageMapper.deleteById(imageId); // 只删这一条。
    }

    /** 取图片并校验归属：不存在或不属于该商品一律 404，不泄露是否存在。 */
    private ProductImage requireImage(long productId, long imageId) {
        ProductImage image = imageMapper.selectById(imageId); // 主键查询。
        if (image == null || image.getProductId() != productId) {
            throw BizException.notFound("图片不存在"); // 404。
        }
        return image; // 属于该商品的图片。
    }
}
