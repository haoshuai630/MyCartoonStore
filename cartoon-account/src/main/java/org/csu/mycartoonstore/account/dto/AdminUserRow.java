package org.csu.mycartoonstore.account.dto; // 后台顾客列表的一行。

import lombok.Getter; // 生成 getter。
import lombok.Setter; // 模板只读，MyBatis/服务映射用 setter。

import java.time.LocalDateTime; // 注册时间。

// M08-04：依据 docs/01 FR-A-07（只列 CUSTOMER、按 ID 升序）。
// 页面只展示这五列，不把密码散列等敏感列带出 Service。
@Getter // 读取。
@Setter // 由服务组装。
public class AdminUserRow {

    /** 用户 id：启停表单要带它 */
    private Long id;

    /** 登录名：创建后不可改，也是搜索字段 */
    private String username;

    /** 显示名：顾客可自行修改 */
    private String displayName;

    /** 邮箱：M10-02 起可在后台编辑，可空 */
    private String email;

    /** 电话：M10-02 起可在后台编辑，可空 */
    private String phone;

    /** ENABLED / DISABLED */
    private String status;

    /** 注册时间：列表按 id 升序，时间只用于展示 */
    private LocalDateTime createdAt;
}
