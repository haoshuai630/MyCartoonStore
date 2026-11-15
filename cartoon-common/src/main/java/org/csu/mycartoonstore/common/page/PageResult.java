package org.csu.mycartoonstore.common.page;

import java.util.List;

/**
 * 分页结果：页内数据 + 总数 + 当前页信息。
 *
 * <p>与 {@link PageQuery} 配套使用：查询条件由 PageQuery 归一，返回由 PageResult 承载。</p>
 *
 * @param items    当前页数据
 * @param total    符合条件的总数
 * @param page     当前页码（从 1 开始）
 * @param pageSize 每页数量
 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {

    /** 由查询条件与总数构造，保证页码/页大小与查询一致。 */
    public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
        return new PageResult<>(items, total, query.page(), query.pageSize());
    }

    /** 总页数；无数据时为 0 */
    public int totalPages() {
        if (total <= 0) {
            return 0;
        }
        return (int) ((total + pageSize - 1) / pageSize);
    }

    /** 是否还有上一页，模板据此决定是否渲染「上一页」 */
    public boolean hasPrevious() {
        return page > 1;
    }

    /** 是否还有下一页 */
    public boolean hasNext() {
        return page < totalPages();
    }

    /** 当前页没有任何数据 */
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
