package com.dbtool.core.query;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded sliding-window page cache for virtualized tabular browsing and diff display.
 * Retains only up to {@code maxCachedPages} (default: {@code maxCachedRows / pageSize})
 * in memory using LRU eviction so large datasets never force eager full-table materialization.
 */
public class PagedResultWindow {

    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int DEFAULT_MAX_CACHED_ROWS = 2000;

    @FunctionalInterface
    public interface PageFetcher {
        List<RowData> fetchPage(int offset, int limit) throws SQLException;
    }

    private final List<String> columnNames;
    private final int totalRowCount;
    private final int pageSize;
    private final int maxCachedPages;
    private final PageFetcher fetcher;
    private final Map<Integer, List<RowData>> pageCache;

    public PagedResultWindow(List<String> columnNames, int totalRowCount, PageFetcher fetcher) {
        this(columnNames, totalRowCount, DEFAULT_PAGE_SIZE, DEFAULT_MAX_CACHED_ROWS, fetcher);
    }

    public PagedResultWindow(
            List<String> columnNames,
            int totalRowCount,
            int pageSize,
            int maxCachedRows,
            PageFetcher fetcher
    ) {
        this.columnNames = columnNames != null
                ? Collections.unmodifiableList(new ArrayList<>(columnNames))
                : Collections.emptyList();
        this.totalRowCount = Math.max(0, totalRowCount);
        this.pageSize = Math.max(1, pageSize);
        int effectiveMaxRows = Math.max(this.pageSize, maxCachedRows);
        this.maxCachedPages = Math.max(1, (int) Math.ceil((double) effectiveMaxRows / this.pageSize));
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher must not be null");

        final int maxPages = this.maxCachedPages;
        this.pageCache = Collections.synchronizedMap(new LinkedHashMap<Integer, List<RowData>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, List<RowData>> eldest) {
                return size() > maxPages;
            }
        });
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public int getColumnCount() {
        return columnNames.size();
    }

    public int getTotalRowCount() {
        return totalRowCount;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getMaxCachedPages() {
        return maxCachedPages;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= totalRowCount || columnIndex < 0 || columnIndex >= columnNames.size()) {
            return null;
        }
        RowData row = getRow(rowIndex);
        return row != null ? row.getValue(columnIndex) : null;
    }

    public RowData getRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= totalRowCount) {
            return null;
        }
        int pageIdx = rowIndex / pageSize;
        int offsetInPage = rowIndex % pageSize;
        List<RowData> page = loadPageIfAbsent(pageIdx);
        if (page == null || offsetInPage >= page.size()) {
            return null;
        }
        return page.get(offsetInPage);
    }

    public void updateCachedCell(int rowIndex, int columnIndex, Object newValue) {
        if (rowIndex < 0 || rowIndex >= totalRowCount || columnIndex < 0 || columnIndex >= columnNames.size()) {
            return;
        }
        int pageIdx = rowIndex / pageSize;
        int offsetInPage = rowIndex % pageSize;
        List<RowData> page = pageCache.get(pageIdx);
        if (page != null && offsetInPage < page.size()) {
            RowData old = page.get(offsetInPage);
            Object[] updated = new Object[Math.max(old.size(), columnNames.size())];
            for (int i = 0; i < old.size(); i++) {
                updated[i] = old.getValue(i);
            }
            updated[columnIndex] = newValue;
            page.set(offsetInPage, new RowData(updated));
        }
    }

    public void prefetchPageForRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < totalRowCount) {
            loadPageIfAbsent(rowIndex / pageSize);
        }
    }

    public int getCachedPageCount() {
        return pageCache.size();
    }

    public int getCachedRowCount() {
        synchronized (pageCache) {
            int count = 0;
            for (List<RowData> page : pageCache.values()) {
                count += page.size();
            }
            return count;
        }
    }

    public void invalidateCache() {
        pageCache.clear();
    }

    private List<RowData> loadPageIfAbsent(int pageIndex) {
        List<RowData> cached = pageCache.get(pageIndex);
        if (cached != null) {
            return cached;
        }
        int offset = pageIndex * pageSize;
        int limit = Math.min(pageSize, Math.max(0, totalRowCount - offset));
        if (limit <= 0) {
            return Collections.emptyList();
        }
        try {
            List<RowData> fetched = fetcher.fetchPage(offset, limit);
            List<RowData> mutable = fetched != null ? new ArrayList<>(fetched) : new ArrayList<>();
            pageCache.put(pageIndex, mutable);
            return mutable;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch result page offset=" + offset + ": " + e.getMessage(), e);
        }
    }
}
