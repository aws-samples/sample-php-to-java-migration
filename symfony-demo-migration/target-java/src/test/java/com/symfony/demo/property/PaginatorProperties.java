package com.symfony.demo.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.service.Paginator;
import java.util.List;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link Paginator} bounds and slice arithmetic.
 *
 * <p>**Validates: Requirements 3.4**
 *
 * <p>Property 5: Paginator invariant (bounds + slice). For any total {@code N} (0..1000), any
 * {@code pageSize} (1..50), and any requested {@code page}, the {@link Paginator} reproduces the
 * PHP {@code App\Pagination\Paginator} arithmetic exactly:
 *
 * <ul>
 *   <li>the current page is clamped to {@code max(1, page)} and is therefore always {@code >= 1};
 *   <li>the result slice equals the expected sublist
 *       {@code [ (cp-1)*size , min(N, cp*size) )} (empty when the offset is past the end);
 *   <li>{@code lastPage == ceil(N / size)};
 *   <li>{@code hasNextPage} iff {@code cp < lastPage};
 *   <li>{@code hasPreviousPage} iff {@code cp > 1};
 *   <li>{@code previousPage} is clamped to {@code max(1, cp-1)} and {@code nextPage} to
 *       {@code min(lastPage, cp+1)};
 *   <li>{@code hasToPaginate} iff {@code N > size}.
 * </ul>
 *
 * <p>The requested page is drawn from a range wide enough to exercise clamping (below 1) and
 * beyond-the-end offsets, while staying within {@code int} bounds so the PHP-equivalent
 * {@code (cp-1)*size} offset arithmetic cannot overflow (which is not part of the invariant under
 * test).
 */
class PaginatorProperties {

    private static final String TAG =
            "Feature: symfony-demo-php-to-java-migration, Property 5: Paginator invariant (bounds + slice)";

    private static List<Integer> range(int size) {
        return IntStream.range(0, size).boxed().toList();
    }

    /**
     * **Validates: Requirements 3.4**
     *
     * <p>Full invariant check across total/pageSize/page: current-page clamping, slice bounds,
     * last-page ceiling, next/previous existence and clamping, and the paginate-needed flag.
     */
    @Property(tries = 1000)
    @Tag(TAG)
    void paginatorMatchesPhpBoundsAndSlice(
            @ForAll @IntRange(min = 0, max = 1000) int total,
            @ForAll @IntRange(min = 1, max = 50) int pageSize,
            @ForAll @IntRange(min = -50, max = 5000) int page) {

        List<Integer> all = range(total);

        Paginator<Integer> p = new Paginator<Integer>(pageSize).paginate(Paginator.fromList(all), page);

        int expectedCurrent = Math.max(1, page);
        int firstResult = (expectedCurrent - 1) * pageSize;
        int expectedLastPage = (int) Math.ceil((double) total / pageSize);

        List<Integer> expectedSlice;
        if (firstResult >= total || firstResult < 0) {
            expectedSlice = List.of();
        } else {
            int toIndex = Math.min(total, firstResult + pageSize);
            expectedSlice = all.subList(firstResult, toIndex);
        }

        assertThat(p.getCurrentPage())
                .as("currentPage must be clamped to max(1, page) for total=%d size=%d page=%d", total, pageSize, page)
                .isEqualTo(expectedCurrent)
                .isGreaterThanOrEqualTo(1);

        assertThat(p.getPageSize()).isEqualTo(pageSize);
        assertThat(p.getNumResults()).isEqualTo(total);

        assertThat(p.getResults())
                .as("result slice must equal sublist [%d, %d) for total=%d size=%d page=%d",
                        firstResult, firstResult + pageSize, total, pageSize, page)
                .containsExactlyElementsOf(expectedSlice);

        assertThat(p.getLastPage())
                .as("lastPage must be ceil(N/size) for total=%d size=%d", total, pageSize)
                .isEqualTo(expectedLastPage);

        assertThat(p.hasNextPage())
                .as("hasNextPage iff currentPage < lastPage")
                .isEqualTo(expectedCurrent < expectedLastPage);

        assertThat(p.hasPreviousPage())
                .as("hasPreviousPage iff currentPage > 1")
                .isEqualTo(expectedCurrent > 1);

        assertThat(p.getPreviousPage())
                .as("previousPage clamped to max(1, cp-1)")
                .isEqualTo(Math.max(1, expectedCurrent - 1));

        assertThat(p.getNextPage())
                .as("nextPage clamped to min(lastPage, cp+1)")
                .isEqualTo(Math.min(expectedLastPage, expectedCurrent + 1));

        assertThat(p.hasToPaginate())
                .as("hasToPaginate iff numResults > pageSize")
                .isEqualTo((long) total > pageSize);
    }
}
