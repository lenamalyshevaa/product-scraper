package com.example.priceparser.service;

import java.util.List;

import com.example.priceparser.model.BatchResult;
import com.example.priceparser.model.ParseError;
import com.example.priceparser.model.Product;
import org.springframework.stereotype.Service;

/**
 * Обработка сразу нескольких ссылок.
 */
@Service
public class BatchParseService {

    private final ParseService parseService;

    public BatchParseService(ParseService parseService) {
        this.parseService = parseService;
    }

    public BatchResult parseAll(List<String> urls) {
        long start = System.currentTimeMillis();
        BatchResult result = new BatchResult();

        for (String url : urls) {
            try {
                Product product = parseService.parse(url);
                result.getProducts().add(product);
            } catch (Exception e) {
                result.getErrors().add(new ParseError(url, e.getMessage()));
            }
        }

        result.setTotal(urls.size());
        result.setSuccess(result.getProducts().size());
        result.setFailed(result.getErrors().size());
        result.setTimeMs(System.currentTimeMillis() - start);
        return result;
    }
}
