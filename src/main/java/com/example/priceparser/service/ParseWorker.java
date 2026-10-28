package com.example.priceparser.service;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.priceparser.model.ParseError;
import com.example.priceparser.model.Product;

/**
 * Задача для одного потока: берёт ссылки из общей очереди и обрабатывает их.
 * Если ссылка ведёт на каталог, кладёт ссылки на товары обратно в очередь,
 * и их разбирают все потоки.
 */
public class ParseWorker implements Runnable {

    private final Queue<String> urls;
    private final ParseService parseService;
    private final List<Product> products;
    private final List<ParseError> errors;
    // ссылки, которые прислал пользователь: раскрываем только их, чтобы не уйти в обход всего сайта
    private final Set<String> requestUrls;
    // ссылки, которые уже были в очереди, чтобы не парсить одно и то же дважды
    private final Set<String> seen;
    // сколько ссылок ещё не обработано (в очереди + в работе)
    private final AtomicInteger pending;
    private final int maxProductsFromCatalog;

    public ParseWorker(Queue<String> urls, ParseService parseService, List<Product> products,
                       List<ParseError> errors, Set<String> requestUrls, Set<String> seen,
                       AtomicInteger pending, int maxProductsFromCatalog) {
        this.urls = urls;
        this.parseService = parseService;
        this.products = products;
        this.errors = errors;
        this.requestUrls = requestUrls;
        this.seen = seen;
        this.pending = pending;
        this.maxProductsFromCatalog = maxProductsFromCatalog;
    }

    @Override
    public void run() {
        // работаем, пока есть необработанные ссылки, даже если очередь сейчас пустая:
        // другой поток может прямо сейчас разбирать каталог и добавить новые ссылки
        while (pending.get() > 0) {
            String url = urls.poll();
            if (url == null) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }

            try {
                Product product = parseService.parse(url);
                products.add(product);
            } catch (NotProductException e) {
                if (requestUrls.contains(url) && !e.getProductLinks().isEmpty()) {
                    addLinksFromCatalog(e.getProductLinks());
                } else {
                    errors.add(new ParseError(url, e.getMessage()));
                }
            } catch (Exception e) {
                errors.add(new ParseError(url, e.getMessage()));
            } finally {
                pending.decrementAndGet();
            }
        }
    }

    private void addLinksFromCatalog(List<String> links) {
        int added = 0;
        for (String link : links) {
            if (added >= maxProductsFromCatalog) {
                break;
            }
            // add() вернёт false, если такая ссылка уже была
            if (seen.add(link)) {
                // сначала увеличиваем счётчик, потом кладём в очередь,
                // иначе другие потоки могут решить, что работа закончилась
                pending.incrementAndGet();
                urls.add(link);
                added++;
            }
        }
    }
}
