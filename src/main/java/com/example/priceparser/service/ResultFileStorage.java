package com.example.priceparser.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.example.priceparser.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Сохраняет результаты в файл data/results.jsonl: одна строка - один товар в формате JSON.
 *
 * Писать в файл могут сразу несколько потоков, а читать его - запрос к /api/file-results.
 * ReentrantReadWriteLock разрешает читать нескольким потокам одновременно,
 * но запись идёт строго по одному, и во время записи никто не читает.
 */
@Component
public class ResultFileStorage {

    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Value("${parser.results-file}")
    private String resultsFile;

    public ResultFileStorage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void append(Product product) {
        try {
            String line = objectMapper.writeValueAsString(product) + System.lineSeparator();
            Path path = Path.of(resultsFile);

            lock.writeLock().lock();
            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } finally {
                lock.writeLock().unlock();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать результат в файл: " + e.getMessage());
        }
    }

    /**
     * Последние limit записей из файла, новые сверху.
     */
    public List<Product> readLast(int limit) {
        Path path = Path.of(resultsFile);
        List<String> lines;

        lock.readLock().lock();
        try {
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл с результатами: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }

        List<Product> result = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
            try {
                result.add(objectMapper.readValue(lines.get(i), Product.class));
            } catch (IOException e) {
                // битую строку пропускаем
            }
        }
        return result;
    }
}
