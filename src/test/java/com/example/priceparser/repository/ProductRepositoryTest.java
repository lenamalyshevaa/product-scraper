package com.example.priceparser.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.priceparser.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * @DataJpaTest поднимает только JPA и H2 в памяти, без веба и парсера.
 */
@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    @Test
    void historyOfOneUrl() {
        String url = "https://www.labirint.ru/books/982366/";
        repository.save(product(url, "618", "2026-10-30T10:00:00Z"));
        repository.save(product(url, "529", "2026-11-01T10:00:00Z"));
        repository.save(product("https://www.labirint.ru/books/1/", "100", "2026-10-31T10:00:00Z"));

        List<Product> history = repository.findByUrlOrderByParsedAtAsc(url);

        assertEquals(2, history.size());
        // compareTo, потому что для equals 618 и 618.00 - разные числа
        assertEquals(0, new BigDecimal("618").compareTo(history.get(0).getPrice()));
        assertEquals(0, new BigDecimal("529").compareTo(history.get(1).getPrice()));
    }

    @Test
    void urlsWithoutDuplicates() {
        repository.save(product("https://www.labirint.ru/books/1/", "100", "2026-10-30T10:00:00Z"));
        repository.save(product("https://www.labirint.ru/books/1/", "110", "2026-10-31T10:00:00Z"));
        repository.save(product("https://www.labirint.ru/books/2/", "200", "2026-10-31T10:00:00Z"));

        assertEquals(2, repository.findAllUrls().size());
    }

    @Test
    void newestFirst() {
        repository.save(product("https://www.labirint.ru/books/1/", "100", "2026-10-30T10:00:00Z"));
        repository.save(product("https://www.labirint.ru/books/2/", "200", "2026-11-01T10:00:00Z"));

        List<Product> all = repository.findAllByOrderByParsedAtDesc();

        assertEquals("https://www.labirint.ru/books/2/", all.get(0).getUrl());
    }

    private Product product(String url, String price, String parsedAt) {
        Product product = new Product();
        product.setUrl(url);
        product.setTitle("Книга");
        product.setPrice(new BigDecimal(price));
        product.setCurrency("RUB");
        product.setParsedAt(Instant.parse(parsedAt));
        return product;
    }
}
