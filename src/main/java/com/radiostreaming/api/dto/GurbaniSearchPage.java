package com.radiostreaming.api.dto;

import java.util.ArrayList;
import java.util.List;

public class GurbaniSearchPage {

    private List<GurbaniSearchHit> items = new ArrayList<>();
    private long total;
    private int page;
    private int size;
    private boolean available;
    private String message;

    public List<GurbaniSearchHit> getItems() {
        return items;
    }

    public void setItems(List<GurbaniSearchHit> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
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

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
