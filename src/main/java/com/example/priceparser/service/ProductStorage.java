package com.example.priceparser.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.example.priceparser.model.Product;
import org.springframework.stereotype.Component;

/**
 * Пока храним товары в памяти.
 * Запросы приходят из разных потоков Tomcat, поэтому список и счётчик потокобезопасные.
 */
@Component
public class ProductStorage {

    private final List<Product> products = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public Product save(Product product) {
        product.setId(nextId.getAndIncrement());
        products.add(product);
        return product;
    }

    public List<Product> findAll() {
        List<Product> result = new ArrayList<>(products);
        // новые сверху
        result.sort(Comparator.comparing(Product::getParsedAt).reversed());
        return result;
    }
}
