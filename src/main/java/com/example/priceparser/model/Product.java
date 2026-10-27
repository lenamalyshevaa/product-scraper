package com.example.priceparser.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Product {

    private Long id;
    private String url;
    private String title;
    private String description;
    private BigDecimal price;
    private String currency;
    private String availability;
    private Instant publishedAt;
    private Instant parsedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(Instant parsedAt) {
        this.parsedAt = parsedAt;
    }
}
