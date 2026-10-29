package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HostLimiterTest {

    private HostLimiter hostLimiter;

    @BeforeEach
    void setUp() {
        hostLimiter = new HostLimiter();
        ReflectionTestUtils.setField(hostLimiter, "maxRequestsPerSite", 2);
    }

    @Test
    void notMoreThanTwoRequestsToOneSite() throws Exception {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Thread thread = new Thread(() -> {
                String url = "https://www.labirint.ru/books/1/";
                try {
                    hostLimiter.acquire(url);
                    try {
                        int now = current.incrementAndGet();
                        max.accumulateAndGet(now, Math::max);
                        Thread.sleep(50);
                        current.decrementAndGet();
                    } finally {
                        hostLimiter.release(url);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(2, max.get());
    }

    @Test
    void differentSitesDoNotWaitForEachOther() throws Exception {
        hostLimiter.acquire("https://www.labirint.ru/books/1/");
        hostLimiter.acquire("https://www.labirint.ru/books/2/");

        // лимит Лабиринта занят, но book24 это не касается
        long start = System.currentTimeMillis();
        hostLimiter.acquire("https://book24.ru/product/1/");
        assertTrue(System.currentTimeMillis() - start < 100);
    }
}
