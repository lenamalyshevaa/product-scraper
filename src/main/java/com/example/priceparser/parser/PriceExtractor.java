package com.example.priceparser.parser;

import java.math.BigDecimal;

/**
 * Достаёт цену и валюту из строки, например "618 ₽" или "$1,299.99".
 */
public class PriceExtractor {

    public static BigDecimal extractPrice(String text) {
        if (text == null) {
            return null;
        }

        // оставляем только цифры, точки и запятые
        String number = text.replaceAll("[^0-9.,]", "");
        // точка в конце остаётся от "руб."
        while (number.endsWith(".") || number.endsWith(",")) {
            number = number.substring(0, number.length() - 1);
        }
        if (number.isEmpty()) {
            return null;
        }

        if (number.contains(".") && number.contains(",")) {
            // 1,299.99 - запятая разделяет тысячи
            number = number.replace(",", "");
        } else {
            // 12,50 - запятая вместо точки
            number = number.replace(",", ".");
        }

        try {
            return new BigDecimal(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String extractCurrency(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase();
        if (lower.contains("₽") || lower.contains("руб") || lower.contains("rub")) {
            return "RUB";
        }
        if (lower.contains("$") || lower.contains("usd")) {
            return "USD";
        }
        if (lower.contains("€") || lower.contains("eur")) {
            return "EUR";
        }
        return null;
    }
}
