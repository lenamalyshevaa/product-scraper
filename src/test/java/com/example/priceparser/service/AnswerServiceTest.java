package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.priceparser.model.AnswerFilter;
import com.example.priceparser.model.PageResponse;
import com.example.priceparser.model.Product;
import com.example.priceparser.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnswerServiceTest {

    private AnswerService answerService;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        List<Product> products = new ArrayList<>();
        // у "Дракулы" два обхода: цена выросла с 700 до 763
        products.add(product("https://www.labirint.ru/books/1/", "Дракула", "700", "2026-10-30T10:00:00Z"));
        products.add(product("https://www.labirint.ru/books/1/", "Дракула", "763", "2026-11-01T10:00:00Z"));
        products.add(product("https://www.labirint.ru/books/2/", "Маскарад", "1219", "2026-11-01T10:00:00Z"));
        products.add(product("https://www.labirint.ru/books/3/", "Книга призраков", "3078", "2026-11-01T11:00:00Z"));
        products.add(product("https://book24.ru/product/4/", "Новый год на Зелёной", "549", "2026-11-01T12:00:00Z"));

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        answerService = new AnswerService(repository);
    }

    @Test
    void latestOnlyByDefault() {
        PageResponse page = answerService.find(new AnswerFilter());

        assertEquals(4, page.getTotalElements());
    }

    @Test
    void allHistoryWhenLatestOnlyIsFalse() {
        AnswerFilter filter = new AnswerFilter();
        filter.setLatestOnly(false);

        assertEquals(5, answerService.find(filter).getTotalElements());
    }

    @Test
    void filterByPriceRangeUsesLatestPrice() {
        AnswerFilter filter = new AnswerFilter();
        filter.setMinPrice(new BigDecimal("500"));
        filter.setMaxPrice(new BigDecimal("750"));

        PageResponse page = answerService.find(filter);

        // Дракула раньше стоил 700, но сейчас 763, поэтому в диапазон попадает только book24
        assertEquals(1, page.getTotalElements());
        assertEquals("Новый год на Зелёной", page.getContent().get(0).getTitle());
    }

    @Test
    void searchByTitleAndSite() {
        AnswerFilter filter = new AnswerFilter();
        filter.setQ("КНИГА");
        filter.setSite("labirint");

        PageResponse page = answerService.find(filter);

        assertEquals(1, page.getTotalElements());
        assertEquals("Книга призраков", page.getContent().get(0).getTitle());
    }

    @Test
    void sortByPriceAscending() {
        AnswerFilter filter = new AnswerFilter();
        filter.setSort("price");
        filter.setOrder("asc");

        List<Product> content = answerService.find(filter).getContent();

        assertEquals(0, new BigDecimal("549").compareTo(content.get(0).getPrice()));
        assertEquals(0, new BigDecimal("3078").compareTo(content.get(3).getPrice()));
    }

    @Test
    void pagination() {
        AnswerFilter filter = new AnswerFilter();
        filter.setSort("title");
        filter.setOrder("asc");
        filter.setSize(3);
        filter.setPage(1);

        PageResponse page = answerService.find(filter);

        assertEquals(4, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        // на второй странице остался один товар - последний по алфавиту
        assertEquals(1, page.getContent().size());
        assertEquals("Новый год на Зелёной", page.getContent().get(0).getTitle());
    }

    @Test
    void wrongSortParameter() {
        AnswerFilter filter = new AnswerFilter();
        filter.setSort("color");

        assertThrows(IllegalArgumentException.class, () -> answerService.find(filter));
    }

    private Product product(String url, String title, String price, String parsedAt) {
        Product product = new Product();
        product.setId(nextId++);
        product.setUrl(url);
        product.setTitle(title);
        product.setPrice(new BigDecimal(price));
        product.setCurrency("RUB");
        product.setParsedAt(Instant.parse(parsedAt));
        return product;
    }
}
