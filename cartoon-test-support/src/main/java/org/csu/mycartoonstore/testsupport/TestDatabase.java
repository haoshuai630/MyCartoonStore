package org.csu.mycartoonstore.testsupport;

/**
 * 集成测试库常量。测试库与演示库分离，避免测试污染演示数据。
 *
 * <p>约定：集成测试使用 {@code MyCartoonStore_test}，由测试基座在用例前重建。</p>
 */
public final class TestDatabase {

    public static final String NAME = "MyCartoonStore_test";

    public static final String URL =
            "jdbc:mysql://localhost:3306/" + NAME
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true";

    private TestDatabase() {
    }
}
