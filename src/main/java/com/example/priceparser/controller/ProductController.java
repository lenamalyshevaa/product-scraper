package com.example.priceparser.controller;

import java.util.List;

import com.example.priceparser.model.AnswerFilter;
import com.example.priceparser.model.BatchResult;
import com.example.priceparser.model.PageResponse;
import com.example.priceparser.model.Product;
import com.example.priceparser.service.AnswerService;
import com.example.priceparser.service.BatchParseService;
import com.example.priceparser.service.ParseService;
import com.example.priceparser.service.ResultFileStorage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProductController {

    private final ParseService parseService;
    private final BatchParseService batchParseService;
    private final ResultFileStorage fileStorage;
    private final AnswerService answerService;

    public ProductController(ParseService parseService, BatchParseService batchParseService,
                             ResultFileStorage fileStorage, AnswerService answerService) {
        this.parseService = parseService;
        this.batchParseService = batchParseService;
        this.fileStorage = fileStorage;
        this.answerService = answerService;
    }

    @PostMapping("/api/parse")
    @ResponseStatus(HttpStatus.CREATED)
    public Product parse(@Valid @RequestBody ParseRequest request) {
        return parseService.parse(request.getUrl());
    }

    @PostMapping("/api/parse/batch")
    public BatchResult parseBatch(@Valid @RequestBody BatchRequest request) {
        return batchParseService.parseAll(request.getUrls());
    }

    /**
     * Результаты из H2 с фильтрами, сортировкой и страницами,
     * например /answer?q=дракула&maxPrice=1000&sort=price&order=asc&page=0&size=10
     */
    @GetMapping("/answer")
    public PageResponse answer(AnswerFilter filter) {
        return answerService.find(filter);
    }

    /**
     * История цены по одной ссылке: каждый обход, от старых к новым.
     */
    @GetMapping("/answer/history")
    public List<Product> history(@RequestParam String url) {
        List<Product> history = parseService.getHistory(url);
        if (history.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "По этой ссылке ещё нет данных");
        }
        return history;
    }

    /**
     * Результаты из файла data/results.jsonl, они сохраняются и после перезапуска.
     */
    @GetMapping("/api/file-results")
    public List<Product> fileResults(@RequestParam(defaultValue = "20") int limit) {
        return fileStorage.readLast(limit);
    }
}
