package org.csu.mycartoonstore.admin;

import org.csu.mycartoonstore.admin.support.AdminFixture;
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 顾客管理页测试：只列顾客、筛选、启停、重复操作与管理端越权。
 */
@AutoConfigureMockMvc
class AdminUserPageTest extends AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("未登录访问顾客列表：302 到管理端登录页")
    void usersRequireLogin() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fusers*"));
    }

    @Test
    @DisplayName("列表只列 CUSTOMER：管理员账号不出现")
    void listsCustomersOnly() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        AdminFixture.createCustomer(jdbc, "alice");
        AdminFixture.createUser(jdbc, "bob", "CUSTOMER", "DISABLED");

        mockMvc.perform(get("/users").session(AdminFixture.session(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice")))
                .andExpect(content().string(containsString("bob")))
                .andExpect(content().string(not(containsString("rootadmin"))));
    }

    @Test
    @DisplayName("关键字筛选：只显示命中的顾客")
    void filtersByKeyword() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        AdminFixture.createCustomer(jdbc, "alice");
        AdminFixture.createCustomer(jdbc, "bob");

        mockMvc.perform(get("/users").param("keyword", "ali").session(AdminFixture.session(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice")))
                .andExpect(content().string(not(containsString("bob"))));
    }

    @Test
    @DisplayName("状态筛选：只看禁用")
    void filtersByStatus() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        AdminFixture.createCustomer(jdbc, "alice");
        AdminFixture.createUser(jdbc, "bob", "CUSTOMER", "DISABLED");

        mockMvc.perform(get("/users").param("status", "DISABLED").session(AdminFixture.session(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bob")))
                .andExpect(content().string(not(containsString("alice"))));
    }

    @Test
    @DisplayName("禁用顾客：回列表提示，库里状态改变")
    void disablesCustomer() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long aliceId = AdminFixture.createCustomer(jdbc, "alice");

        mockMvc.perform(post("/users/" + aliceId + "/status").param("status", "DISABLED")
                        .session(AdminFixture.session(adminId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("message", "已禁用该账号"));

        assertThat(statusOf(aliceId)).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重复禁用：提示当前已是禁用状态，不重复写")
    void repeatDisableIsIdempotent() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long aliceId = AdminFixture.createUser(jdbc, "alice", "CUSTOMER", "DISABLED");

        mockMvc.perform(post("/users/" + aliceId + "/status").param("status", "DISABLED")
                        .session(AdminFixture.session(adminId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "该账号当前已是禁用状态"));

        assertThat(statusOf(aliceId)).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重新启用：禁用账号恢复为 ENABLED")
    void enablesCustomer() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long bobId = AdminFixture.createUser(jdbc, "bob", "CUSTOMER", "DISABLED");

        mockMvc.perform(post("/users/" + bobId + "/status").param("status", "ENABLED")
                        .session(AdminFixture.session(adminId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "已启用该账号"));

        assertThat(statusOf(bobId)).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("启用/禁用管理员：404，不写库")
    void cannotToggleAdmin() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");

        mockMvc.perform(post("/users/" + adminId + "/status").param("status", "DISABLED")
                        .session(AdminFixture.session(adminId)))
                .andExpect(status().isNotFound());

        assertThat(statusOf(adminId)).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("启停状态非法：400")
    void invalidStatusRejected() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long aliceId = AdminFixture.createCustomer(jdbc, "alice");

        mockMvc.perform(post("/users/" + aliceId + "/status").param("status", "BANNED")
                        .session(AdminFixture.session(adminId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("筛选状态非法：400")
    void invalidFilterRejected() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");

        mockMvc.perform(get("/users").param("status", "BANNED").session(AdminFixture.session(adminId)))
                .andExpect(status().isBadRequest());
    }

    /** 现查账号状态，断言启停确实落库。 */
    private String statusOf(long userId) {
        return jdbc.queryForObject("SELECT status FROM store_user WHERE id = ?", String.class, userId);
    }
}
