package com.example.priceparser.service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ограничивает число одновременных запросов к одному сайту.
 * Даже если работают 10 потоков, к labirint.ru одновременно уйдут не больше N запросов,
 * остальные потоки подождут. Так сайт не примет нас за DDoS и не забанит.
 */
@Component
public class HostLimiter {

    // для каждого сайта свой семафор
    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    @Value("${parser.max-requests-per-site}")
    private int maxRequestsPerSite;

    public void acquire(String url) throws InterruptedException {
        getSemaphore(url).acquire();
    }

    public void release(String url) {
        getSemaphore(url).release();
    }

    private Semaphore getSemaphore(String url) {
        String host = URI.create(url).getHost();
        // computeIfAbsent создаст семафор один раз, даже если два потока придут одновременно
        return semaphores.computeIfAbsent(host, h -> new Semaphore(maxRequestsPerSite));
    }
}
