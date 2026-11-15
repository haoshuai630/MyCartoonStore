package org.csu.mycartoonstore.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 布局渲染测试：确认公共片段能被 Thymeleaf 解析、静态资源能正确返回。
 *
 * <p>不连业务数据，所以不继承集成测试基类，避免每个用例重建七张表。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LayoutRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("首页能渲染出页头、页脚与样式表链接")
    void homeRendersLayout() throws Exception {
        mockMvc.perform(get("/main"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("漫屿")))
                .andExpect(content().string(containsString("/css/store.css")))
                .andExpect(content().string(containsString("MANGA ISLE")));
    }

    @Test
    @DisplayName("SVG 封面能作为静态资源返回")
    void coverIsServed() throws Exception {
        mockMvc.perform(get("/images/products/STARLINE-C01/cover.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
    }

    @Test
    @DisplayName("样式表能返回且非空")
    void cssIsServed() throws Exception {
        mockMvc.perform(get("/css/store.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }
}
