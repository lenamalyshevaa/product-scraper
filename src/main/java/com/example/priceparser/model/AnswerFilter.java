package com.example.priceparser.model;

import java.math.BigDecimal;

/**
 * Параметры запроса GET /answer. Все необязательные.
 */
public class AnswerFilter {

    // поиск по названию и описанию
    private String q;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String currency;
    // часть адреса сайта, например labirint или book24
    private String site;
    // true - только последний обход каждой ссылки, false - вся история
    private boolean latestOnly = true;

    // сортировка: parsedAt, price, title, publishedAt; порядок: asc или desc
    private String sort = "parsedAt";
    private String order = "desc";
    // номер страницы с нуля и размер страницы
    private int page = 0;
    private int size = 20;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public boolean isLatestOnly() {
        return latestOnly;
    }

    public void setLatestOnly(boolean latestOnly) {
        this.latestOnly = latestOnly;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
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
}
