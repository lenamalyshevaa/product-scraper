package com.example.priceparser.service;

import java.net.URI;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Скачивает страницу через WebClient.
 * Если сайт не ответил или вернул ошибку 5xx, пробует ещё раз.
 */
@Service
public class PageLoader {

    private final WebClient webClient;
    private final HostLimiter hostLimiter;
    private final EventLog eventLog;

    @Value("${parser.retry-attempts}")
    private int retryAttempts;

    @Value("${parser.retry-delay-ms}")
    private long retryDelayMs;

    public PageLoader(WebClient webClient, HostLimiter hostLimiter, EventLog eventLog) {
        this.webClient = webClient;
        this.hostLimiter = hostLimiter;
        this.eventLog = eventLog;
    }

    public LoadedPage load(String url) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                return loadOnce(url);
            } catch (WebClientRequestException e) {
                // сайт не ответил: таймаут, обрыв соединения
                lastError = e;
            } catch (WebClientResponseException e) {
                // 404 и другие 4xx повторять бессмысленно, а 5xx - временная ошибка сервера
                if (!e.getStatusCode().is5xxServerError()) {
                    throw e;
                }
                lastError = e;
            }

            if (attempt < retryAttempts) {
                eventLog.log("Попытка " + attempt + " не удалась для " + url + ", пробуем ещё раз");
                try {
                    // с каждой попыткой ждём дольше
                    Thread.sleep(retryDelayMs * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Загрузка прервана");
                }
            }
        }
        throw lastError;
    }

    private LoadedPage loadOnce(String url) {
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
