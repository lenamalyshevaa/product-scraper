package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Вместо настоящего сайта - маленький HTTP-сервер из JDK на случайном порту.
 */
class PageLoaderTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();
    private PageLoader pageLoader;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        // первый запрос падает с 503, второй отвечает нормально
        server.createContext("/flaky", exchange -> {
            int number = requests.incrementAndGet();
            if (number == 1) {
                send(exchange, 503, "временно недоступно");
            } else {
                send(exchange, 200, "<h1>Книга</h1>");
            }
        });
        server.createContext("/missing", exchange -> {
            requests.incrementAndGet();
            send(exchange, 404, "нет такой страницы");
        });
        server.createContext("/broken", exchange -> {
            requests.incrementAndGet();
            send(exchange, 500, "ошибка сервера");
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        HostLimiter hostLimiter = new HostLimiter();
        ReflectionTestUtils.setField(hostLimiter, "maxRequestsPerSite", 2);

        pageLoader = new PageLoader(WebClient.builder().build(), hostLimiter, mock(EventLog.class));
        ReflectionTestUtils.setField(pageLoader, "retryAttempts", 3);
        ReflectionTestUtils.setField(pageLoader, "retryDelayMs", 10L);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void retryAfterServerError() {
        PageLoader.LoadedPage page = pageLoader.load(baseUrl + "/flaky");

        assertEquals("<h1>Книга</h1>", page.html());
        assertEquals(2, requests.get());
    }

    @Test
    void noRetryForNotFound() {
        assertThrows(WebClientResponseException.NotFound.class, () -> pageLoader.load(baseUrl + "/missing"));
        assertEquals(1, requests.get());
    }

    @Test
    void giveUpAfterAllAttempts() {
        assertThrows(WebClientResponseException.class, () -> pageLoader.load(baseUrl + "/broken"));
        assertEquals(3, requests.get());
    }

    private void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
