package org.csu.mycartoonstore.admin; // 商品维护页面：列表、筛选、新增、编辑（AC-25/AC-27）。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号、分类、商品。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc / JdbcTemplate。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.mock.web.MockHttpSession; // 管理端会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 断言页面内容。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 断言 302 目标。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M07-06：依据 docs/04 §3.3、docs/06 AC-25/AC-27、docs/05 §4.9。
// 覆盖：列表含下架与筛选、新增默认 DRAFT、字段校验回表单、编辑页 SKU/库存只读、旧版本保存 409。
// 不覆盖：上下架与补货（M07-07）、分类页面（M07-04）。
@AutoConfigureMockMvc // 不占端口。
class ProductAdminPageTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    @Test // 列表：含下架商品，显示 SKU、价格、状态。
    @DisplayName("列表：含下架商品，显示状态徽章")
    void listShowsAllStatuses() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        AdminFixture.createProductIn(jdbc, "LIST-ON", leaf, "28.00", 5, "ON_SALE"); // 上架商品。
        AdminFixture.createProductIn(jdbc, "LIST-OFF", leaf, "39.00", 0, "DRAFT"); // 下架商品。

        mockMvc.perform(get("/products").session(adminSession())) // 管理员打开列表。
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("LIST-ON"))) // 上架商品在列表里。
                .andExpect(content().string(containsString("LIST-OFF"))) // 下架商品也在列表里。
                .andExpect(content().string(containsString("已下架"))) // 状态徽章。
                .andExpect(content().string(containsString("¥28.00"))); // 价格两位小数。
    }

    @Test // 状态筛选：只看下架。
    @DisplayName("列表：按状态筛选")
    void listFiltersByStatus() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        AdminFixture.createProductIn(jdbc, "F-ON", leaf, "10.00", 1, "ON_SALE"); // 上架。
        AdminFixture.createProductIn(jdbc, "F-OFF", leaf, "20.00", 1, "DRAFT"); // 下架。

        mockMvc.perform(get("/products").param("status", "DRAFT").session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("F-OFF"))) // 命中下架。
                .andExpect(content().string(not(containsString("F-ON")))); // 上架被过滤掉。
    }

    @Test // 名称搜索：包含匹配。
    @DisplayName("列表：按名称包含搜索")
    void listFiltersByKeyword() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        AdminFixture.createProductIn(jdbc, "KW-1", leaf, "10.00", 1, "ON_SALE"); // 名称含 KW-1。
        AdminFixture.createProductIn(jdbc, "OTHER", leaf, "10.00", 1, "ON_SALE"); // 不含。

        mockMvc.perform(get("/products").param("keyword", "KW-1").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("商品KW-1"))) // 命中。
                .andExpect(content().string(not(containsString("商品OTHER")))); // 未命中。
    }

    @Test // 新增表单：分类下拉只有二级。
    @DisplayName("新增表单：分类下拉只列二级")
    void newForm() throws Exception {
        seedLeaf(); // 一个作品 + 一个品类。

        mockMvc.perform(get("/products/new").session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("新增商品"))) // 标题。
                .andExpect(content().string(containsString("单行本"))) // 二级选项。
                .andExpect(content().string(containsString("初始库存"))); // 新增才有库存输入。
    }

    @Test // AC-25：新增商品默认下架。
    @DisplayName("新增：默认 DRAFT 并回列表（AC-25）")
    void createProduct() throws Exception {
        long leaf = seedLeaf(); // 二级分类。

        mockMvc.perform(post("/products").with(csrf()) // 提交新增。
                        .param("sku", "NEW-C01") // SKU。
                        .param("name", "新商品") // 名称。
                        .param("categoryId", String.valueOf(leaf)) // 二级分类。
                        .param("price", "28.50") // 价格。
                        .param("stock", "7") // 初始库存。
                        .param("coverImage", "images/products/NEW-C01/cover.svg") // 封面。
                        .param("sortOrder", "2") // 排序。
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/products"));

        assertThat(jdbc.queryForObject("SELECT status FROM product WHERE sku = 'NEW-C01'", String.class))
                .isEqualTo("DRAFT"); // 新增默认下架。
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE sku = 'NEW-C01'", Integer.class))
                .isEqualTo(7); // 初始库存。
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE sku = 'NEW-C01'", Long.class))
                .isZero(); // 版本从 0 开始。
    }

    @Test // 小写 SKU：400 + 红字，不写库。
    @DisplayName("新增：小写 SKU → 400 回表单")
    void createBadSku() throws Exception {
        long leaf = seedLeaf(); // 二级分类。

        mockMvc.perform(post("/products").with(csrf())
                        .param("sku", "lower-case") // 不合法。
                        .param("name", "新商品")
                        .param("categoryId", String.valueOf(leaf))
                        .param("price", "28.50")
                        .param("coverImage", "images/x.svg")
                        .session(adminSession()))
                .andExpect(status().isBadRequest()) // 400。
                .andExpect(content().string(containsString("大写"))); // 文案说明字符集。

        assertThat(count("SELECT COUNT(*) FROM product WHERE sku = 'lower-case'")).isZero(); // 未写库。
    }

    @Test // M09-05：描述上限 2000，超了回表单。
    @DisplayName("新增：描述超 2000 字 → 400 回表单")
    void createTooLongDescription() throws Exception {
        long leaf = seedLeaf(); // 二级分类。

        mockMvc.perform(post("/products").with(csrf())
                        .param("sku", "LONG-DESC")
                        .param("name", "新商品")
                        .param("categoryId", String.valueOf(leaf))
                        .param("price", "28.50")
                        .param("description", "描".repeat(2001)) // 刚好超一个字符。
                        .param("coverImage", "images/x.svg")
                        .session(adminSession()))
                .andExpect(status().isBadRequest()) // 400。
                .andExpect(content().string(containsString("2000"))); // 文案带上限。

        assertThat(count("SELECT COUNT(*) FROM product WHERE sku = 'LONG-DESC'")).isZero(); // 未写库。
    }

    @Test // M09-05：封面只收站内路径。
    @DisplayName("新增：外链封面 → 400 回表单")
    void createExternalCover() throws Exception {
        long leaf = seedLeaf(); // 二级分类。

        mockMvc.perform(post("/products").with(csrf())
                        .param("sku", "EXT-COVER")
                        .param("name", "新商品")
                        .param("categoryId", String.valueOf(leaf))
                        .param("price", "28.50")
                        .param("coverImage", "https://evil.example.com/a.png") // 外链。
                        .session(adminSession()))
                .andExpect(status().isBadRequest()) // 400。
                .andExpect(content().string(containsString("站内路径"))); // 文案说明口径。

        assertThat(count("SELECT COUNT(*) FROM product WHERE sku = 'EXT-COVER'")).isZero(); // 未写库。
    }

    @Test // 价格三位小数：400。
    @DisplayName("新增：价格三位小数 → 400")
    void createBadPrice() throws Exception {
        long leaf = seedLeaf(); // 二级分类。

        mockMvc.perform(post("/products").with(csrf())
                        .param("sku", "P-1")
                        .param("name", "新商品")
                        .param("categoryId", String.valueOf(leaf))
                        .param("price", "12.345") // 超两位小数。
                        .param("coverImage", "images/x.svg")
                        .session(adminSession()))
                .andExpect(status().isBadRequest()) // 400。
                .andExpect(content().string(containsString("两位小数")));
    }

    @Test // 一级分类不能挂商品：400。
    @DisplayName("新增：分类是一级 → 400")
    void createTopCategory() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "TOP", null, "作品", 1); // 一级。

        mockMvc.perform(post("/products").with(csrf())
                        .param("sku", "T-1")
                        .param("name", "新商品")
                        .param("categoryId", String.valueOf(top)) // 一级。
                        .param("price", "10.00")
                        .param("coverImage", "images/x.svg")
                        .session(adminSession()))
                .andExpect(status().isBadRequest()) // 400。
                .andExpect(content().string(containsString("二级分类")));
    }

    @Test // 编辑页：带出当前值，SKU 与库存只读。
    @DisplayName("编辑页：SKU 与库存只读，版本在隐藏域")
    void editForm() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "EDIT-1", leaf, "28.00", 4, "ON_SALE"); // 一件上架商品。

        mockMvc.perform(get("/products/{id}/edit", productId).session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("EDIT-1"))) // SKU 展示。
                .andExpect(content().string(containsString("readonly"))) // 只读。
                .andExpect(content().string(containsString("name=\"version\""))); // 版本隐藏域。
    }

    @Test // AC-27：保存成功，版本 +1，SKU 与库存不变。
    @DisplayName("编辑：保存资料，版本 +1（AC-27）")
    void editSaves() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "EDIT-2", leaf, "28.00", 4, "ON_SALE"); // 版本 0。

        mockMvc.perform(post("/products/{id}", productId).with(csrf()) // 提交编辑。
                        .param("sku", "HACKED") // 服务端忽略。
                        .param("name", "新名字") // 新名称。
                        .param("categoryId", String.valueOf(leaf)) // 分类。
                        .param("price", "31.00") // 新价格。
                        .param("stock", "999") // 服务端忽略。
                        .param("coverImage", "images/x.svg") // 封面。
                        .param("version", "0") // 隐藏域带回的版本。
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/products"));

        assertThat(jdbc.queryForObject("SELECT name FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("新名字"); // 名称生效。
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId))
                .isEqualTo(1L); // 版本 +1。
        assertThat(jdbc.queryForObject("SELECT sku FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("EDIT-2"); // SKU 不变。
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId))
                .isEqualTo(4); // 库存不变。
    }

    @Test // AC-27：旧版本保存返回 409，且不覆盖新数据。
    @DisplayName("编辑：旧版本保存 → 409（AC-27）")
    void editStaleVersion() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "EDIT-3", leaf, "28.00", 4, "ON_SALE"); // 版本 0。
        jdbc.update("UPDATE product SET version = 3 WHERE id = ?", productId); // 别人已经改过两次。

        mockMvc.perform(post("/products/{id}", productId).with(csrf())
                        .param("name", "旧页面改的名字")
                        .param("categoryId", String.valueOf(leaf))
                        .param("price", "99.00")
                        .param("coverImage", "images/x.svg")
                        .param("version", "0") // 旧页面手里的版本。
                        .session(adminSession()))
                .andExpect(status().isConflict()) // 409。
                .andExpect(content().string(containsString("数据已发生变化"))); // 红字提示。

        assertThat(jdbc.queryForObject("SELECT name FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("商品EDIT-3"); // 没被覆盖。
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId))
                .isEqualTo(3L); // 版本未变。
    }

    @Test // 上下架：版本一致才切换。
    @DisplayName("上下架：上架成功，版本 +1")
    void toggleStatus() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "ST-1", leaf, "28.00", 4, "DRAFT"); // 下架商品，版本 0。

        mockMvc.perform(post("/products/{id}/status", productId).with(csrf()) // 点「上架」。
                        .param("status", "ON_SALE") // 目标状态。
                        .param("version", "0") // 隐藏域带回的版本。
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/products"));

        assertThat(jdbc.queryForObject("SELECT status FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("ON_SALE"); // 已上架。
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId))
                .isEqualTo(1L); // 版本 +1。
    }

    @Test // 上下架：旧版本 → 409。
    @DisplayName("上下架：旧版本 → 409")
    void toggleStatusStale() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "ST-2", leaf, "28.00", 4, "DRAFT"); // 下架商品。
        jdbc.update("UPDATE product SET version = 3 WHERE id = ?", productId); // 别人已经改过。

        mockMvc.perform(post("/products/{id}/status", productId).with(csrf())
                        .param("status", "ON_SALE")
                        .param("version", "0") // 旧页面手里的版本。
                        .session(adminSession()))
                .andExpect(status().isConflict()); // 409 错误页。

        assertThat(jdbc.queryForObject("SELECT status FROM product WHERE id = ?", String.class, productId))
                .isEqualTo("DRAFT"); // 状态没被改。
    }

    @Test // 补货：在最新库存上累加并回列表提示。
    @DisplayName("补货：库存 +3，版本 +1")
    void restock() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "RS-1", leaf, "28.00", 4, "ON_SALE"); // 库存 4。

        mockMvc.perform(post("/products/{id}/restock", productId).with(csrf()) // 补 3 件。
                        .param("quantity", "3")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/products"));

        assertThat(AdminFixture.stockOf(jdbc, productId)).isEqualTo(7); // 4 + 3。
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId))
                .isEqualTo(1L); // 版本 +1。
    }

    @Test // 补货数量非法：400。
    @DisplayName("补货：数量为 0 → 400")
    void restockInvalidQuantity() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "RS-2", leaf, "28.00", 4, "ON_SALE"); // 库存 4。

        mockMvc.perform(post("/products/{id}/restock", productId).with(csrf())
                        .param("quantity", "0") // 不允许 0。
                        .session(adminSession()))
                .andExpect(status().isBadRequest()); // 400 错误页。

        assertThat(AdminFixture.stockOf(jdbc, productId)).isEqualTo(4); // 库存没变。
    }

    /** 造"作品 + 单行本"，返回二级分类 id */
    private long seedLeaf() {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        return AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。
    }

    /** 管理员会话 */
    private MockHttpSession adminSession() {
        long adminId = AdminFixture.createAdmin(jdbc, "prod_admin"); // 每个用例单独造。
        return AdminFixture.session(adminId); // 带登录态。
    }

    /** 单值计数 */
    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class); // 计数查询。
        return value == null ? 0 : value; // 空按 0。
    }
}
