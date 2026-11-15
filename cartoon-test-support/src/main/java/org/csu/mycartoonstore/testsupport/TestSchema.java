package org.csu.mycartoonstore.testsupport;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 测试库初始化：每次用例前把七张表删掉重建，保证测试之间互不影响。
 *
 * <p>只操作 {@code MyCartoonStore_test}，绝不碰演示库。删除顺序按外键依赖从子到父。</p>
 */
public final class TestSchema {

    /** 先删子表，再删父表，避免外键 RESTRICT 阻塞 */
    private static final String[] DROP_ORDER = {
            "sales_order_item",
            "sales_order",
            "cart_item",
            "product_image",
            "product",
            "category",
            "store_user"
    };

    private TestSchema() {
    }

    public static void reset(DataSource dataSource) {
        dropTables(dataSource);
        runSchema(dataSource);
    }

    private static void dropTables(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String table : DROP_ORDER) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("清理测试库失败", e);
        }
    }

    private static void runSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(new ClassPathResource("sql/schema-test.sql"), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("初始化测试库失败", e);
        }
    }
}
