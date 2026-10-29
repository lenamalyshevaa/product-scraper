package com.example.priceparser.service;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Скачивает страницу через WebClient.
 */
@Service
public class PageLoader {

    private final WebClient webClient;
    private final HostLimiter hostLimiter;

    public PageLoader(WebClient webClient, HostLimiter hostLimiter) {
        this.webClient = webClient;
        this.hostLimiter = hostLimiter;
    }

    public LoadedPage load(String url) {
        try {
            hostLimiter.acquire(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Загрузка прервана");
        }

        ResponseEntity<String> response;
        try {
            // block() ждёт ответ, нам здесь не нужна реактивность
            response = webClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .toEntity(String.class)
                    .block();
        } finally {
            // отпускаем в finally, иначе при ошибке место для этого сайта не освободится
            hostLimiter.release(url);
        }

        if (response == null || response.getBody() == null) {
            throw new IllegalStateException("Пустой ответ от " + url);
        }

        // Last-Modified пригодится как дата публикации, если на странице её нет
        Instant lastModified = null;
        long header = response.getHeaders().getLastModified();
        if (header > 0) {
            lastModified = Instant.ofEpochMilli(header);
        }
        return new LoadedPage(response.getBody(), lastModified);
    }

    public record LoadedPage(String html, Instant lastModified) {
    }
}
