package com.example.priceparser.parser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.priceparser.model.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Разбирает HTML страницы товара.
 * Лабиринт и book24 размечают цену по стандарту schema.org (itemprop="price"),
 * поэтому в первую очередь смотрим туда.
 */
@Component
public class ProductPageParser {

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    @Value("${parser.catalog-link-selector}")
    private String catalogLinkSelector;

    /**
     * Возвращает товар или null, если на странице нет цены.
     */
    public Product parse(String url, String html, Instant lastModified) {
        Document doc = Jsoup.parse(html, url);

        BigDecimal price = findPrice(doc);
        if (price == null) {
            return null;
        }

        Product product = new Product();
        product.setUrl(url);
        product.setTitle(findTitle(doc));
        product.setDescription(shorten(findDescription(doc)));
        product.setPrice(price);
        product.setCurrency(findCurrency(doc));
        product.setAvailability(findAvailability(doc));

        Instant publishedAt = findPublishedAt(doc);
        if (publishedAt == null) {
            publishedAt = lastModified;
        }
        product.setPublishedAt(publishedAt);
        return product;
    }

    /**
     * Ссылки на товары со страницы каталога.
     */
    public List<String> findProductLinks(String url, String html) {
        Document doc = Jsoup.parse(html, url);
        List<String> links = new ArrayList<>();
        for (Element link : doc.select(catalogLinkSelector)) {
            String href = link.absUrl("href");
            if (!links.contains(href)) {
                links.add(href);
            }
        }
        return links;
    }

    private BigDecimal findPrice(Document doc) {
        Element itemprop = doc.selectFirst("[itemprop=price]");
        if (itemprop != null) {
            String value = itemprop.hasAttr("content") ? itemprop.attr("content") : itemprop.text();
            return PriceExtractor.extractPrice(value);
        }

        Element meta = doc.selectFirst("meta[property=product:price:amount]");
        if (meta != null) {
            return PriceExtractor.extractPrice(meta.attr("content"));
        }

        // на странице каталога у каждой книги своя цена, это не товар
        if (!doc.select(catalogLinkSelector).isEmpty()) {
            return null;
        }

        Element priceElement = doc.selectFirst("[class*=price]");
        if (priceElement != null) {
            return PriceExtractor.extractPrice(priceElement.text());
        }
        return null;
    }

    private String findCurrency(Document doc) {
        Element itemprop = doc.selectFirst("[itemprop=priceCurrency]");
        if (itemprop != null && !itemprop.attr("content").isEmpty()) {
            return itemprop.attr("content");
        }

        Element meta = doc.selectFirst("meta[property=product:price:currency]");
        if (meta != null) {
            return meta.attr("content");
        }

        Element priceElement = doc.selectFirst("[class*=price]");
        if (priceElement != null) {
            return PriceExtractor.extractCurrency(priceElement.text());
        }
        return null;
    }

    private String findTitle(Document doc) {
        // в og:title магазины дописывают "Купить книгу | Лабиринт", поэтому сначала h1
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }

        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            return ogTitle.attr("content");
        }
        return doc.title();
    }

    private String findDescription(Document doc) {
        // Лабиринт: блок "Аннотация"
        Element annotation = doc.selectFirst("#annotation");
        if (annotation != null) {
            Element copy = annotation.clone();
            copy.select("h2, button").remove();
            return copy.text();
        }

        // book24: аннотация в itemprop="description"
        Element itemprop = doc.selectFirst("[itemprop=description]");
        if (itemprop != null) {
            return itemprop.hasAttr("content") ? itemprop.attr("content") : itemprop.text();
        }

        Element ogDescription = doc.selectFirst("meta[property=og:description]");
        if (ogDescription != null) {
            return ogDescription.attr("content");
        }

        Element metaDescription = doc.selectFirst("meta[name=description]");
        if (metaDescription != null) {
            return metaDescription.attr("content");
        }
        return null;
    }

    private String findAvailability(Document doc) {
        Element element = doc.selectFirst("[itemprop=availability]");
        if (element == null) {
            return null;
        }
        String value = element.hasAttr("content") ? element.attr("content") : element.attr("href");
        // https://schema.org/InStock -> InStock
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private Instant findPublishedAt(Document doc) {
        Element datePublished = doc.selectFirst("[itemprop=datePublished]");
        if (datePublished != null) {
            Instant date = parseDate(datePublished.attr("content"));
            if (date != null) {
                return date;
            }
        }

        Element articleTime = doc.selectFirst("meta[property=article:published_time]");
        if (articleTime != null) {
            Instant date = parseDate(articleTime.attr("content"));
            if (date != null) {
                return date;
            }
        }

        // у Лабиринта точной даты нет, есть строка "Издательство: Текст, 2023"
        for (Element label : doc.getElementsMatchingOwnText("^(Год издания|Издательство):?$")) {
            Element row = label.parent();
            if (row == null) {
                continue;
            }
            Matcher matcher = YEAR.matcher(row.text());
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group());
                return LocalDate.of(year, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
            }
        }
        return null;
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // 2026-09-14T00:00:00+03:00
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception e) {
            // не получилось, пробуем просто дату
        }
        try {
            // 2026-09-14
            return LocalDate.parse(value.substring(0, 10)).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private String shorten(String text) {
        if (text == null) {
            return null;
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= MAX_DESCRIPTION_LENGTH) {
            return text;
        }
        String cut = text.substring(0, MAX_DESCRIPTION_LENGTH - 3);
        // обрезаем по последнему пробелу, чтобы не рвать слово
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace);
        }
        return cut + "...";
    }
}
