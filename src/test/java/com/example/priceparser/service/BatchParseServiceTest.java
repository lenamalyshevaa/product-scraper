package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.priceparser.model.BatchResult;
import com.example.priceparser.model.Product;
import com.example.priceparser.parser.ProductPageParser;
import com.example.priceparser.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Загрузку страниц подменяем: страница товара с ценой, каталог со ссылками или ошибка сети.
 */
class BatchParseServiceTest {

    private static final String CATALOG = "https://www.labirint.ru/genres/2529/";
    private static final String BROKEN = "https://www.labirint.ru/books/404/";

    private BatchParseService batchParseService;

    @BeforeEach
    void setUp() {
        PageLoader pageLoader = mock(PageLoader.class);
        when(pageLoader.load(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            // имитируем медленный сайт, чтобы потоки реально работали одновременно
            Thread.sleep(50);
            if (url.equals(BROKEN)) {
                throw new IllegalStateException("сайт не ответил");
            }
            if (url.equals(CATALOG)) {
                String html = "<a class=\"cover\" href=\"/books/1/\"></a>"
                        + "<a class=\"cover\" href=\"/books/2/\"></a>"
                        + "<a class=\"cover\" href=\"/books/3/\"></a>";
                return new PageLoader.LoadedPage(html, null);
            }
            String html = "<h1>Книга " + url + "</h1><meta itemprop=\"price\" content=\"500\">";
            return new PageLoader.LoadedPage(html, null);
        });

        ProductPageParser parser = new ProductPageParser();
        ReflectionTestUtils.setField(parser, "catalogLinkSelector", "a.cover[href*=/books/]");

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventLog eventLog = mock(EventLog.class);
        ParseService parseService = new ParseService(pageLoader, parser, repository,
                mock(ResultFileStorage.class), eventLog);
        batchParseService = new BatchParseService(parseService, eventLog);
        ReflectionTestUtils.setField(batchParseService, "threadCount", 4);
        ReflectionTestUtils.setField(batchParseService, "maxProductsFromCatalog", 20);
    }

    @Test
    void parseManyLinksInSeveralThreads() {
        List<String> urls = List.of(
                "https://www.labirint.ru/books/10/", "https://www.labirint.ru/books/11/",
                "https://www.labirint.ru/books/12/", "https://www.labirint.ru/books/13/",
                "https://www.labirint.ru/books/14/", "https://www.labirint.ru/books/15/",
                "https://www.labirint.ru/books/16/", "https://www.labirint.ru/books/17/");

        BatchResult result = batchParseService.parseAll(urls);

        assertEquals(8, result.getSuccess());
        assertEquals(0, result.getFailed());

        Set<String> threadNames = new HashSet<>();
        for (Product product : result.getProducts()) {
            threadNames.add(product.getThreadName());
        }
        assertTrue(threadNames.size() > 1, "ссылки должны были обработать разные потоки");
        // 8 ссылок по 50 мс в одном потоке заняли бы 400 мс
        assertTrue(result.getTimeMs() < 400, "параллельно должно быть быстрее");
    }

    @Test
    void catalogIsExpandedToProducts() {
        BatchResult result = batchParseService.parseAll(List.of(CATALOG, "https://www.labirint.ru/books/1/"));

        // книга 1 есть и в запросе, и в каталоге, но обработана один раз
        assertEquals(3, result.getSuccess());
        assertEquals(0, result.getFailed());
    }

    @Test
    void errorDoesNotStopOtherLinks() {
        BatchResult result = batchParseService.parseAll(List.of(BROKEN, "https://www.labirint.ru/books/20/"));

        assertEquals(1, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(BROKEN, result.getErrors().get(0).getUrl());
    }
}
