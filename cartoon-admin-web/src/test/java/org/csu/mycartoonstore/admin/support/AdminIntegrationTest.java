package org.csu.mycartoonstore.admin.support; // 管理端测试基类：完整后台上下文 + 每个用例前重建表。

import org.csu.mycartoonstore.testsupport.TestSchema; // 建表脚本的执行器。
import org.junit.jupiter.api.BeforeEach; // 每个用例前执行。
import org.springframework.beans.factory.annotation.Autowired; // 注入数据源。
import org.springframework.boot.test.context.SpringBootTest; // 启动完整上下文。
import org.springframework.test.context.ActiveProfiles; // 用 test 配置。

import javax.sql.DataSource; // 重建表要用。

// M07-02：管理端第一份集成测试基座。
// 与用户端同构，但单独放在 admin 模块：不把已验收的 175 个用例基线绑在一起改。
// 是否上移到 cartoon-test-support 留到 M09 统一评估。
@SpringBootTest // 启动 CartoonAdminApplication。
@ActiveProfiles("test") // 连 MyCartoonStore_test。
public abstract class AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    protected DataSource dataSource; // 测试库连接池。

    /** 每个用例前重建七张表，避免用例之间互相污染。 */
    @BeforeEach
    void resetSchema() {
        TestSchema.reset(dataSource); // 删表 + 建表。
    }
}
