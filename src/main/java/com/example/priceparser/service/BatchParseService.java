package com.example.priceparser.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.priceparser.model.BatchResult;
import com.example.priceparser.model.ParseError;
import com.example.priceparser.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Обработка сразу нескольких ссылок в нескольких потоках.
 * Страницы грузятся по сети, поэтому пока один поток ждёт ответа сайта, другие тоже качают.
 */
@Service
public class BatchParseService {

    private final ParseService parseService;

    @Value("${parser.batch-threads}")
    private int threadCount;

    public BatchParseService(ParseService parseService) {
        this.parseService = parseService;
    }

    public BatchResult parseAll(List<String> urls) {
        long start = System.currentTimeMillis();

        // общая очередь ссылок и общие списки результатов - их используют все потоки сразу
        Queue<String> queue = new ConcurrentLinkedQueue<>(urls);
        List<Product> products = Collections.synchronizedList(new ArrayList<>());
        List<ParseError> errors = Collections.synchronizedList(new ArrayList<>());

        int count = Math.min(threadCount, urls.size());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Thread thread = new Thread(new ParseWorker(queue, parseService, products, errors),
                    "batch-worker-" + (i + 1));
            threads.add(thread);
            thread.start();
        }

        // ждём, пока все потоки закончат
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Обработка прервана");
            }
        }

        BatchResult result = new BatchResult();
        result.setTotal(urls.size());
        result.setProducts(new ArrayList<>(products));
        result.setErrors(new ArrayList<>(errors));
        result.setSuccess(products.size());
        result.setFailed(errors.size());
        result.setTimeMs(System.currentTimeMillis() - start);
        return result;
    }
}
