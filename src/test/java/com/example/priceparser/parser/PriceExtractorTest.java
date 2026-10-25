package com.example.priceparser.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PriceExtractorTest {

    @Test
    void priceWithRubleSign() {
        assertEquals(new BigDecimal("1299"), PriceExtractor.extractPrice("1 299 ₽"));
        assertEquals("RUB", PriceExtractor.extractCurrency("1 299 ₽"));
    }

    @Test
    void priceWithKopecks() {
        assertEquals(new BigDecimal("2490.00"), PriceExtractor.extractPrice("Цена: 2 490,00 руб."));
        assertEquals("RUB", PriceExtractor.extractCurrency("Цена: 2 490,00 руб."));
    }

    @Test
    void priceInDollars() {
        assertEquals(new BigDecimal("1299.99"), PriceExtractor.extractPrice("$1,299.99"));
        assertEquals("USD", PriceExtractor.extractCurrency("$1,299.99"));
    }

    @Test
    void textWithoutPrice() {
        assertNull(PriceExtractor.extractPrice("нет в наличии"));
        assertNull(PriceExtractor.extractPrice(null));
    }
}
