package org.csu.mycartoonstore.common.page;

import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    @DisplayName("缺省：页码 1，页大小取调用方默认值")
    void defaults() {
        PageQuery q = PageQuery.of(null, null, 6);
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.pageSize()).isEqualTo(6);
    }

    @Test
    @DisplayName("页码小于 1 归一到 1")
    void pageBelowOneIsNormalized() {
        assertThat(PageQuery.of("0", "10", 6).page()).isEqualTo(1);
        assertThat(PageQuery.of("-3", "10", 6).page()).isEqualTo(1);
    }

    @Test
    @DisplayName("页码非整数或溢出返回 400")
    void pageNotAnInteger() {
        assertThatThrownBy(() -> PageQuery.of("abc", "10", 6))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> PageQuery.of("999999999999", "10", 6))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("页大小越界返回 400")
    void pageSizeOutOfRange() {
        assertThatThrownBy(() -> PageQuery.of("1", "0", 6)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> PageQuery.of("1", "51", 6)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> PageQuery.of("1", "1.5", 6)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("偏移量按 (page-1)*pageSize 计算")
    void offset() {
        assertThat(PageQuery.of("3", "6", 6).offset()).isEqualTo(12L);
        assertThat(PageQuery.of("1", "50", 6).offset()).isZero();
    }
}
