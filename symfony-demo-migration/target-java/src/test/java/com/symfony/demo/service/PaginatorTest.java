package com.symfony.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Unit tests pinning the {@link Paginator} metadata to the PHP {@code App\Pagination\Paginator}
 * behavior (page size, page clamping, last-page/next/previous arithmetic, slice bounds).
 */
class PaginatorTest {

    private static List<Integer> range(int size) {
        return IntStream.range(0, size).boxed().toList();
    }

    @Test
    void defaultPageSizeMatchesPhpConstant() {
        assertThat(Paginator.PAGE_SIZE).isEqualTo(10);
        assertThat(new Paginator<>().getPageSize()).isEqualTo(10);
    }

    @Test
    void firstPageSlicesFirstPageSizeItems() {
        Paginator<Integer> p = Paginator.ofList(range(25), 1);

        assertThat(p.getCurrentPage()).isEqualTo(1);
        assertThat(p.getNumResults()).isEqualTo(25);
        assertThat(p.getPageSize()).isEqualTo(10);
        assertThat(p.getResults()).containsExactlyElementsOf(range(10));
        assertThat(p.getLastPage()).isEqualTo(3); // ceil(25/10)
        assertThat(p.hasPreviousPage()).isFalse();
        assertThat(p.getPreviousPage()).isEqualTo(1); // clamped
        assertThat(p.hasNextPage()).isTrue();
        assertThat(p.getNextPage()).isEqualTo(2);
        assertThat(p.hasToPaginate()).isTrue();
    }

    @Test
    void lastPageSlicesRemainder() {
        Paginator<Integer> p = Paginator.ofList(range(25), 3);

        assertThat(p.getCurrentPage()).isEqualTo(3);
        assertThat(p.getResults()).containsExactly(20, 21, 22, 23, 24);
        assertThat(p.hasNextPage()).isFalse();
        assertThat(p.getNextPage()).isEqualTo(3); // clamped to last page
        assertThat(p.hasPreviousPage()).isTrue();
        assertThat(p.getPreviousPage()).isEqualTo(2);
    }

    @Test
    void pageBelowOneIsClampedToOne() {
        Paginator<Integer> p = Paginator.ofList(range(25), 0);
        assertThat(p.getCurrentPage()).isEqualTo(1);
        assertThat(p.getResults()).containsExactlyElementsOf(range(10));

        Paginator<Integer> negative = Paginator.ofList(range(25), -5);
        assertThat(negative.getCurrentPage()).isEqualTo(1);
    }

    @Test
    void pageBeyondEndYieldsEmptySlice() {
        Paginator<Integer> p = Paginator.ofList(range(25), 99);
        assertThat(p.getCurrentPage()).isEqualTo(99);
        assertThat(p.getResults()).isEmpty();
        assertThat(p.getLastPage()).isEqualTo(3);
        assertThat(p.hasNextPage()).isFalse();
        assertThat(p.getNextPage()).isEqualTo(3);
    }

    @Test
    void emptyResultsProduceLastPageZeroLikePhp() {
        Paginator<Integer> p = Paginator.ofList(List.of(), 1);

        assertThat(p.getNumResults()).isEqualTo(0);
        assertThat(p.getLastPage()).isEqualTo(0); // (int) ceil(0/10)
        assertThat(p.getResults()).isEmpty();
        assertThat(p.hasNextPage()).isFalse();
        assertThat(p.getNextPage()).isEqualTo(0); // min(0, 2)
        assertThat(p.hasToPaginate()).isFalse();
    }

    @Test
    void singleFullPageDoesNotNeedPagination() {
        Paginator<Integer> p = Paginator.ofList(range(10), 1);

        assertThat(p.getLastPage()).isEqualTo(1);
        assertThat(p.hasToPaginate()).isFalse(); // numResults (10) > pageSize (10) is false
        assertThat(p.hasNextPage()).isFalse();
    }

    @Test
    void customPageSizeIsHonored() {
        Paginator<Integer> p = new Paginator<Integer>(5).paginate(Paginator.fromList(range(12)), 2);

        assertThat(p.getPageSize()).isEqualTo(5);
        assertThat(p.getResults()).containsExactly(5, 6, 7, 8, 9);
        assertThat(p.getLastPage()).isEqualTo(3); // ceil(12/5)
    }

    @Test
    void invalidPageSizeRejected() {
        assertThatThrownBy(() -> new Paginator<>(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
