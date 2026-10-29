package com.example.priceparser.controller;

import java.util.List;

import com.example.priceparser.model.BatchResult;
import com.example.priceparser.model.Product;
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

@RestController
public class ProductController {

    private final ParseService parseService;
    private final BatchParseService batchParseService;
    private final ResultFileStorage fileStorage;

    public ProductController(ParseService parseService, BatchParseService batchParseService,
                             ResultFileStorage fileStorage) {
        this.parseService = parseService;
        this.batchParseService = batchParseService;
        this.fileStorage = fileStorage;
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

    @GetMapping("/answer")
    public List<Product> answer() {
        return parseService.getAll();
    }

    /**
     * Результаты из файла data/results.jsonl, они сохраняются и после перезапуска.
     */
    @GetMapping("/api/file-results")
    public List<Product> fileResults(@RequestParam(defaultValue = "20") int limit) {
        return fileStorage.readLast(limit);
    }
}
