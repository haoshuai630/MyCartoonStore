package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录拦截测试：未登录被拦到登录页，已登录放行。
 *
 * <p>受保护页面本身在 M04/M05 才实现，这里不关心它们返回什么，
 * 只断言"有没有被重定向到登录页"。</p>
 */
@AutoConfigureMockMvc
class LoginInterceptorTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("未登录访问购物车：跳登录并带上原地址")
    void cartRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fcart*"));
    }

    @Test
    @DisplayName("未登录访问订单：同样被拦")
    void ordersRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Forders*"));
    }

    @Test
    @DisplayName("已登录访问购物车：不再跳登录")
    void loggedInPassesThrough() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("公开页面不受拦截")
    void publicPagesAreOpen() throws Exception {
        mockMvc.perform(get("/main")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }
}