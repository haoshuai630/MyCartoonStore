package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试基座自检：证明 {@link AbstractIntegrationTest} 能连上测试库、建出七张表。
 */
class SchemaSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("测试库存在且恰好七张表")
    void sevenTables() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Integer.class);
        assertThat(count).isEqualTo(7);
    }

    @Test
    @DisplayName("建表后是空库，不含演示数据")
    void tablesAreEmpty() {
        Integer products = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class);
        Integer users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM store_user", Integer.class);
        assertThat(products).isZero();
        assertThat(users).isZero();
    }

    @Test
    @DisplayName("生成列 parent_key 存在且是 STORED")
    void generatedColumnExists() {
        String extra = jdbcTemplate.queryForObject(
                "SELECT extra FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'category' AND column_name = 'parent_key'",
                String.class);
        assertThat(extra).containsIgnoringCase("STORED GENERATED");
    }
}
