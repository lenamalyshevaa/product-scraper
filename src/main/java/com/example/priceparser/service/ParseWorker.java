package com.example.priceparser.service;

import java.util.List;
import java.util.Queue;

import com.example.priceparser.model.ParseError;
import com.example.priceparser.model.Product;

/**
 * Задача для одного потока: берёт ссылки из общей очереди, пока они не кончатся.
 * Несколько таких потоков работают одновременно и делят ссылки между собой.
 */
public class ParseWorker implements Runnable {

    private final Queue<String> urls;
    private final ParseService parseService;
    private final List<Product> products;
    private final List<ParseError> errors;

    public ParseWorker(Queue<String> urls, ParseService parseService,
                       List<Product> products, List<ParseError> errors) {
        this.urls = urls;
        this.parseService = parseService;
        this.products = products;
        this.errors = errors;
    }

    @Override
    public void run() {
        String url;
        // poll() вернёт null, когда очередь опустеет
        while ((url = urls.poll()) != null) {
            try {
                Product product = parseService.parse(url);
                products.add(product);
            } catch (Exception e) {
                errors.add(new ParseError(url, e.getMessage()));
            }
        }
    }
}
