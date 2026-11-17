package org.csu.mycartoonstore.web.controller; // 验证码图片接口：生成、写会话、返回 PNG。

import jakarta.servlet.http.HttpSession; // 验证码存会话。
import org.csu.mycartoonstore.web.captcha.CaptchaService; // 生成与绘制。
import org.springframework.http.CacheControl; // 禁止缓存。
import org.springframework.http.MediaType; // 响应类型。
import org.springframework.http.ResponseEntity; // 带头的响应。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.web.bind.annotation.GetMapping; // 只读接口。

// M10-04：依据指导书实验内容一「验证码」。
// 负责：每次请求都生成新验证码并写进会话，返回 PNG。
// 不做：校验（在 AuthController），不做图片缓存。
@Controller // 返回二进制而不是模板名。
public class CaptchaController {

    private final CaptchaService captchaService; // 生成器。

    public CaptchaController(CaptchaService captchaService) { // 构造器注入。
        this.captchaService = captchaService; // 保存服务。
    }

    /** 输出验证码图片：每次刷新都会换一个新码。 */
    @GetMapping("/captcha")
    public ResponseEntity<byte[]> captcha(HttpSession session) {
        String code = captchaService.newCode(); // 新码。
        session.setAttribute(CaptchaService.SESSION_KEY, code); // 覆盖旧码：刷新即作废旧码。
        return ResponseEntity.ok() // 200。
                .contentType(MediaType.IMAGE_PNG) // PNG。
                .cacheControl(CacheControl.noStore()) // 浏览器不缓存，刷新一定重新拿。
                .body(captchaService.png(code)); // 图片字节。
    }
}
