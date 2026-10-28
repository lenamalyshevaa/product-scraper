package com.example.priceparser.service;

import java.util.ArrayList;
import java.util.List;

public class NotProductException extends RuntimeException {

    // если это страница каталога, здесь ссылки на товары с неё
    private final List<String> productLinks;

    public NotProductException(String message) {
        this(message, new ArrayList<>());
    }

    public NotProductException(String message, List<String> productLinks) {
        super(message);
        this.productLinks = productLinks;
    }

    public List<String> getProductLinks() {
        return productLinks;
    }
}
