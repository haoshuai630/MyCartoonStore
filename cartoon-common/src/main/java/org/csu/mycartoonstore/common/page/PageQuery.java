package org.csu.mycartoonstore.common.page;

import org.csu.mycartoonstore.common.exception.BizException;

/**
 * 分页参数：把请求里的字符串页码/页大小归一成受控整数。
 *
 * <p>统一口径（与 04 篇 §4 一致）：</p>
 * <ul>
 *   <li>页码缺省为 1；小于 1 的整数归一为 1；非整数或溢出 → 400。</li>
 *   <li>页大小缺省由调用方给（商品列表 6，其余 10）；只允许 1–50，越界或格式错 → 400。</li>
 * </ul>
 */
public record PageQuery(int page, int pageSize) {

    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 50;

    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize out of range");
        }
    }

    /**
     * 解析请求参数。
     *
     * @param rawPage         原始页码，可为 null/空白
     * @param rawPageSize     原始页大小，可为 null/空白
     * @param defaultPageSize 缺省页大小
     */
    public static PageQuery of(String rawPage, String rawPageSize, int defaultPageSize) {
        int page = parsePage(rawPage);
        int pageSize = parsePageSize(rawPageSize, defaultPageSize);
        return new PageQuery(page, pageSize);
    }

    /** 数据库偏移量，使用 long 防止 (page-1)*pageSize 溢出。 */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest("页码必须是整数");
        }
        return Math.max(value, 1);
    }

    private static int parsePageSize(String raw, int defaultPageSize) {
        if (raw == null || raw.isBlank()) {
            return requireValidDefault(defaultPageSize);
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest("每页数量必须是整数");
        }
        if (value < MIN_PAGE_SIZE || value > MAX_PAGE_SIZE) {
            throw BizException.badRequest("每页数量必须在 " + MIN_PAGE_SIZE + "–" + MAX_PAGE_SIZE + " 之间");
        }
        return value;
    }

    private static int requireValidDefault(int defaultPageSize) {
        if (defaultPageSize < MIN_PAGE_SIZE || defaultPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("defaultPageSize out of range: " + defaultPageSize);
        }
        return defaultPageSize;
    }
}
