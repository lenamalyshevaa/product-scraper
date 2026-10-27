package com.example.priceparser.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.priceparser.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HTML в тестах - упрощённые куски настоящих страниц Лабиринта и book24.
 */
class ProductPageParserTest {

    private ProductPageParser parser;

    @BeforeEach
    void setUp() {
        parser = new ProductPageParser();
        ReflectionTestUtils.setField(parser, "catalogLinkSelector", "a.cover[href*=/books/]");
    }

    @Test
    void parseLabirintPage() {
        String html = """
                <html><head>
                <meta property="og:title" content="Книга: Волосы Вероники - Фрэнсис Фицджеральд. Купить книгу | Лабиринт">
                </head><body>
                <h1 itemprop="name">Волосы Вероники: Фрэнсис Фицджеральд</h1>
                <div itemprop="offers">
                  <meta itemprop="availability" content="https://schema.org/InStock">
                  <meta itemprop="priceCurrency" content="RUB">
                  <meta itemprop="price" content="618">
                </div>
                <div id="annotation">
                  <div><h2>Аннотация</h2><button>Содержание</button></div>
                  <div>Один из крупнейших американских прозаиков прошлого века.</div>
                </div>
                <div><div>Издательство</div><div><a href="/pubhouse/130/">Текст</a>, <span>2023</span></div></div>
                </body></html>
                """;

        Product product = parser.parse("https://www.labirint.ru/books/982366/", html, null);

        assertEquals("Волосы Вероники: Фрэнсис Фицджеральд", product.getTitle());
        assertEquals("Один из крупнейших американских прозаиков прошлого века.", product.getDescription());
        assertEquals(new BigDecimal("618"), product.getPrice());
        assertEquals("RUB", product.getCurrency());
        assertEquals("InStock", product.getAvailability());
        assertEquals(Instant.parse("2023-01-01T00:00:00Z"), product.getPublishedAt());
    }

    @Test
    void parseBook24Page() {
        String html = """
                <html><body>
                <h1 itemprop="name"> Бракко Джулия: Новый год на Зелёной, 25 </h1>
                <meta itemprop="description" content="Тёплая сказка про новогоднее чудо">
                <meta itemprop="datePublished" content="2026-09-14T00:00:00+03:00">
                <div itemprop="offers">
                  <link itemprop="availability" href="https://schema.org/InStock">
                  <meta itemprop="price" content="549">
                  <meta itemprop="priceCurrency" content="RUB">
                </div>
                </body></html>
                """;

        Product product = parser.parse("https://book24.ru/product/novyj-god-na-zelenoj-25-8844240/", html, null);

        assertEquals("Бракко Джулия: Новый год на Зелёной, 25", product.getTitle());
        assertEquals("Тёплая сказка про новогоднее чудо", product.getDescription());
        assertEquals(new BigDecimal("549"), product.getPrice());
        assertEquals("InStock", product.getAvailability());
        assertEquals(Instant.parse("2026-09-13T21:00:00Z"), product.getPublishedAt());
    }

    @Test
    void useLastModifiedWhenNoDateOnPage() {
        String html = "<html><body><h1>Товар</h1><span class=\"price\">1 299 ₽</span></body></html>";
        Instant lastModified = Instant.parse("2026-10-20T12:00:00Z");

        Product product = parser.parse("https://shop.example/item", html, lastModified);

        assertEquals(new BigDecimal("1299"), product.getPrice());
        assertEquals("RUB", product.getCurrency());
        assertEquals(lastModified, product.getPublishedAt());
    }

    @Test
    void pageWithoutPriceReturnsNull() {
        String html = "<html><body><h1>О компании</h1></body></html>";

        assertNull(parser.parse("https://shop.example/about", html, null));
    }

    @Test
    void catalogPageIsNotProduct() {
        String html = """
                <html><body>
                <a class="cover" href="/books/982366/">Волосы Вероники</a><span class="price-val">618 ₽</span>
                <a class="cover" href="/books/765910/">Призрак</a><span class="price-val">540 ₽</span>
                <a class="cover" href="/books/982366/">дубль</a>
                </body></html>
                """;
        String url = "https://www.labirint.ru/genres/2529/";

        assertNull(parser.parse(url, html, null));
        List<String> links = parser.findProductLinks(url, html);
        assertEquals(List.of("https://www.labirint.ru/books/982366/", "https://www.labirint.ru/books/765910/"), links);
    }

    @Test
    void longDescriptionIsShortened() {
        String html = "<html><body><h1>Книга</h1><meta itemprop=\"price\" content=\"100\">"
                + "<meta itemprop=\"description\" content=\"" + "слово ".repeat(200) + "\"></body></html>";

        Product product = parser.parse("https://shop.example/book", html, null);

        assertTrue(product.getDescription().length() <= 500);
        assertTrue(product.getDescription().endsWith("слово..."));
    }
}
