package com.example.priceparser.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final EventLog eventLog;

    @Value("${parser.batch-threads}")
    private int threadCount;

    @Value("${parser.catalog-max-products}")
    private int maxProductsFromCatalog;

    public BatchParseService(ParseService parseService, EventLog eventLog) {
        this.parseService = parseService;
        this.eventLog = eventLog;
    }

    public BatchResult parseAll(List<String> urls) {
        long start = System.currentTimeMillis();
        eventLog.log("Пакет из " + urls.size() + " ссылок, потоков: " + threadCount);

        // всё, что ниже, используют все потоки сразу, поэтому только потокобезопасные коллекции
        Set<String> seen = ConcurrentHashMap.newKeySet();
        seen.addAll(urls);
        Queue<String> queue = new ConcurrentLinkedQueue<>(seen);
        AtomicInteger pending = new AtomicInteger(queue.size());
        List<Product> products = Collections.synchronizedList(new ArrayList<>());
        List<ParseError> errors = Collections.synchronizedList(new ArrayList<>());
        Set<String> requestUrls = new HashSet<>(urls);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            ParseWorker worker = new ParseWorker(queue, parseService, products, errors,
                    requestUrls, seen, pending, maxProductsFromCatalog);
            Thread thread = new Thread(worker, "batch-worker-" + (i + 1));
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
        result.setTotal(products.size() + errors.size());
        result.setProducts(new ArrayList<>(products));
        result.setErrors(new ArrayList<>(errors));
        result.setSuccess(products.size());
        result.setFailed(errors.size());
        result.setTimeMs(System.currentTimeMillis() - start);
        eventLog.log("Пакет обработан за " + result.getTimeMs() + " мс: успешно " + result.getSuccess()
                + ", ошибок " + result.getFailed());
        return result;
    }
}
