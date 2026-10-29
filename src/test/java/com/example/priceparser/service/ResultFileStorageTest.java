package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.example.priceparser.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class ResultFileStorageTest {

    @TempDir
    Path tempDir;

    private ResultFileStorage storage;
    private Path file;

    @BeforeEach
    void setUp() {
        file = tempDir.resolve("results.jsonl");
        storage = new ResultFileStorage(new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(storage, "resultsFile", file.toString());
    }

    @Test
    void writeAndReadBack() {
        storage.append(product("Книга 1"));
        storage.append(product("Книга 2"));

        List<Product> result = storage.readLast(10);

        assertEquals(2, result.size());
        // новые сверху
        assertEquals("Книга 2", result.get(0).getTitle());
    }

    @Test
    void manyThreadsWriteAtTheSameTime() throws Exception {
        int threadCount = 8;
        int recordsPerThread = 50;
        // все потоки стартуют одновременно по сигналу, чтобы записи точно пересекались
        CountDownLatch startSignal = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int threadNumber = i;
            Thread thread = new Thread(() -> {
                try {
                    startSignal.await();
                    for (int j = 0; j < recordsPerThread; j++) {
                        storage.append(product("Поток " + threadNumber + " запись " + j));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }
        startSignal.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        // ни одна строка не потерялась и не склеилась с другой
        assertEquals(threadCount * recordsPerThread, Files.readAllLines(file).size());
        assertEquals(threadCount * recordsPerThread, storage.readLast(1000).size());
    }

    private Product product(String title) {
        Product product = new Product();
        product.setTitle(title);
        product.setPrice(new BigDecimal("100"));
        return product;
    }
}
