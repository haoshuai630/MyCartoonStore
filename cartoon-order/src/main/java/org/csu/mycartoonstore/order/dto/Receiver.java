package org.csu.mycartoonstore.order.dto; // 校验通过的收货信息：下单时直接写进订单快照。

import lombok.Getter; // 生成 getter，字段只读。

import java.io.Serial; // 序列化成员标记。
import java.io.Serializable; // 可能随快照一起进 Session，保持可序列化。

// M05-01：依据 docs/01 FR-O-01 的收货字段规则。
// 只负责携带"已经校验并去空白"的三项；校验逻辑在 ReceiverForm。
@Getter // 三项都只读：构造后不再修改。
public class Receiver implements Serializable {

    @Serial // 序列化相关字段的标记。
    private static final long serialVersionUID = 1L; // 会话序列化版本号。

    /** 收货人，去首尾空白后 1–50 字符 */
    private final String name;

    /** 联系电话，去首尾空白后 1–30 字符；不限定手机号格式 */
    private final String phone;

    /** 详细地址，去首尾空白后 1–255 字符，单一完整地址文本 */
    private final String address;

    /** 三项都已校验：这里不再重复判断。 */
    public Receiver(String name, String phone, String address) {
        this.name = name; // 收货人快照。
        this.phone = phone; // 联系电话快照。
        this.address = address; // 地址快照。
    }
}
