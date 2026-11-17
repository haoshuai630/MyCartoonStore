package org.csu.mycartoonstore.web; // XSS 转义用例：库里的脚本不能原样出现在页面上。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 每例重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 造商品。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入依赖。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 直接写库造"恶意内容"。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import static org.hamcrest.Matchers.containsString; // 正向断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 响应体断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M09-03：依据 docs/06 AC-31「商品名/简介输入脚本 → 文本转义」。
// 覆盖：详情页与列表页对商品名、简介、描述的输出转义；搜索关键字回显转义。
// 不覆盖：后台表单回显（同样是 th:text，由页面用例覆盖）；富文本（本项目没有）。
@AutoConfigureMockMvc // 不占端口。
class XssEscapingTest extends AbstractIntegrationTest {

    /** 恶意片段：只要页面上出现未转义的它，用例就红。 */
    private static final String SCRIPT = "<script>alert(1)</script>"; // 最典型的脚本注入载荷。

    /** 转义后的样子：浏览器会当成文字显示。 */
    private static final String ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;"; // Thymeleaf th:text 的输出。

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据。

    @Test // 详情页把商品名、简介、描述都当文本输出。
    @DisplayName("详情页：商品名与简介里的脚本被转义")
    void productPageEscapesScript() throws Exception {
        long productId = CartFixture.createProduct(jdbc, "XSS-C01", "28.00", 10, "ON_SALE"); // 上架商品。
        jdbc.update("UPDATE product SET name = ?, summary = ?, description = ? WHERE id = ?",
                "名称" + SCRIPT, "简介" + SCRIPT, "描述" + SCRIPT, productId); // 模拟后台录入恶意内容。

        mockMvc.perform(get("/product/" + productId)) // 游客看详情。
                .andExpect(status().isOk()) // 页面正常渲染。
                .andExpect(content().string(containsString(ESCAPED))) // 输出的是转义文本。
                .andExpect(content().string(not(containsString(SCRIPT)))); // 没有原样脚本。
    }

    @Test // 列表页同样输出名称与简介。
    @DisplayName("列表页：商品名里的脚本被转义")
    void categoryPageEscapesScript() throws Exception {
        long productId = CartFixture.createProduct(jdbc, "XSS-C02", "28.00", 10, "ON_SALE"); // 上架商品，列表可见。
        jdbc.update("UPDATE product SET name = ? WHERE id = ?", "名称" + SCRIPT, productId); // 只改名。

        mockMvc.perform(get("/category").param("keyword", "名称")) // 关键字命中这一条。
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ESCAPED))) // 商品名被转义。
                .andExpect(content().string(not(containsString(SCRIPT)))); // 没有原样脚本。
    }

    @Test // 搜索框回显关键字：不能把用户输入当 HTML。
    @DisplayName("列表页：搜索关键字回显被转义")
    void keywordEchoEscapesScript() throws Exception {
        mockMvc.perform(get("/category").param("keyword", SCRIPT)) // 直接拿脚本当关键字。
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(SCRIPT)))) // 输入框里不能有可执行标签。
                .andExpect(content().string(containsString("&lt;script&gt;"))); // 至少以转义形式出现。
    }
}
