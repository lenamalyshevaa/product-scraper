package com.example.priceparser.model;

import java.util.List;

/**
 * Одна страница результатов и сведения о том, сколько их всего.
 */
public class PageResponse {

    private List<Product> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String sort;
    private String order;

    public PageResponse(List<Product> content, int page, int size, long totalElements, String sort, String order) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
        this.sort = sort;
        this.order = order;
    }

    public List<Product> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public String getSort() {
        return sort;
    }

    public String getOrder() {
        return order;
    }
}
