package org.csu.mycartoonstore.web.controller;

import jakarta.servlet.http.HttpSession;
import org.csu.mycartoonstore.cart.dto.CartSummary;
import org.csu.mycartoonstore.cart.service.CartService;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 购物车页面与四个写操作。
 *
 * <p>控制器只做三件事：从会话取当前用户、把原始表单值交给 {@link CartService}、
 * 决定重定向目标。数量、库存、归属、版本这些判断一律不在这一层。</p>
 *
 * <p>写操作成功一律回 {@code /cart} 并用 Flash 提示；失败由全局异常处理器转成 4xx 错误页。</p>
 */
@Controller
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** 购物车页：失效条目也渲染，页面自己决定是否禁用结算 */
    @GetMapping("/cart")
    public String cart(HttpSession session, Model model) {
        CartSummary summary = cartService.summary(currentUserId(session));
        model.addAttribute("cart", summary);
        return "cart";
    }

    /** 加入购物车：同商品累加，回购物车看结果 */
    @PostMapping("/cart/items")
    public String addItem(@RequestParam String productId,
                          @RequestParam(defaultValue = "1") String quantity,
                          HttpSession session,
                          RedirectAttributes redirect) {
        cartService.add(currentUserId(session), productId, quantity);
        redirect.addFlashAttribute("message", "已加入购物车");
        return "redirect:/cart";
    }

    /** 改数量：带旧 version，冲突时由服务层给 409 */
    @PostMapping("/cart/items/{itemId}/quantity")
    public String changeQuantity(@PathVariable String itemId,
                                 @RequestParam String quantity,
                                 @RequestParam String version,
                                 HttpSession session,
                                 RedirectAttributes redirect) {
        cartService.changeQuantity(currentUserId(session), itemId, quantity, version);
        redirect.addFlashAttribute("message", "数量已更新");
        return "redirect:/cart";
    }

    /** 移除条目：同样带旧 version */
    @PostMapping("/cart/items/{itemId}/remove")
    public String removeItem(@PathVariable String itemId,
                             @RequestParam String version,
                             HttpSession session,
                             RedirectAttributes redirect) {
        cartService.remove(currentUserId(session), itemId, version);
        redirect.addFlashAttribute("message", "已移除该商品");
        return "redirect:/cart";
    }

    /**
     * 当前登录用户 id。
     * <p>受保护路径已由 LoginInterceptor 保证有会话；这里再判一次是防止以后改拦截规则时
     * 出现 NullPointerException——宁可给 401 也不要 500。</p>
     */
    private long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(LoginInterceptor.USER_ID);
        if (userId instanceof Long id) {
            return id;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}
