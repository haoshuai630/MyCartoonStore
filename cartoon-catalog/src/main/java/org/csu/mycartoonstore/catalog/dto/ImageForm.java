package org.csu.mycartoonstore.catalog.dto; // 相册表单：路径、说明与排序的校验与归一。

import org.csu.mycartoonstore.common.exception.BizException; // 校验失败转 400，文案带字段名。

// M08-05：依据 docs/01 FR-A-07（相册维护）与 docs/03 §2.4 的列宽（255 / 120）。
// 负责：去空白、长度校验、站点内路径判断与排序解析，产出可直接写库的三项；
// 不做：图片文件是否存在（只维护记录）、商品/图片归属判断（在 Service）。
public record ImageForm(String imagePath, String altText, int sortOrder) {

    /** 路径上限，与 image_path VARCHAR(255) 一致 */
    private static final int PATH_MAX = 255;

    /** 说明上限，与 alt_text VARCHAR(120) 一致 */
    private static final int ALT_MAX = 120;

    /** 排序上限：教学场景足够，避免误填把 INT 撑爆 */
    private static final int SORT_MAX = 999_999;

    /**
     * 解析并校验表单：路径必填且必须是站内路径，说明可空，排序可空（默认 0）。
     */
    public static ImageForm parse(String rawPath, String rawAlt, String rawSort) {
        String path = rawPath == null ? "" : rawPath.strip(); // null 当空串，只在这里判一次。
        if (path.isEmpty()) {
            throw BizException.badRequest("图片路径不能为空"); // 路径是唯一必填项。
        }
        if (path.length() > PATH_MAX) {
            throw BizException.badRequest("图片路径不能超过 " + PATH_MAX + " 个字符"); // 超长拒绝而不是截断。
        }
        if (path.contains("://") || path.startsWith("//")) {
            throw BizException.badRequest("图片路径只接受站内路径"); // 外链留到 docs/12 的图片接入。
        }
        String alt = rawAlt == null ? "" : rawAlt.strip(); // 说明可空。
        if (alt.length() > ALT_MAX) {
            throw BizException.badRequest("图片说明不能超过 " + ALT_MAX + " 个字符"); // 与列宽一致。
        }
        return new ImageForm(path, alt.isEmpty() ? null : alt, parseSort(rawSort)); // 空说明存 null。
    }

    /** 排序：空按 0，必须是 0–999999 的整数。 */
    private static int parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0; // 不填就排最前。
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim()); // 只接受整数。
        } catch (NumberFormatException e) {
            throw BizException.badRequest("排序必须是非负整数"); // 小数/文字都拒绝。
        }
        if (value < 0 || value > SORT_MAX) {
            throw BizException.badRequest("排序必须在 0–" + SORT_MAX + " 之间"); // 负数与溢出直接拒。
        }
        return value; // 合法排序。
    }
}
