package org.csu.mycartoonstore.admin.controller; // 相册维护：按商品查看、新增、编辑、删除图片记录。

import jakarta.servlet.http.HttpServletResponse; // 校验失败时给出正确状态码。
import org.csu.mycartoonstore.catalog.service.ProductImageAdminService; // 业务规则。
import org.csu.mycartoonstore.common.exception.BizException; // 校验失败回列表。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 传模板。
import org.springframework.web.bind.annotation.GetMapping; // 列表。
import org.springframework.web.bind.annotation.PathVariable; // 商品与图片 id。
import org.springframework.web.bind.annotation.PostMapping; // 新增/编辑/删除。
import org.springframework.web.bind.annotation.RequestMapping; // 路由前缀。
import org.springframework.web.bind.annotation.RequestParam; // 表单字段。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 一次性提示。

// M08-05：依据 docs/01 FR-A-07、docs/04 §3.5、docs/05 §4.9。
// 只做参数绑定、页面选择与跳转；路径/说明/排序校验与归属判断都在 ProductImageAdminService。
// 删除只删记录：不删文件、不改商品封面；失败回列表并保留输入（与 M07 后台表单同一口径）。
@Controller // 返回模板名。
@RequestMapping("/products/{productId}/images") // 挂在商品下的相册路由。
public class AdminImageController {

    private final ProductImageAdminService imageAdminService; // 业务服务。

    public AdminImageController(ProductImageAdminService imageAdminService) { // 构造器注入。
        this.imageAdminService = imageAdminService; // 保存服务。
    }

    /** 相册列表：商品信息 + 按 sort_order、id 升序的图片 + 新增表单。 */
    @GetMapping
    public String list(@PathVariable long productId, Model model) {
        model.addAttribute("product", imageAdminService.product(productId)); // 商品不存在时 404。
        model.addAttribute("images", imageAdminService.images(productId)); // 相册行。
        model.addAttribute("active", "products"); // 导航高亮。
        return "admin/image-list"; // 列表模板。
    }

    /** 新增：成功后回列表并提示；失败回列表并保留输入。 */
    @PostMapping
    public String add(@PathVariable long productId,
                      @RequestParam String imagePath, // 站内路径。
                      @RequestParam(required = false) String altText, // 可空。
                      @RequestParam(required = false) String sortOrder, // 可空，默认 0。
                      Model model, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            imageAdminService.add(productId, imagePath, altText, sortOrder); // 校验与写库。
            redirect.addFlashAttribute("message", "已新增一张相册图"); // 一次性提示。
            return "redirect:/products/" + productId + "/images"; // PRG。
        } catch (BizException e) { // 路径/说明/排序不合法。
            return backToList(productId, imagePath, altText, sortOrder, e, response, model); // 回列表。
        }
    }

    /** 编辑：只改路径、说明与排序。 */
    @PostMapping("/{imageId}")
    public String update(@PathVariable long productId, @PathVariable long imageId,
                         @RequestParam String imagePath,
                         @RequestParam(required = false) String altText,
                         @RequestParam(required = false) String sortOrder,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            imageAdminService.update(productId, imageId, imagePath, altText, sortOrder); // 归属 + 校验 + 写库。
            redirect.addFlashAttribute("message", "已保存相册图"); // 一次性提示。
            return "redirect:/products/" + productId + "/images"; // PRG。
        } catch (BizException e) { // 图片不属于该商品或字段非法。
            return backToList(productId, imagePath, altText, sortOrder, e, response, model); // 回列表。
        }
    }

    /** 删除：只删记录，不删文件、不改封面。 */
    @PostMapping("/{imageId}/delete")
    public String delete(@PathVariable long productId, @PathVariable long imageId,
                         RedirectAttributes redirect) {
        imageAdminService.delete(productId, imageId); // 不存在/不属于该商品 → 404。
        redirect.addFlashAttribute("message", "已删除相册图（文件保留）"); // 说明只删记录。
        return "redirect:/products/" + productId + "/images"; // PRG。
    }

    /** 校验失败：回列表，保留输入与商品上下文，HTTP 状态取业务异常。 */
    private String backToList(long productId, String imagePath, String altText, String sortOrder,
                              BizException e, HttpServletResponse response, Model model) {
        response.setStatus(e.code().status()); // 400 或 404。
        model.addAttribute("product", imageAdminService.product(productId)); // 商品信息不能丢。
        model.addAttribute("images", imageAdminService.images(productId)); // 列表照常渲染。
        model.addAttribute("error", e.getMessage()); // 红字提示。
        model.addAttribute("formPath", imagePath); // 保留刚填的路径。
        model.addAttribute("formAlt", altText); // 保留说明。
        model.addAttribute("formSort", sortOrder); // 保留排序。
        model.addAttribute("active", "products");
        return "admin/image-list"; // 同一个模板。
    }
}
