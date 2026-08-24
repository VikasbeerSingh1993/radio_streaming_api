package com.radiostreaming.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PageResponse<T> {

    private List<T> items = List.of();
    private int page;
    private int size;
    private long total;
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        int total = all == null ? 0 : all.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        PageResponse<T> response = new PageResponse<>();
        response.items = total == 0 ? List.of() : new ArrayList<>(all.subList(from, to));
        response.page = safePage;
        response.size = safeSize;
        response.total = total;
        response.totalPages = total == 0 ? 1 : (int) Math.ceil(total / (double) safeSize);
        return response;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
