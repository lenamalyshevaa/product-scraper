package com.example.priceparser.service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.example.priceparser.model.AnswerFilter;
import com.example.priceparser.model.Product;
import com.example.priceparser.repository.ProductRepository;
import org.springframework.stereotype.Service;

/**
 * Выборка результатов для GET /answer.
 * Данные берутся из H2, а фильтруются параллельным стримом (parallelStream):
 * записи делятся на части и проверяются в нескольких потоках ForkJoinPool.
 */
@Service
public class AnswerService {

    private final ProductRepository repository;

    public AnswerService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> find(AnswerFilter filter) {
        List<Product> all = repository.findAll();

        Collection<Product> source = all;
        if (filter.isLatestOnly()) {
            source = latestByUrl(all);
        }

        return source.parallelStream()
                .filter(product -> matches(product, filter))
                .collect(Collectors.toList());
    }

    /**
     * Для каждой ссылки оставляет только последний обход.
     * toConcurrentMap подходит для параллельного стрима: потоки пишут в одну ConcurrentHashMap.
     */
    private Collection<Product> latestByUrl(List<Product> all) {
        ConcurrentMap<String, Product> latest = all.parallelStream()
                .collect(Collectors.toConcurrentMap(
                        Product::getUrl,
                        product -> product,
                        (a, b) -> a.getParsedAt().isAfter(b.getParsedAt()) ? a : b));
        return latest.values();
    }

    private boolean matches(Product product, AnswerFilter filter) {
        if (filter.getQ() != null && !filter.getQ().isBlank()) {
            String q = filter.getQ().toLowerCase();
            boolean inTitle = product.getTitle() != null && product.getTitle().toLowerCase().contains(q);
            boolean inDescription = product.getDescription() != null
                    && product.getDescription().toLowerCase().contains(q);
            if (!inTitle && !inDescription) {
                return false;
            }
        }
        if (filter.getMinPrice() != null) {
            if (product.getPrice() == null || product.getPrice().compareTo(filter.getMinPrice()) < 0) {
                return false;
            }
        }
        if (filter.getMaxPrice() != null) {
            if (product.getPrice() == null || product.getPrice().compareTo(filter.getMaxPrice()) > 0) {
                return false;
            }
        }
        if (filter.getCurrency() != null && !filter.getCurrency().isBlank()) {
            if (!filter.getCurrency().equalsIgnoreCase(product.getCurrency())) {
                return false;
            }
        }
        if (filter.getSite() != null && !filter.getSite().isBlank()) {
            if (!product.getUrl().toLowerCase().contains(filter.getSite().toLowerCase())) {
                return false;
            }
        }
        return true;
    }
}
