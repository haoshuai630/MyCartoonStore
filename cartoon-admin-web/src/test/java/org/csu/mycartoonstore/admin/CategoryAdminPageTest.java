package org.csu.mycartoonstore.admin; // 分类维护页面：列表、新增、编辑、删除（AC-25/AC-26）。

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

// M07-04：依据 docs/06 AC-25/AC-26、docs/04 §3.2、docs/05 §4.9。
// 覆盖：树形列表（层级/计数/父名）、新增一级与二级、后缀与重名失败回表单、编辑只改名称与排序、
//       删除有子分类/有商品时 409、无引用可删、非法父级 404。
// 不覆盖：登录与拦截（AdminLoginTest）、商品页面（M07-06）。
@AutoConfigureMockMvc // 不占端口。
class CategoryAdminPageTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    @Test // 列表：一级、二级、父名与计数。
    @DisplayName("列表：两级缩进、父名与计数")
    void listShowsTree() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。
        jdbc.update("INSERT INTO product (sku, category_id, name, cover_image, price, stock, status, sort_order) "
                + "VALUES ('PAGE-P1', ?, '页面商品', 'images/x.svg', 10.00, 1, 'ON_SALE', 1)", leaf); // 一件商品。

        mockMvc.perform(get("/categories").session(adminSession())) // 管理员打开列表。
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("页面作品"))) // 一级名称。
                .andExpect(content().string(containsString("单行本"))) // 二级名称。
                .andExpect(content().string(containsString("PAGE-COMIC"))) // 二级编码。
                .andExpect(content().string(containsString("新增品类"))); // 一级行有新增二级入口。
    }

    @Test // 折叠：默认收起，不带 searched 标记。
    @DisplayName("列表：作品默认折叠")
    void listCollapsedByDefault() throws Exception {
        seedWorkAndChild(); // 一个作品 + 一个品类。

        mockMvc.perform(get("/categories").session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("work-group"))) // 有折叠分组。
                .andExpect(content().string(not(containsString("searched")))); // 没有搜索命中标记。
    }

    @Test // 搜索：只显示命中的作品。
    @DisplayName("列表：按作品名搜索")
    void listSearchByWorkName() throws Exception {
        seedWorkAndChild(); // PAGE / 页面作品。
        long otherTop = AdminFixture.createCategory(jdbc, "OTHER", null, "另一个作品", 2); // 干扰作品。
        AdminFixture.createCategory(jdbc, "OTHER-COMIC", otherTop, "单行本", 1); // 它的品类。

        mockMvc.perform(get("/categories").param("keyword", "页面").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("页面作品"))) // 命中。
                .andExpect(content().string(not(containsString("另一个作品")))) // 未命中。
                .andExpect(content().string(containsString("searched"))); // 搜索时默认展开。
    }

    @Test // 搜索无结果：空状态。
    @DisplayName("列表：搜索无结果 → 空状态")
    void listSearchEmpty() throws Exception {
        seedWorkAndChild(); // 有数据。

        mockMvc.perform(get("/categories").param("keyword", "不存在").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("没有匹配的作品"))) // 空状态文案。
                .andExpect(content().string(containsString("清空搜索"))); // 给出口。
    }

    @Test // 新增表单：一级模式。
    @DisplayName("新增表单：默认是一级")
    void newTopForm() throws Exception {
        mockMvc.perform(get("/categories/new").session(adminSession())) // 打开新增页。
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("新增作品"))) // 标题。
                .andExpect(content().string(containsString("name=\"code\""))); // 有编码输入。
    }

    @Test // 新增二级表单：带父级名称且是只读。
    @DisplayName("新增表单：带 parentId 时显示所属作品")
    void newChildForm() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。

        mockMvc.perform(get("/categories/new").param("parentId", String.valueOf(top)).session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("新增品类"))) // 标题。
                .andExpect(content().string(containsString("页面作品"))); // 父级名称。
    }

    @Test // 父级不是一级：404，不进入半截表单。
    @DisplayName("新增二级：父级不合法 → 404")
    void newChildBadParent() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。

        mockMvc.perform(get("/categories/new").param("parentId", String.valueOf(leaf)).session(adminSession()))
                .andExpect(status().isNotFound()); // 父级必须是 404。
    }

    @Test // AC-25：新增一级作品。
    @DisplayName("新增一级：入库成功并回列表（AC-25）")
    void createTop() throws Exception {
        mockMvc.perform(post("/categories").with(csrf()) // 提交新增。
                        .param("code", "NEWWORK") // 大写编码。
                        .param("name", "新作品") // 名称。
                        .param("sortOrder", "3") // 排序。
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/categories")); // 回列表。

        assertThat(count("SELECT COUNT(*) FROM category WHERE code = 'NEWWORK'")).isEqualTo(1); // 已入库。
        assertThat(jdbc.queryForObject("SELECT parent_id FROM category WHERE code = 'NEWWORK'", Long.class))
                .isNull(); // 一级没有父级。
    }

    @Test // AC-25：新增二级品类并挂在作品下。
    @DisplayName("新增二级：父级正确、编码后缀合法（AC-25）")
    void createChild() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "NEWWORK", null, "新作品", 1); // 一级父分类。

        mockMvc.perform(post("/categories").with(csrf())
                        .param("code", "NEWWORK-COMIC") // 二级编码。
                        .param("name", "单行本") // 名称。
                        .param("parentId", String.valueOf(top)) // 父级。
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/categories"));

        assertThat(jdbc.queryForObject("SELECT parent_id FROM category WHERE code = 'NEWWORK-COMIC'", Long.class))
                .isEqualTo(top); // 挂在一级下。
    }

    @Test // 后缀不合法：400 + 红字，且不写库。
    @DisplayName("新增二级：后缀不合法 → 400 回表单")
    void createChildBadSuffix() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "NEWWORK", null, "新作品", 1); // 一级。

        mockMvc.perform(post("/categories").with(csrf())
                        .param("code", "NEWWORK-BADGE") // 不是 -COMIC/-MERCH。
                        .param("name", "徽章")
                        .param("parentId", String.valueOf(top))
                        .session(adminSession()))
                .andExpect(status().isBadRequest()) // 参数错误 400。
                .andExpect(content().string(containsString("-COMIC"))); // 红字说明合法后缀。

        assertThat(count("SELECT COUNT(*) FROM category WHERE code = 'NEWWORK-BADGE'")).isZero(); // 未写库。
    }

    @Test // 编码重复：409 + 红字。
    @DisplayName("新增：编码重复 → 409 回表单")
    void createDuplicateCode() throws Exception {
        AdminFixture.createCategory(jdbc, "DUP", null, "已存在", 1); // 占住编码。

        mockMvc.perform(post("/categories").with(csrf())
                        .param("code", "DUP")
                        .param("name", "另一个")
                        .session(adminSession()))
                .andExpect(status().isConflict()) // 409。
                .andExpect(content().string(containsString("编码已存在"))); // 文案来自服务层。
    }

    @Test // 编辑页：带出当前值，编码只读。
    @DisplayName("编辑页：带出当前值且编码只读")
    void editForm() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 先建真实父级，满足外键。
        long id = AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 2); // 二级分类。

        mockMvc.perform(get("/categories/{id}/edit", id).session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("单行本"))) // 名称。
                .andExpect(content().string(containsString("readonly"))); // 编码只读。
    }

    @Test // 编辑：只改名称与排序，编码不被覆盖。
    @DisplayName("编辑：只改名称与排序，编码不变")
    void editSavesNameAndSort() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。

        mockMvc.perform(post("/categories/{id}", leaf).with(csrf()) // 提交编辑。
                        .param("code", "HACKED") // 故意改编码：服务端忽略。
                        .param("name", "单行本（新）") // 名称。
                        .param("sortOrder", "9") // 排序。
                        .param("parentId", "999") // 故意改父级：服务端忽略。
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/categories"));

        assertThat(jdbc.queryForObject("SELECT name FROM category WHERE id = ?", String.class, leaf))
                .isEqualTo("单行本（新）"); // 名称生效。
        assertThat(jdbc.queryForObject("SELECT sort_order FROM category WHERE id = ?", Integer.class, leaf))
                .isEqualTo(9); // 排序生效。
        assertThat(jdbc.queryForObject("SELECT code FROM category WHERE id = ?", String.class, leaf))
                .isEqualTo("PAGE-COMIC"); // 编码没被改。
        assertThat(jdbc.queryForObject("SELECT parent_id FROM category WHERE id = ?", Long.class, leaf))
                .isEqualTo(top); // 父级没被改。
    }

    @Test // 编辑后同级重名：409。
    @DisplayName("编辑：同级同名 → 409 回表单")
    void editDuplicateName() throws Exception {
        long first = AdminFixture.createCategory(jdbc, "A1", null, "同名", 1); // 第一个一级。
        long second = AdminFixture.createCategory(jdbc, "A2", null, "另一个", 2); // 第二个一级。

        mockMvc.perform(post("/categories/{id}", second).with(csrf()) // 把第二个改成和第一个同名。
                        .param("name", "同名")
                        .param("sortOrder", "2")
                        .session(adminSession()))
                .andExpect(status().isConflict()) // 409。
                .andExpect(content().string(containsString("同级已存在同名分类")));

        assertThat(first).isNotEqualTo(second); // 两个分类都在。
    }

    @Test // AC-26：有子分类不能删。
    @DisplayName("删除：有子分类 → 409（AC-26）")
    void deleteWithChildren() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。

        mockMvc.perform(post("/categories/{id}/delete", top).with(csrf()).session(adminSession()))
                .andExpect(status().isConflict()); // 409 错误页。

        assertThat(count("SELECT COUNT(*) FROM category WHERE id = " + top)).isEqualTo(1); // 没被删。
    }

    @Test // AC-26：有商品引用（含下架）不能删。
    @DisplayName("删除：有商品引用 → 409（AC-26）")
    void deleteWithProduct() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。
        jdbc.update("INSERT INTO product (sku, category_id, name, cover_image, price, stock, status, sort_order) "
                + "VALUES ('PAGE-D1', ?, '下架商品', 'images/x.svg', 10.00, 0, 'DRAFT', 1)", leaf); // 下架商品也算引用。

        mockMvc.perform(post("/categories/{id}/delete", leaf).with(csrf()).session(adminSession()))
                .andExpect(status().isConflict()); // 409。

        assertThat(count("SELECT COUNT(*) FROM category WHERE id = " + leaf)).isEqualTo(1); // 没被删。
    }

    @Test // 无引用：可以删。
    @DisplayName("删除：无引用 → 删除并回列表")
    void deleteLeaf() throws Exception {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "PAGE-PAPER", top, "色纸", 5); // 二级，无商品。

        mockMvc.perform(post("/categories/{id}/delete", leaf).with(csrf()).session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/categories"));

        assertThat(count("SELECT COUNT(*) FROM category WHERE id = " + leaf)).isZero(); // 已删除。
    }

    /** 造"页面作品 + 单行本" */
    private void seedWorkAndChild() {
        long top = AdminFixture.createCategory(jdbc, "PAGE", null, "页面作品", 1); // 一级。
        AdminFixture.createCategory(jdbc, "PAGE-COMIC", top, "单行本", 1); // 二级。
    }

    /** 管理员会话：拦截器只认 userId */
    private MockHttpSession adminSession() {
        long adminId = AdminFixture.createAdmin(jdbc, "cat_admin"); // 每个用例单独造，避免互相影响。
        return AdminFixture.session(adminId); // 带登录态。
    }

    /** 单值计数：用例里只关心 0 还是 1 */
    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class); // 计数查询。
        return value == null ? 0 : value; // 空按 0。
    }
}
