package org.csu.mypetstore.controller;

import org.csu.mypetstore.dto.UserReadResult;
import org.csu.mypetstore.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Controller
@Profile("dev")
public class UserDemoController {

    private final UserService userService;

    public UserDemoController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 只调用 Service，不碰 Mapper、不返回 User 实体；
     * 查不到给 404，数据访问异常继续上抛（由容器转成 500），不做统一 404 兜底。
     */
    @GetMapping(value = "/demo/user", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<UserReadResult> user(
            @RequestParam(name = "username", defaultValue = "alice") String username) {
        return userService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 页面接口：同样复用 UserService 查一次，把消息和用户放进 Model，
     * 返回的字符串是视图名（不是响应体），所以这里不加 @ResponseBody。
     * 只有“用户不存在”这一种情况转成 404，数据访问异常仍然上抛。
     */
    @GetMapping("/demo/user/page")
    public String userPage(
            @RequestParam(name = "username", defaultValue = "alice") String username, Model model) {
        Optional<UserReadResult> found = userService.findByUsername(username);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在：" + username);
        }
        model.addAttribute("message", "Thymeleaf 初始化成功！");
        model.addAttribute("user", found.get());
        return "demo/user";
    }
}
