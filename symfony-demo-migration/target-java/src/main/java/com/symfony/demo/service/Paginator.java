package com.symfony.demo.service;

import java.util.List;

/**
 * Framework-light port of the Symfony Demo {@code App\Pagination\Paginator}.
 *
 * <p>Behavioral parity notes (see {@code source-php/src/Pagination/Paginator.php}):
 * <ul>
 *   <li>Default page size is {@link #PAGE_SIZE} = 10, matching {@code Paginator::PAGE_SIZE}.</li>
 *   <li>The requested page is clamped with {@code max(1, page)} so the current page is never
 *       below 1 (PHP {@code $this->currentPage = max(1, $page)}).</li>
 *   <li>The first result offset is {@code (currentPage - 1) * pageSize}
 *       (PHP {@code $firstResult = ($this->currentPage - 1) * $this->pageSize}).</li>
 *   <li>Last page is {@code ceil(numResults / pageSize)} (integer via ceiling of float division),
 *       so an empty result set yields last page 0, exactly like PHP.</li>
 *   <li>{@code hasPreviousPage}, {@code getPreviousPage}, {@code hasNextPage}, {@code getNextPage}
 *       and {@code hasToPaginate} reproduce the PHP clamping/bounds arithmetic verbatim.</li>
 * </ul>
 *
 * <p>The PHP class wraps a Doctrine {@code QueryBuilder}; to keep this port easy to unit- and
 * property-test it depends only on a small {@link PageSource} abstraction that supplies the total
 * count and the page slice. Repositories (or an in-memory list via {@link #fromList(List)}) provide
 * the data; no Spring or ORM types leak into this class.
 *
 * @param <T> the element type of the paginated results
 */
public final class Paginator<T> {

    /**
     * Default page size, mirroring {@code Paginator::PAGE_SIZE} in the PHP source.
     */
    public static final int PAGE_SIZE = 10;

    /**
     * Supplies the total number of results and a slice of them. Mirrors the two things the PHP
     * code derives from the Doctrine {@code Paginator}: {@code count()} and {@code getIterator()}
     * over a query configured with {@code setFirstResult}/{@code setMaxResults}.
     *
     * @param <T> the element type
     */
    public interface PageSource<T> {

        /**
         * @return total number of results across all pages (PHP {@code $paginator->count()})
         */
        long count();

        /**
         * @param firstResult zero-based offset of the first item (PHP {@code setFirstResult})
         * @param maxResults   maximum number of items to return (PHP {@code setMaxResults})
         * @return the results for the requested window
         */
        List<T> slice(int firstResult, int maxResults);
    }

    private final int pageSize;

    private int currentPage = 1;
    private long numResults;
    private List<T> results = List.of();

    /**
     * Creates a paginator using the default {@link #PAGE_SIZE}.
     */
    public Paginator() {
        this(PAGE_SIZE);
    }

    /**
     * Creates a paginator with an explicit page size, mirroring the PHP constructor's optional
     * {@code $pageSize} argument.
     *
     * @param pageSize the page size (must be positive)
     */
    public Paginator(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1, got " + pageSize);
        }
        this.pageSize = pageSize;
    }

    /**
     * Paginates the first page ({@code page = 1}), matching the PHP default argument.
     *
     * @param source the data source
     * @return this paginator, populated
     */
    public Paginator<T> paginate(PageSource<T> source) {
        return paginate(source, 1);
    }

    /**
     * Populates the paginator's metadata and result slice for the requested page.
     *
     * <p>Reproduces PHP {@code Paginator::paginate}: clamps the page to {@code >= 1}, computes the
     * first-result offset, then fetches the count and slice from the source.
     *
     * @param source the data source
     * @param page   the requested (1-based) page number
     * @return this paginator, populated
     */
    public Paginator<T> paginate(PageSource<T> source, int page) {
        this.currentPage = Math.max(1, page);
        int firstResult = (this.currentPage - 1) * this.pageSize;

        this.numResults = source.count();
        this.results = source.slice(firstResult, this.pageSize);

        return this;
    }

    /**
     * Convenience factory that paginates an already-materialized list in memory.
     *
     * @param all  the full list of items
     * @param page the requested (1-based) page number
     * @param <T>  the element type
     * @return a populated paginator
     */
    public static <T> Paginator<T> ofList(List<T> all, int page) {
        return new Paginator<T>().paginate(fromList(all), page);
    }

    /**
     * Adapts an in-memory list to a {@link PageSource}, slicing with the same offset/limit
     * semantics Doctrine applies (offset beyond the end yields an empty slice).
     *
     * @param all the full list of items
     * @param <T> the element type
     * @return a page source over the list
     */
    public static <T> PageSource<T> fromList(List<T> all) {
        return new PageSource<>() {
            @Override
            public long count() {
                return all.size();
            }

            @Override
            public List<T> slice(int firstResult, int maxResults) {
                if (firstResult >= all.size() || firstResult < 0) {
                    return List.of();
                }
                int toIndex = Math.min(all.size(), firstResult + maxResults);
                return List.copyOf(all.subList(firstResult, toIndex));
            }
        };
    }

    /**
     * @return the clamped current page (PHP {@code getCurrentPage})
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * @return the last page number, {@code ceil(numResults / pageSize)}; 0 when there are no
     *         results (PHP {@code getLastPage})
     */
    public int getLastPage() {
        return (int) Math.ceil((double) numResults / pageSize);
    }

    /**
     * @return the configured page size (PHP {@code getPageSize})
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @return whether a previous page exists (PHP {@code hasPreviousPage})
     */
    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    /**
     * @return the previous page, clamped to at least 1 (PHP {@code getPreviousPage})
     */
    public int getPreviousPage() {
        return Math.max(1, currentPage - 1);
    }

    /**
     * @return whether a next page exists (PHP {@code hasNextPage})
     */
    public boolean hasNextPage() {
        return currentPage < getLastPage();
    }

    /**
     * @return the next page, clamped to at most the last page (PHP {@code getNextPage})
     */
    public int getNextPage() {
        return Math.min(getLastPage(), currentPage + 1);
    }

    /**
     * @return whether pagination controls are needed, i.e. results exceed one page
     *         (PHP {@code hasToPaginate})
     */
    public boolean hasToPaginate() {
        return numResults > pageSize;
    }

    /**
     * @return the total number of results (PHP {@code getNumResults})
     */
    public long getNumResults() {
        return numResults;
    }

    /**
     * @return the results for the current page (PHP {@code getResults})
     */
    public List<T> getResults() {
        return results;
    }
}
