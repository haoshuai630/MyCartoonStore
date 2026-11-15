package org.csu.mycartoonstore.web.support;

import org.csu.mycartoonstore.testsupport.TestSchema;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

/**
 * 集成测试基类：启动完整用户端上下文 + 使用 test 配置 + 每个用例前重建七张表。
 *
 * <p>子类放在 {@code org.csu.mycartoonstore.web} 及其子包下，Spring Boot 才能向上找到启动类。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void resetSchema() {
        TestSchema.reset(dataSource);
    }
}
