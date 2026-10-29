package com.example.priceparser.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Фоновая задача на обработку списка ссылок.
 * Каждую ссылку обрабатывает отдельная задача в пуле, поэтому счётчики и списки потокобезопасные.
 */
public class ParseTask {

    private final String id;
    private final int total;
    private final Instant createdAt = Instant.now();
    private volatile Instant finishedAt;

    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final List<Product> products = Collections.synchronizedList(new ArrayList<>());
    private final List<ParseError> errors = Collections.synchronizedList(new ArrayList<>());

    // на старте равен числу ссылок, каждая обработанная ссылка уменьшает его на 1
    @JsonIgnore
    private final CountDownLatch latch;

    public ParseTask(String id, int total) {
        this.id = id;
        this.total = total;
        this.latch = new CountDownLatch(total);
    }

    public void addProduct(Product product) {
        products.add(product);
        success.incrementAndGet();
    }

    public void addError(String url, String message) {
        errors.add(new ParseError(url, message));
        failed.incrementAndGet();
    }

    public synchronized void linkDone() {
        latch.countDown();
        if (latch.getCount() == 0) {
            finishedAt = Instant.now();
        }
    }

    /**
     * Ждёт окончания задачи, но не дольше seconds секунд.
     */
    public void await(int seconds) throws InterruptedException {
        latch.await(seconds, TimeUnit.SECONDS);
    }

    public String getStatus() {
        return latch.getCount() == 0 ? "DONE" : "IN_PROGRESS";
    }

    public String getId() {
        return id;
    }

    public int getTotal() {
        return total;
    }

    public int getProcessed() {
        return total - (int) latch.getCount();
    }

    public int getSuccess() {
        return success.get();
    }

    public int getFailed() {
        return failed.get();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public List<Product> getProducts() {
        synchronized (products) {
            return new ArrayList<>(products);
        }
    }

    public List<ParseError> getErrors() {
        synchronized (errors) {
            return new ArrayList<>(errors);
        }
    }
}
