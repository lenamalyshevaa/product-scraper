package com.example.priceparser.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.example.priceparser.model.AnswerFilter;
import com.example.priceparser.model.PageResponse;
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

    public PageResponse find(AnswerFilter filter) {
        if (filter.getPage() < 0) {
            throw new IllegalArgumentException("page не может быть меньше 0");
        }
        if (filter.getSize() < 1 || filter.getSize() > 100) {
            throw new IllegalArgumentException("size должен быть от 1 до 100");
        }
        Comparator<Product> comparator = buildComparator(filter.getSort(), filter.getOrder());

        List<Product> all = repository.findAll();

        Collection<Product> source = all;
        if (filter.isLatestOnly()) {
            source = latestByUrl(all);
        }

        // sorted() в параллельном стриме сортирует части в разных потоках и потом сливает их,
        // toList() сохраняет получившийся порядок
        List<Product> sorted = source.parallelStream()
                .filter(product -> matches(product, filter))
                .sorted(comparator)
                .toList();

        int from = Math.min(filter.getPage() * filter.getSize(), sorted.size());
        int to = Math.min(from + filter.getSize(), sorted.size());
        List<Product> pageContent = sorted.subList(from, to);

        return new PageResponse(pageContent, filter.getPage(), filter.getSize(), sorted.size(),
                filter.getSort(), filter.getOrder());
    }

    private Comparator<Product> buildComparator(String sort, String order) {
        Comparator<Product> comparator;
        switch (sort) {
            case "price" -> comparator = Comparator.comparing(Product::getPrice,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "title" -> comparator = Comparator.comparing(Product::getTitle,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "publishedAt" -> comparator = Comparator.comparing(Product::getPublishedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "parsedAt" -> comparator = Comparator.comparing(Product::getParsedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> throw new IllegalArgumentException(
                    "sort может быть только parsedAt, price, title или publishedAt");
        }

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        } else if (!"asc".equalsIgnoreCase(order)) {
            throw new IllegalArgumentException("order может быть только asc или desc");
        }
        // при равных значениях порядок по id, чтобы страницы не перемешивались
        return comparator.thenComparing(Product::getId, Comparator.nullsLast(Comparator.naturalOrder()));
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
