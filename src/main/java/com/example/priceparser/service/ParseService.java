package com.example.priceparser.service;

import java.time.Instant;
import java.util.List;

import com.example.priceparser.model.Product;
import com.example.priceparser.parser.ProductPageParser;
import org.springframework.stereotype.Service;

@Service
public class ParseService {

    private final PageLoader pageLoader;
    private final ProductPageParser parser;
    private final ProductStorage storage;

    public ParseService(PageLoader pageLoader, ProductPageParser parser, ProductStorage storage) {
        this.pageLoader = pageLoader;
        this.parser = parser;
        this.storage = storage;
    }

    public Product parse(String url) {
        PageLoader.LoadedPage page = pageLoader.load(url);
        Product product = parser.parse(url, page.html(), page.lastModified());

        if (product == null) {
            List<String> links = parser.findProductLinks(url, page.html());
            if (!links.isEmpty()) {
                throw new NotProductException("Это страница каталога (" + links.size()
                        + " ссылок на товары), нужна ссылка на товар", links);
            }
            throw new NotProductException("На странице не найдена цена товара");
        }

        product.setParsedAt(Instant.now());
        // видно, какой поток обработал ссылку
        product.setThreadName(Thread.currentThread().getName());
        return storage.save(product);
    }

    public List<Product> getAll() {
        return storage.findAll();
    }
}
